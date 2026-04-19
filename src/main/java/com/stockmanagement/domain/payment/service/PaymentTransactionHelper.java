package com.stockmanagement.domain.payment.service;

import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.PointEarnEvent;
import com.stockmanagement.common.event.ShipmentCreateEvent;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.service.OrderService;
import com.stockmanagement.domain.payment.dto.PaymentResponse;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 결제 도메인의 DB 연산을 단기 트랜잭션으로 분리하는 헬퍼.
 *
 * <p>{@link PaymentService#confirm}과 {@link PaymentService#cancel}은 외부 HTTP(Toss API)를 호출한다.
 * {@code @Transactional} 메서드 안에서 외부 HTTP를 호출하면 HTTP 응답 대기 중에도 DB 커넥션을 점유하여
 * 커넥션 풀 고갈로 이어질 수 있다.
 * 이 클래스는 HTTP 호출 이전/이후의 DB 연산을 각각 짧은 트랜잭션으로 캡슐화하여 그 위험을 제거한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class PaymentTransactionHelper {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OutboxEventStore outboxEventStore;

    // ===== confirm 흐름 =====

    /**
     * 결제 확정 전 DB 검증 트랜잭션 (짧게 끝난다).
     *
     * <p>비관적 락({@code SELECT ... FOR UPDATE})으로 payment 행을 잠그고 소유권·상태·금액을 검증한다.
     * 메서드가 반환되면 DB 커넥션은 즉시 반환된다.
     *
     * @param tossOrderId Toss 주문 ID
     * @param amount      클라이언트가 요청한 결제 금액
     * @param username    요청자 username
     * @return 이미 DONE 상태이면 {@code Optional.of(response)} (Toss API 재호출 불필요),
     *         정상 진행이면 {@code Optional.empty()}
     */
    @Transactional
    Optional<PaymentResponse> loadAndValidateForConfirm(String tossOrderId, BigDecimal amount, Long userId) {
        Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // JWT claim에서 추출한 userId로 소유권 검증 — DB users 조회 불필요
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        // 만료 스케줄러가 이미 주문을 취소한 경우 결제 진행 거부 (Toss API 호출 전에 차단)
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        // DB 상태 재확인 — Redis TTL 만료 후 재요청 시 이중 안전장치
        if (payment.getStatus() == PaymentStatus.DONE) {
            return Optional.of(PaymentResponse.from(payment));
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (payment.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // PENDING → PAYMENT_IN_PROGRESS: Toss HTTP 대기(최대 30초) 중
        // 만료 스케줄러가 이 주문을 취소하지 못하도록 상태를 선점한다.
        // Dirty Checking: 트랜잭션 종료 시 자동 flush.
        order.startPayment();

        return Optional.empty();
    }

    /**
     * 결제 실패·오류 시 PAYMENT_IN_PROGRESS → PENDING 복원 트랜잭션.
     *
     * <p>Toss API 호출 실패(네트워크 오류, HTTP 5xx) 또는 비-DONE 응답 수신 시
     * {@link PaymentService#confirm}의 catch 블록에서 호출된다.
     *
     * <p>{@code REQUIRES_NEW}를 사용하여 호출 컨텍스트가 롤백되더라도
     * 이 트랜잭션은 독립적으로 커밋된다. 복원 후 만료 스케줄러가 다시 이 주문을 정리할 수 있다.
     *
     * @param tossOrderId Toss 주문 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void resetOrderOnPaymentError(String tossOrderId) {
        paymentRepository.findByTossOrderId(tossOrderId).ifPresent(payment ->
            orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
                try {
                    order.resetPaymentFailed();
                    log.info("[Payment] 결제 오류 — 주문 상태 복원: orderId={}, PAYMENT_IN_PROGRESS → PENDING",
                            order.getId());
                } catch (BusinessException e) {
                    // PAYMENT_IN_PROGRESS가 아닌 상태 — Webhook 등 다른 경로로 이미 처리됨, 무시
                    log.warn("[Payment] 주문 상태 복원 생략 — 이미 상태 변경됨: orderId={}, currentStatus={}",
                            order.getId(), order.getStatus());
                }
            })
        );
    }

    /**
     * Toss 확정 결과를 DB에 반영하는 트랜잭션.
     *
     * <p>외부 HTTP 호출 성공 이후에 실행된다. 재시도 시 이미 DONE이면 현재 상태를 그대로 반환한다.
     * 배송 레코드 생성·포인트 적립 실패는 결제 트랜잭션을 롤백하지 않는다.
     *
     * @param tossOrderId  Toss 주문 ID
     * @param tossResponse Toss 확정 응답 (paymentKey, method, approvedAt 등 포함)
     * @return 업데이트된 결제 상세 정보
     */
    @Transactional
    PaymentResponse applyConfirmResult(String tossOrderId, TossConfirmResponse tossResponse) {
        Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 재시도 시 이미 반영됐으면 그대로 반환
        if (payment.getStatus() == PaymentStatus.DONE) {
            return PaymentResponse.from(payment);
        }

        if (!"DONE".equals(tossResponse.getStatus())) {
            String failureCode = null;
            String failureMessage = null;
            if (tossResponse.getFailure() != null) {
                failureCode = tossResponse.getFailure().getCode();
                failureMessage = tossResponse.getFailure().getMessage();
            }
            // fail() 상태를 독립 트랜잭션으로 커밋 — 예외에 의한 롤백으로 PENDING 잔존 방지
            markPaymentFailed(tossOrderId, failureCode, failureMessage);
            throw new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR,
                    failureMessage != null ? failureMessage : "결제 승인 실패");
        }

        payment.approve(
                tossResponse.getPaymentKey(),
                tossResponse.getMethod(),
                parseDateTime(tossResponse.getRequestedAt()),
                parseDateTime(tossResponse.getApprovedAt())
        );

        return doPostConfirmWork(payment);
    }

    /**
     * 결제 실패 상태를 독립 트랜잭션으로 커밋한다.
     * applyConfirmResult() 내 예외에 의한 롤백으로 fail() 상태가 소실되는 것을 방지.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markPaymentFailed(String tossOrderId, String failureCode, String failureMessage) {
        Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.fail(failureCode, failureMessage);
        log.warn("[Payment] 결제 승인 실패: tossOrderId={}, code={}, message={}",
                tossOrderId, failureCode, failureMessage);
    }

    /**
     * 가상계좌 입금 완료 Webhook에 의한 결제 확정 트랜잭션.
     *
     * <p>Toss Webhook은 최소한의 데이터(paymentKey, orderId, status)만 포함하므로
     * method·requestedAt·approvedAt은 null로 저장된다. 이 정보는 결제 후 Toss API 조회로 보완 가능하다.
     * 재시도 시 이미 DONE이면 현재 상태를 그대로 반환한다.
     *
     * @param tossOrderId Toss 주문 ID (webhook data.orderId)
     * @param paymentKey  Toss 결제 키 (webhook data.paymentKey)
     * @return 업데이트된 결제 상세 정보
     */
    @Transactional
    PaymentResponse applyWebhookConfirmResult(String tossOrderId, String paymentKey) {
        Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.DONE) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        // 가상계좌: method·requestedAt·approvedAt은 Webhook에 포함되지 않아 null 저장
        payment.approve(paymentKey, null, null, null);

        return doPostConfirmWork(payment);
    }

    /**
     * 결제 승인 후 공통 후처리: 주문 확정, 배송 생성, 포인트 적립, 아웃박스 이벤트 저장.
     *
     * <p>applyConfirmResult()와 applyWebhookConfirmResult() 양쪽에서 재사용된다.
     *
     * <p>배송 생성·포인트 적립은 {@link com.stockmanagement.common.outbox.OutboxEventProcessor}가
     * Outbox를 통해 최대 5회 재시도한다. 실패해도 결제 트랜잭션을 롤백하지 않는다.
     */
    private PaymentResponse doPostConfirmWork(Payment payment) {
        // 주문 확정: PENDING → CONFIRMED, 재고 reserved → allocated
        // 반환된 Order를 재사용하여 이후 orderRepository.findById() 중복 조회 제거
        // 극히 드문 경합: loadAndValidate(검증) → Toss HTTP 대기 → 만료 스케줄러가 취소
        // → 주문이 이미 CANCELLED인 케이스를 방어
        Order confirmedOrder;
        try {
            confirmedOrder = orderService.confirm(payment.getOrderId());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_ORDER_STATUS) {
                log.error("[Payment] CRITICAL: Toss 승인 완료됐으나 주문이 이미 취소됨 — " +
                          "수동 환불 처리 필요: paymentKey={}, orderId={}",
                          payment.getPaymentKey(), payment.getOrderId());
                // payment는 DONE으로 커밋 유지 (Toss가 청구됐으므로 환불 근거 보존)
                // 이미 취소된 주문이므로 배송/포인트/이벤트 처리 불필요 — 수동 환불 팀이 처리
                return PaymentResponse.from(payment);
            } else {
                throw e;
            }
        }

        // 배송 레코드 생성 — Outbox로 위임하여 실패 시 릴레이 스케줄러가 최대 5회 재시도
        outboxEventStore.save(new ShipmentCreateEvent(payment.getOrderId()));

        // 포인트 적립 — Outbox로 위임하여 실패 시 릴레이 스케줄러가 최대 5회 재시도
        // BigDecimal 전체 연산 후 마지막에 longValue() 호출 —
        // 중간에 longValue()를 끼우면 소수점 버림이 먼저 발생해 포인트 누수 발생
        long paidAmount = confirmedOrder.getTotalAmount()
                .subtract(confirmedOrder.getDiscountAmount())
                .subtract(BigDecimal.valueOf(confirmedOrder.getUsedPoints()))
                .max(BigDecimal.ZERO)
                .longValue();
        if (paidAmount > 0) {
            outboxEventStore.save(new PointEarnEvent(
                    confirmedOrder.getUserId(), paidAmount, confirmedOrder.getId()));
        }

        outboxEventStore.save(new PaymentConfirmedEvent(
                payment.getId(), payment.getOrderId(), payment.getAmount()));

        log.info("[Payment] 결제 확정 완료: paymentKey={}, orderId={}, amount={}",
                payment.getPaymentKey(), payment.getOrderId(), payment.getAmount());
        return PaymentResponse.from(payment);
    }

    // ===== cancel 흐름 =====

    /**
     * 결제 취소 전 DB 검증 트랜잭션 (짧게 끝난다).
     *
     * <p>비관적 락({@code SELECT ... FOR UPDATE})으로 payment 행을 잠그고 소유권·상태를 검증한다.
     * Redis 장애 등으로 분산 락이 우회된 경우에도 DB 레벨에서 중복 취소를 방지한다.
     * 메서드가 반환되면 DB 커넥션은 즉시 반환된다.
     *
     * @param paymentKey Toss 결제 키
     * @param userId     요청자 userId (JWT claim에서 추출 — IDOR 방지용 소유권 검증)
     * @param isAdmin    ADMIN 권한 여부 (ADMIN은 모든 결제 취소 가능)
     * @return 이미 CANCELLED이면 {@code Optional.of(response)}, 정상 진행이면 {@code Optional.empty()}
     */
    @Transactional
    Optional<PaymentResponse> loadAndValidateForCancel(String paymentKey, Long userId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentKeyWithLock(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 소유권 검증 — paymentKey를 아는 누구나 타인의 결제를 취소할 수 있는 IDOR 방지
        // JWT claim에서 추출한 userId 사용 — DB users 조회 불필요
        if (!isAdmin) {
            Order order = orderRepository.findById(payment.getOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return Optional.of(PaymentResponse.from(payment));
        }
        if (payment.getStatus() != PaymentStatus.DONE) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        return Optional.empty();
    }

    /**
     * Toss 취소 결과를 DB에 반영하는 트랜잭션.
     *
     * <p>외부 HTTP 호출 성공 이후에 실행된다. 재시도 시 이미 CANCELLED이면 현재 상태를 반환한다.
     *
     * @param paymentKey   Toss 결제 키
     * @param cancelReason 취소 사유
     * @return 업데이트된 결제 상세 정보
     */
    @Transactional
    PaymentResponse applyCancelResult(String paymentKey, String cancelReason) {
        Payment payment = paymentRepository.findByPaymentKeyWithLock(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 재시도 시 이미 반영됐으면 그대로 반환
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return PaymentResponse.from(payment);
        }

        payment.cancel(cancelReason);
        orderService.refund(payment.getOrderId());

        log.info("[Payment] 결제 취소 완료: paymentKey={}, orderId={}, reason={}",
                paymentKey, payment.getOrderId(), cancelReason);
        return PaymentResponse.from(payment);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return null;
        return OffsetDateTime.parse(dateTimeStr).toLocalDateTime();
    }
}
