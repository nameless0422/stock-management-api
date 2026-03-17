package com.stockmanagement.domain.payment.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.service.OrderService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    private final OrderService orderService;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentIdempotencyManager idempotencyManager;

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
    public PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        Order order = orderRepository.findByIdWithItems(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

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
     *   <li>DB 상태 재확인 (Redis TTL 만료 후 재요청 대비 이중 안전장치)
     *   <li>Calls TossPayments confirmation API
     *   <li>Transitions Payment to DONE or FAILED
     *   <li>Calls {@link OrderService#confirm(Long)} → Order CONFIRMED + Inventory allocated
     *   <li>결과를 Redis에 캐싱 (24h TTL)
     * </ol>
     *
     * @param request paymentKey, tossOrderId, and amount forwarded from the checkout widget
     * @return updated payment details
     */
    @Transactional
    public PaymentResponse confirm(PaymentConfirmRequest request) {
        String idempotencyKey = "confirm:" + request.getTossOrderId();

        // 1. Redis 완료 캐시 확인 (이전 성공 요청의 결과가 있으면 즉시 반환)
        Optional<PaymentResponse> cached = idempotencyManager.getIfCompleted(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. PROCESSING으로 원자적 선점 (SETNX) — 동시 요청 중 하나만 통과
        if (!idempotencyManager.tryAcquire(idempotencyKey)) {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS);
        }

        try {
            Payment payment = paymentRepository.findByTossOrderId(request.getTossOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            // 3. DB 상태 재확인 (Redis TTL 만료 후 재요청 시 이중 안전장치)
            if (payment.getStatus() == PaymentStatus.DONE) {
                PaymentResponse response = PaymentResponse.from(payment);
                idempotencyManager.complete(idempotencyKey, response);
                return response;
            }

            // Reject requests for already-processed (non-PENDING) payments
            if (payment.getStatus() != PaymentStatus.PENDING) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
            }

            // Server-side amount re-verification (prevents client-side amount manipulation)
            if (payment.getAmount().compareTo(request.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }

            // 4. Call TossPayments confirmation API
            TossConfirmResponse tossResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.getPaymentKey(), request.getTossOrderId(), request.getAmount())
            );

            // Handle payment failure
            if (!"DONE".equals(tossResponse.getStatus())) {
                String failureCode = null;
                String failureMessage = null;
                if (tossResponse.getFailure() != null) {
                    failureCode = tossResponse.getFailure().getCode();
                    failureMessage = tossResponse.getFailure().getMessage();
                }
                payment.fail(failureCode, failureMessage);
                log.warn("Payment failed: tossOrderId={}, code={}, message={}",
                        request.getTossOrderId(), failureCode, failureMessage);
                throw new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR,
                        failureMessage != null ? failureMessage : "결제 승인 실패");
            }

            // 5. Transition payment to DONE
            payment.approve(
                    tossResponse.getPaymentKey(),
                    tossResponse.getMethod(),
                    parseDateTime(tossResponse.getRequestedAt()),
                    parseDateTime(tossResponse.getApprovedAt())
            );

            // Confirm order: PENDING → CONFIRMED, and move inventory: reserved → allocated
            orderService.confirm(payment.getOrderId());

            // 6. 결과를 Redis에 캐싱 (24h TTL)
            PaymentResponse response = PaymentResponse.from(payment);
            idempotencyManager.complete(idempotencyKey, response);

            log.info("Payment confirmed: paymentKey={}, orderId={}, amount={}",
                    payment.getPaymentKey(), payment.getOrderId(), payment.getAmount());
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
     *   <li>DB 상태 재확인 (Redis TTL 만료 후 재요청 대비 이중 안전장치)
     *   <li>Calls TossPayments cancellation API
     *   <li>Transitions Payment to CANCELLED
     *   <li>Calls {@link OrderService#refund(Long)} → Order CANCELLED + Inventory allocated released
     *   <li>결과를 Redis에 캐싱 (24h TTL)
     * </ol>
     *
     * @param paymentKey TossPayments-assigned payment key
     * @param request    cancellation reason and optional partial amount
     * @return updated payment details
     */
    @Transactional
    public PaymentResponse cancel(String paymentKey, PaymentCancelRequest request) {
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
            Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            // 3. DB 상태 재확인 (이중 안전장치)
            if (payment.getStatus() == PaymentStatus.CANCELLED) {
                PaymentResponse response = PaymentResponse.from(payment);
                idempotencyManager.complete(idempotencyKey, response);
                return response;
            }

            // Only DONE payments can be cancelled/refunded
            if (payment.getStatus() != PaymentStatus.DONE) {
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
            }

            // 4. Call TossPayments cancellation API
            tossPaymentsClient.cancel(paymentKey,
                    new TossCancelRequest(request.getCancelReason(), request.getCancelAmount()));

            // Transition payment to CANCELLED
            payment.cancel(request.getCancelReason());

            // Refund order: CONFIRMED → CANCELLED, and release inventory: allocated → freed
            orderService.refund(payment.getOrderId());

            // 5. 결과를 Redis에 캐싱 (24h TTL)
            PaymentResponse response = PaymentResponse.from(payment);
            idempotencyManager.complete(idempotencyKey, response);

            log.info("Payment cancelled: paymentKey={}, orderId={}, reason={}",
                    paymentKey, payment.getOrderId(), request.getCancelReason());
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
     * (관리자 주문 상세 조회용 — 결제 전 PENDING 주문은 null 반환)
     *
     * @param orderId 조회할 주문 ID
     * @return 결제 정보 (없으면 Optional.empty())
     */
    public Optional<PaymentResponse> getByOrderId(Long orderId) {
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

    /**
     * Parses an ISO-8601 datetime string (with timezone offset) into {@link LocalDateTime}.
     * TossPayments returns timestamps in the format {@code 2024-01-01T00:00:00+09:00}.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return null;
        return OffsetDateTime.parse(dateTimeStr).toLocalDateTime();
    }
}
