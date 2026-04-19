package com.stockmanagement.domain.payment.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.infrastructure.PaymentIdempotencyManager;
import com.stockmanagement.domain.payment.infrastructure.TossPaymentsClient;
import com.stockmanagement.domain.payment.infrastructure.dto.TossCancelRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 결제 도메인 핵심 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 로 개별 오버라이드
 * </ul>
 *
 * <p>결제 흐름:
 * <pre>
 *   1. prepare()  — 주문 검증, PENDING Payment 생성, tossOrderId + 금액 반환
 *   2. confirm()  — 금액 검증, Toss confirm API 호출, PENDING → DONE 전환,
 *                   OrderService.confirm() → 재고 reserved → allocated
 *   3. cancel()   — Toss cancel API 호출, DONE → CANCELLED 전환,
 *                   OrderService.refund() → 재고 allocated 해제
 * </pre>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentIdempotencyManager idempotencyManager;
    private final PaymentTransactionHelper transactionHelper;

    /**
     * 결제 세션을 준비한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>주문 존재 여부 및 PENDING 상태 검증
     *   <li>클라이언트 제출 금액을 서버 저장 금액과 비교 (클라이언트 조작 방지)
     *   <li>이미 PENDING 결제가 존재하면 기존 결제 반환 (멱등성)
     *   <li>고유 {@code tossOrderId} 생성 후 PENDING Payment 레코드 저장
     * </ol>
     *
     * @param request 내부 orderId와 예상 금액
     * @return TossPayments 결제 위젯에 전달할 tossOrderId와 검증된 금액
     */
    @Transactional
    public PaymentPrepareResponse prepare(PaymentPrepareRequest request, Long userId) {
        Order order = orderRepository.findByIdWithItems(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 요청자가 주문 소유자인지 검증 (JWT claim에서 추출한 userId 사용 — DB 조회 불필요)
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        // PENDING 주문만 결제 세션을 시작할 수 있다
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        // 클라이언트 제출 금액을 실제 결제 금액(쿠폰·포인트 차감 후)과 비교 — 클라이언트 조작 방지
        if (order.getPayableAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 이번 결제 시도용 고유 tossOrderId 생성
        String tossOrderId = buildTossOrderId(order.getId());

        // 기존 결제 레코드 처리: PENDING → 멱등성 반환, FAILED → 재시도 허용, 기타 → 예외
        Optional<Payment> existing = paymentRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (p.getStatus() == PaymentStatus.PENDING) {
                return buildPrepareResponse(p, order);
            }
            if (p.getStatus() == PaymentStatus.FAILED) {
                // FAILED 결제를 새 tossOrderId로 초기화하여 재사용
                p.resetForRetry(tossOrderId);
                return buildPrepareResponse(p, order);
            }
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .tossOrderId(tossOrderId)
                .amount(order.getPayableAmount())
                .build();

        Payment saved = paymentRepository.save(payment);
        return buildPrepareResponse(saved, order);
    }

    /**
     * TossPayments에 결제를 확정하고 내부 상태를 갱신한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>Redis 완료 캐시 확인 → 있으면 즉시 반환 (idempotency)
     *   <li>Redis SETNX로 PROCESSING 상태 선점 → 실패 시 처리 중 예외
     *   <li>[Short TX] DB 검증 — {@link PaymentTransactionHelper#loadAndValidateForConfirm}
     *   <li>Toss API 호출 (DB 커넥션 미점유)
     *   <li>[Short TX] 확정 결과 반영 — {@link PaymentTransactionHelper#applyConfirmResult}
     *   <li>결과를 Redis에 캐싱 (24h TTL)
     * </ol>
     *
     * <p>{@code NOT_SUPPORTED} propagation으로 클래스 레벨 readOnly TX를 억제한다.
     * 내부 DB 연산은 각각 헬퍼의 독립 트랜잭션으로 처리된다.
     *
     * @param request 결제 위젯에서 전달된 paymentKey, tossOrderId, amount
     * @return 업데이트된 결제 상세 정보
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse confirm(PaymentConfirmRequest request, Long userId) {
        String idempotencyKey = "confirm:" + request.getTossOrderId();

        // 1. Redis 완료 캐시 확인
        Optional<PaymentResponse> cached = idempotencyManager.getIfCompleted(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. PROCESSING으로 원자적 선점 (SETNX)
        if (!idempotencyManager.tryAcquire(idempotencyKey)) {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS);
        }

        try {
            // 3. Short TX: 소유권·상태·금액 검증 (DB 커넥션 즉시 반환)
            Optional<PaymentResponse> existing = transactionHelper.loadAndValidateForConfirm(
                    request.getTossOrderId(), request.getAmount(), userId);
            if (existing.isPresent()) {
                idempotencyManager.complete(idempotencyKey, existing.get());
                return existing.get();
            }

            // 4. Toss API 호출 (DB 커넥션 미점유 — 커넥션 풀 고갈 방지)
            TossConfirmResponse tossResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.getPaymentKey(), request.getTossOrderId(), request.getAmount())
            );

            // 5. Short TX: 확정 결과 반영
            PaymentResponse response = transactionHelper.applyConfirmResult(
                    request.getTossOrderId(), tossResponse);
            idempotencyManager.complete(idempotencyKey, response);
            return response;

        } catch (Exception e) {
            // PAYMENT_IN_PROGRESS → PENDING 복원: 재시도 시 스케줄러가 다시 접근 가능하도록 되돌린다
            transactionHelper.resetOrderOnPaymentError(request.getTossOrderId());
            // 실패 시 Redis 키 삭제 → 클라이언트 재시도 허용
            idempotencyManager.release(idempotencyKey);
            throw e;
        }
    }

    /**
     * 결제를 취소하고 전액 또는 부분 환불을 처리한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>Redis 완료 캐시 확인 → 있으면 즉시 반환 (idempotency)
     *   <li>Redis SETNX로 PROCESSING 상태 선점 → 실패 시 처리 중 예외
     *   <li>[Short TX] DB 검증 — {@link PaymentTransactionHelper#loadAndValidateForCancel}
     *   <li>Toss API 호출 (DB 커넥션 미점유)
     *   <li>[Short TX] 취소 결과 반영 — {@link PaymentTransactionHelper#applyCancelResult}
     *   <li>결과를 Redis에 캐싱 (24h TTL)
     * </ol>
     *
     * @param paymentKey Toss 결제 키
     * @param request    취소 사유 및 부분 취소 금액 (선택)
     * @return 업데이트된 결제 상세 정보
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse cancel(String paymentKey, PaymentCancelRequest request, Long userId, boolean isAdmin) {
        String idempotencyKey = "cancel:" + paymentKey;

        // 1. Redis 완료 캐시 확인
        Optional<PaymentResponse> cached = idempotencyManager.getIfCompleted(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. PROCESSING으로 원자적 선점 (SETNX)
        if (!idempotencyManager.tryAcquire(idempotencyKey)) {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS);
        }

        try {
            // 3. Short TX: 소유권·상태 검증 (DB 커넥션 즉시 반환)
            Optional<PaymentResponse> existing = transactionHelper.loadAndValidateForCancel(paymentKey, userId, isAdmin);
            if (existing.isPresent()) {
                idempotencyManager.complete(idempotencyKey, existing.get());
                return existing.get();
            }

            // 4. Toss API 호출 (DB 커넥션 미점유)
            tossPaymentsClient.cancel(paymentKey,
                    new TossCancelRequest(request.getCancelReason(), request.getCancelAmount()));

            // 5. Short TX: 취소 결과 반영
            PaymentResponse response = transactionHelper.applyCancelResult(
                    paymentKey, request.getCancelReason());
            idempotencyManager.complete(idempotencyKey, response);
            return response;

        } catch (Exception e) {
            // 실패 시 Redis 키 삭제 → 재시도 허용
            idempotencyManager.release(idempotencyKey);
            throw e;
        }
    }

    /**
     * TossPayments Webhook 이벤트를 처리한다.
     *
     * <p>TossPayments는 10초 이내 HTTP 2xx를 기대한다.
     * 가상계좌 입금 완료(WAITING_FOR_DEPOSIT → DONE) 이벤트 수신 시 결제를 확정한다.
     *
     * @param event 파싱된 Webhook 페이로드
     */
    @Transactional
    public void handleWebhook(TossWebhookEvent event) {
        if (!"PAYMENT_STATUS_CHANGED".equals(event.getEventType())) {
            log.debug("지원하지 않는 Webhook 이벤트 타입 무시: {}", event.getEventType());
            return;
        }

        TossWebhookEvent.Data data = event.getData();
        log.info("Webhook 수신: eventType={}, status={}, tossOrderId={}",
                event.getEventType(), data.getStatus(), data.getOrderId());

        paymentRepository.findByTossOrderId(data.getOrderId()).ifPresent(payment -> {
            switch (data.getStatus()) {
                case "DONE" -> {
                    // 가상계좌: confirm 시 WAITING_FOR_DEPOSIT으로 PENDING 유지 후
                    // 실제 입금 완료 시 Toss가 DONE Webhook을 전송한다.
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        log.info("[Webhook] 가상계좌 입금 완료 — 결제 확정 진행: tossOrderId={}",
                                data.getOrderId());
                        transactionHelper.applyWebhookConfirmResult(
                                data.getOrderId(), data.getPaymentKey());
                    }
                }
                case "CANCELED" ->
                    log.info("[Webhook] CANCELED: tossOrderId={}", data.getOrderId());
                default ->
                    log.debug("[Webhook] 미처리 status={}: tossOrderId={}", data.getStatus(), data.getOrderId());
            }
        });
    }

    /**
     * Toss paymentKey로 결제 상세 정보를 조회한다.
     *
     * <p>ADMIN은 전체 조회 가능. USER는 자신의 주문에 대한 결제만 조회 가능.
     * Payment 엔티티에 userId 필드가 없으므로 orderId → Order.userId 경유로 소유권을 검증한다.
     *
     * @param paymentKey Toss 결제 키
     * @param userId     요청자 userId (JWT claim)
     * @param isAdmin    ADMIN 여부
     * @return 결제 상세 정보
     * @throws BusinessException 결제 없음(PAYMENT_NOT_FOUND) 또는 소유권 불일치(ORDER_ACCESS_DENIED)
     */
    public PaymentResponse getByPaymentKey(String paymentKey, Long userId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!isAdmin) {
            Long orderUserId = orderRepository.findUserIdById(payment.getOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
            if (!orderUserId.equals(userId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }
        return PaymentResponse.from(payment);
    }

    /**
     * 주문 ID로 결제 정보를 조회한다. 결제 레코드가 없으면 빈 Optional을 반환한다.
     * ADMIN은 전체 조회 가능, USER는 본인 주문만 조회 가능.
     *
     * @param orderId  조회할 주문 ID
     * @param username 요청자 username
     * @param isAdmin  ADMIN 여부
     * @return 결제 정보 (없으면 Optional.empty())
     */
    /** 현재 인증 사용자의 결제 목록을 최신순으로 페이징 조회한다. */
    public Page<PaymentResponse> getMyPayments(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(PaymentResponse::from);
    }

    public Optional<PaymentResponse> getByOrderId(Long orderId, Long userId, boolean isAdmin) {
        if (!isAdmin) {
            // JWT claim에서 추출한 userId로 소유권 검증 — DB 조회 불필요
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }
        return paymentRepository.findByOrderId(orderId).map(PaymentResponse::from);
    }

    // ===== Private helpers =====

    /**
     * 결제 시도용 고유 tossOrderId를 생성한다.
     * 형식: {@code order-{orderId}-{UUID 8자}}
     * TossPayments orderId 제약 충족: 6~64자, 영숫자 + 하이픈/언더스코어.
     */
    private String buildTossOrderId(Long orderId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "order-" + orderId + "-" + suffix;
    }

    /**
     * 결제 위젯용 주문명을 생성한다.
     * 예시: "MacBook Pro 외 2건"
     */
    private PaymentPrepareResponse buildPrepareResponse(Payment payment, Order order) {
        User user = userRepository.findById(order.getUserId()).orElse(null);
        return new PaymentPrepareResponse(
                payment.getTossOrderId(),
                payment.getAmount(),
                buildOrderName(order),
                user != null ? user.getUsername() : null,
                user != null ? user.getEmail() : null
        );
    }

    private String buildOrderName(Order order) {
        List<OrderItem> items = order.getItems();
        if (items.isEmpty()) return "주문";
        String firstName = items.get(0).getProduct().getName();
        if (items.size() == 1) return firstName;
        return firstName + " 외 " + (items.size() - 1) + "건";
    }

}

