package com.stockmanagement.domain.refund.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.dto.PaymentCancelRequest;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.entity.Refund;
import com.stockmanagement.domain.refund.entity.RefundStatus;
import com.stockmanagement.domain.refund.repository.RefundRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final OrderRepository orderRepository;
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
     *
     * <p>{@code noRollbackFor}: paymentService.cancel() 실패 시에도 트랜잭션을 커밋하여
     * Refund FAILED 상태(Dirty Checking)가 DB에 반영되도록 한다.
     * catch 블록이 Exception을 잡아 re-throw하므로 checked exception까지 포함해야 한다.
     * Error 계열(OOM 등)은 여기서 잡히지 않으므로 여전히 롤백된다.
     */
    @Transactional(noRollbackFor = Exception.class)
    public RefundResponse requestRefund(RefundRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 요청자가 해당 주문의 소유자인지 검증
        Order paymentOrder = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!paymentOrder.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.REFUND_ACCESS_DENIED);
        }

        // DONE 상태가 아닌 결제는 환불 불가 (paymentKey가 null이면 NPE 발생 방지)
        if (payment.getStatus() != PaymentStatus.DONE || payment.getPaymentKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        // 기존 환불 레코드 조회
        // - PENDING/COMPLETED: 중복 환불 → 예외
        // - FAILED: 이전 시도 실패 → 재시도 허용 (paymentId UNIQUE 제약으로 새 레코드 생성 불가 → 기존 레코드 재사용)
        Refund refund = refundRepository.findByPaymentId(payment.getId())
                .map(existing -> {
                    if (existing.getStatus() != RefundStatus.FAILED) {
                        throw new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS);
                    }
                    existing.reset(request.getReason());
                    return existing;
                })
                .orElseGet(() -> refundRepository.save(Refund.builder()
                        .paymentId(payment.getId())
                        .orderId(payment.getOrderId())
                        .userId(user.getId())
                        .amount(payment.getAmount())
                        .reason(request.getReason())
                        .build()));

        try {
            // 소유권은 이미 위에서 검증했으므로 userId 전달 (isAdmin=false)
            paymentService.cancel(payment.getPaymentKey(),
                    PaymentCancelRequest.of(request.getReason()), user.getId(), false);
            refund.complete();
            log.info("[Refund] 환불 완료: refundId={}, paymentId={}", refund.getId(), payment.getId());
        } catch (Exception e) {
            refund.fail();
            log.error("[Refund] 환불 실패: refundId={}, paymentId={} error={}", refund.getId(), payment.getId(), e.getMessage());
            throw e;
        }

        return RefundResponse.from(refund);
    }

    /** 환불 ID로 단건 조회한다. 본인 또는 ADMIN만 가능. */
    public RefundResponse getById(Long refundId, String username, boolean isAdmin) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        validateOwnership(refund, user, isAdmin);
        return RefundResponse.from(refund);
    }

    /** 현재 인증 사용자의 환불 목록을 최신순으로 페이징 조회한다. */
    public Page<RefundResponse> getMyRefunds(Long userId, Pageable pageable) {
        return refundRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(RefundResponse::from);
    }

    /** 결제 ID로 환불 정보를 조회한다. 본인 또는 ADMIN만 가능. */
    public RefundResponse getByPaymentId(Long paymentId, String username, boolean isAdmin) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Refund refund = refundRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        validateOwnership(refund, user, isAdmin);
        return RefundResponse.from(refund);
    }

    /**
     * 환불 소유권을 검증한다.
     * ADMIN은 모든 환불에 접근 가능하며, USER는 본인 주문의 환불만 조회할 수 있다.
     *
     * <p>Refund.userId(비정규화)를 사용하여 orders 테이블 추가 조회를 제거한다.
     */
    private void validateOwnership(Refund refund, User user, boolean isAdmin) {
        if (isAdmin) return;
        if (!refund.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.REFUND_ACCESS_DENIED);
        }
    }
}
