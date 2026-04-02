package com.stockmanagement.domain.payment.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.infrastructure.PaymentIdempotencyManager;
import com.stockmanagement.domain.payment.infrastructure.TossPaymentsClient;
import com.stockmanagement.domain.payment.infrastructure.dto.TossCancelRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core business logic for the payment domain.
 *
 * <p>Transaction strategy:
 * <ul>
 *   <li>Class-level: {@code @Transactional(readOnly = true)} – default for queries
 *   <li>Write methods: individually overridden with {@code @Transactional}
 * </ul>
 *
 * <p>Payment flow:
 * <pre>
 *   1. prepare()  – validate order, create PENDING Payment, return tossOrderId + amount
 *   2. confirm()  – verify amount, call Toss confirm API, transition PENDING → DONE,
 *                   call OrderService.confirm() → Inventory reserved → allocated
 *   3. cancel()   – call Toss cancel API, transition DONE → CANCELLED,
 *                   call OrderService.refund() → Inventory allocated released
 * </pre>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentIdempotencyManager idempotencyManager;
    private final UserRepository userRepository;
    private final PaymentTransactionHelper transactionHelper;

    /**
     * Prepares a payment session for the given order.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validates the order exists and is in PENDING status
     *   <li>Validates the client-supplied amount against the server-stored total
     *   <li>Returns an existing PENDING payment if one already exists (idempotency)
     *   <li>Generates a unique {@code tossOrderId} and creates a PENDING Payment record
     * </ol>
     *
     * @param request contains our internal orderId and expected amount
     * @return tossOrderId and verified amount to pass to the TossPayments checkout widget
     */
    @Transactional
    public PaymentPrepareResponse prepare(PaymentPrepareRequest request, String username) {
        Order order = orderRepository.findByIdWithItems(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 요청자가 주문 소유자인지 검증
        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getId();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        // Only PENDING orders can initiate a new payment
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        // Server-side amount validation to prevent client-side manipulation
        if (order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // Idempotency: return existing PENDING payment if already prepared
        Optional<Payment> existing = paymentRepository.findByOrderId(order.getId());
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.PENDING) {
            return buildPrepareResponse(existing.get(), order);
        }

        // Generate a unique tossOrderId for this payment attempt
        String tossOrderId = buildTossOrderId(order.getId());

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .tossOrderId(tossOrderId)
                .amount(order.getTotalAmount())
                .build();

        Payment saved = paymentRepository.save(payment);
        return buildPrepareResponse(saved, order);
    }

    /**
     * Confirms the payment with TossPayments and updates internal state.
     *
     * <p>Steps:
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
     * @param request paymentKey, tossOrderId, and amount forwarded from the checkout widget
     * @return updated payment details
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse confirm(PaymentConfirmRequest request, String username) {
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
                    request.getTossOrderId(), request.getAmount(), username);
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
            // 실패 시 Redis 키 삭제 → 클라이언트 재시도 허용
            idempotencyManager.release(idempotencyKey);
            throw e;
        }
    }

    /**
     * Cancels a payment and processes a full or partial refund.
     *
     * <p>Steps:
     * <ol>
     *   <li>Redis 완료 캐시 확인 → 있으면 즉시 반환 (idempotency)
     *   <li>Redis SETNX로 PROCESSING 상태 선점 → 실패 시 처리 중 예외
     *   <li>[Short TX] DB 검증 — {@link PaymentTransactionHelper#loadAndValidateForCancel}
     *   <li>Toss API 호출 (DB 커넥션 미점유)
     *   <li>[Short TX] 취소 결과 반영 — {@link PaymentTransactionHelper#applyCancelResult}
     *   <li>결과를 Redis에 캐싱 (24h TTL)
     * </ol>
     *
     * @param paymentKey TossPayments-assigned payment key
     * @param request    cancellation reason and optional partial amount
     * @return updated payment details
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse cancel(String paymentKey, PaymentCancelRequest request, String username, boolean isAdmin) {
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
            Optional<PaymentResponse> existing = transactionHelper.loadAndValidateForCancel(paymentKey, username, isAdmin);
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
     * Handles incoming webhook events from TossPayments.
     *
     * <p>TossPayments expects HTTP 2xx within 10 seconds.
     * Currently logs PAYMENT_STATUS_CHANGED events. Virtual account deposit completion
     * (WAITING_FOR_DEPOSIT → DONE) requires additional handling in production.
     *
     * @param event parsed webhook payload
     */
    @Transactional
    public void handleWebhook(TossWebhookEvent event) {
        if (!"PAYMENT_STATUS_CHANGED".equals(event.getEventType())) {
            log.debug("Ignoring unsupported webhook event type: {}", event.getEventType());
            return;
        }

        TossWebhookEvent.Data data = event.getData();
        log.info("Received webhook: eventType={}, status={}, tossOrderId={}",
                event.getEventType(), data.getStatus(), data.getOrderId());

        paymentRepository.findByTossOrderId(data.getOrderId()).ifPresent(payment -> {
            switch (data.getStatus()) {
                case "DONE" -> {
                    // For virtual account payments: the confirm endpoint may receive
                    // WAITING_FOR_DEPOSIT and leave the payment PENDING until the customer deposits.
                    // This webhook fires when the deposit arrives.
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        log.info("Webhook DONE received for PENDING payment: tossOrderId={}. " +
                                "Virtual account deposit detected – manual review may be required.",
                                data.getOrderId());
                        // TODO: implement full webhook-driven confirmation for virtual accounts
                    }
                }
                case "CANCELED" ->
                    log.info("Webhook CANCELED: tossOrderId={}", data.getOrderId());
                default ->
                    log.debug("Webhook unhandled status={}: tossOrderId={}", data.getStatus(), data.getOrderId());
            }
        });
    }

    /**
     * Retrieves payment details by TossPayments paymentKey.
     *
     * @param paymentKey TossPayments-assigned payment key
     * @return payment details
     */
    public PaymentResponse getByPaymentKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
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
    public Optional<PaymentResponse> getByOrderId(Long orderId, String username, boolean isAdmin) {
        if (!isAdmin) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            Long userId = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getId();
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }
        return paymentRepository.findByOrderId(orderId).map(PaymentResponse::from);
    }

    // ===== Private helpers =====

    /**
     * Generates a unique tossOrderId for a payment attempt.
     * Format: {@code order-{orderId}-{8-char UUID suffix}}
     * Satisfies TossPayments orderId constraints: 6–64 chars, alphanumeric + hyphen/underscore.
     */
    private String buildTossOrderId(Long orderId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "order-" + orderId + "-" + suffix;
    }

    /**
     * Builds a human-readable order name for the checkout widget.
     * Example: "MacBook Pro 외 2건"
     */
    private PaymentPrepareResponse buildPrepareResponse(Payment payment, Order order) {
        return new PaymentPrepareResponse(
                payment.getTossOrderId(),
                payment.getAmount(),
                buildOrderName(order)
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

