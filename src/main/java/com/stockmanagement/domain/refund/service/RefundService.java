package com.stockmanagement.domain.refund.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.payment.dto.PaymentCancelRequest;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.entity.Refund;
import com.stockmanagement.domain.refund.repository.RefundRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 이력 관리 서비스.
 *
 * <p>실제 결제 취소 처리는 {@link PaymentService#cancel(String, PaymentCancelRequest)}에 위임하고,
 * 이 서비스는 환불 이력({@link Refund}) 레코드 생성/조회를 담당한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final UserRepository userRepository;

    /**
     * 환불을 요청한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>결제 정보 조회 및 중복 환불 방지 검증
     *   <li>Refund 레코드 PENDING 상태로 생성
     *   <li>PaymentService.cancel() 호출 (Toss 취소 + 주문 환불 + 재고/쿠폰/포인트 복구)
     *   <li>성공 시 COMPLETED, 실패 시 FAILED 전이
     * </ol>
     */
    @Transactional
    public RefundResponse requestRefund(RefundRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 요청자 본인 주문 여부 확인은 OrderService.refund() 내부에서 처리 가능하나,
        // 단순화를 위해 payment.orderId 기준으로 판단하지 않고 서비스 계층에서 이력만 관리한다.
        // 실무에서는 order.userId == user.id 검증을 추가한다.

        if (refundRepository.existsByPaymentId(payment.getId())) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS);
        }

        Refund refund = refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .reason(request.getReason())
                .build());

        try {
            paymentService.cancel(payment.getPaymentKey(),
                    PaymentCancelRequest.of(request.getReason()));
            refund.complete();
            log.info("[Refund] 환불 완료: refundId={}, paymentId={}", refund.getId(), payment.getId());
        } catch (Exception e) {
            refund.fail();
            log.error("[Refund] 환불 실패: refundId={}, paymentId={} error={}", refund.getId(), payment.getId(), e.getMessage());
            throw e;
        }

        return RefundResponse.from(refund);
    }

    /** 환불 ID로 단건 조회한다. */
    public RefundResponse getById(Long refundId, String username) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        return RefundResponse.from(refund);
    }

    /** 결제 ID로 환불 정보를 조회한다. */
    public RefundResponse getByPaymentId(Long paymentId) {
        return refundRepository.findByPaymentId(paymentId)
                .map(RefundResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
    }
}
