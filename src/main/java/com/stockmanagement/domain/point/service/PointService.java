package com.stockmanagement.domain.point.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.point.dto.PointBalanceResponse;
import com.stockmanagement.domain.point.dto.PointTransactionResponse;
import com.stockmanagement.domain.point.entity.PointTransaction;
import com.stockmanagement.domain.point.entity.PointTransactionType;
import com.stockmanagement.domain.point.entity.UserPoint;
import com.stockmanagement.domain.point.repository.PointTransactionRepository;
import com.stockmanagement.domain.point.repository.UserPointRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 적립/사용/환불을 처리하는 서비스.
 *
 * <p>모든 잔액 변경 메서드는 호출 측 트랜잭션에 참여({@link Propagation#MANDATORY})하여
 * 비즈니스 로직과 원자적으로 처리된다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PointService {

    /** 결제 완료 시 적립 비율 (1%) */
    private static final double EARN_RATE = 0.01;

    private final UserPointRepository userPointRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final UserRepository userRepository;

    // ===== 조회 =====

    /** 포인트 잔액을 조회한다. 포인트 계정이 없으면 0을 반환한다. */
    public PointBalanceResponse getBalance(String username) {
        User user = findUser(username);
        long balance = userPointRepository.findByUserId(user.getId())
                .map(UserPoint::getBalance)
                .orElse(0L);
        return PointBalanceResponse.of(user.getId(), balance);
    }

    /** 포인트 변동 이력을 최신순 페이징 조회한다. */
    public Page<PointTransactionResponse> getHistory(String username, Pageable pageable) {
        User user = findUser(username);
        return pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(PointTransactionResponse::from);
    }

    // ===== 변경 (호출 측 트랜잭션 필요) =====

    /**
     * 결제 완료 시 포인트를 적립한다 (결제금액의 1%).
     *
     * @param userId    수혜 사용자 ID
     * @param paidAmount 실 결제 금액 (포인트/쿠폰 차감 후)
     * @param orderId   연관 주문 ID
     */
    @Transactional
    public void earn(Long userId, long paidAmount, Long orderId) {
        long earnAmount = Math.max(1L, Math.round(paidAmount * EARN_RATE));
        UserPoint userPoint = getOrCreate(userId);
        userPoint.earn(earnAmount);

        pointTransactionRepository.save(PointTransaction.builder()
                .userId(userId)
                .amount(earnAmount)
                .type(PointTransactionType.EARN)
                .description("주문 구매 적립 (주문 #" + orderId + ")")
                .orderId(orderId)
                .build());
    }

    /**
     * 주문 시 포인트를 차감한다.
     *
     * @param userId      사용자 ID
     * @param usePoints   차감할 포인트
     * @param orderId     연관 주문 ID
     * @throws BusinessException 잔액 부족 시
     */
    @Transactional
    public void use(Long userId, long usePoints, Long orderId) {
        if (usePoints <= 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_AMOUNT);
        }
        UserPoint userPoint = userPointRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_POINTS));
        userPoint.use(usePoints);

        pointTransactionRepository.save(PointTransaction.builder()
                .userId(userId)
                .amount(-usePoints)
                .type(PointTransactionType.USE)
                .description("주문 포인트 사용 (주문 #" + orderId + ")")
                .orderId(orderId)
                .build());
    }

    /**
     * 주문 취소/환불 시 포인트를 원상복구한다.
     *
     * <p>사용된 포인트 반환 + 결제 완료로 적립된 포인트 회수를 모두 처리한다.
     *
     * @param userId  사용자 ID
     * @param orderId 취소된 주문 ID
     */
    @Transactional
    public void refundByOrder(Long userId, Long orderId) {
        UserPoint userPoint = getOrCreate(userId);

        pointTransactionRepository.findByOrderId(orderId).forEach(tx -> {
            if (tx.getType() == PointTransactionType.USE) {
                // 사용 포인트 반환
                long refundAmount = Math.abs(tx.getAmount());
                userPoint.refund(refundAmount);
                pointTransactionRepository.save(PointTransaction.builder()
                        .userId(userId)
                        .amount(refundAmount)
                        .type(PointTransactionType.REFUND)
                        .description("주문 취소 포인트 반환 (주문 #" + orderId + ")")
                        .orderId(orderId)
                        .build());
            } else if (tx.getType() == PointTransactionType.EARN) {
                // 적립 포인트 회수 (취소 시 소급 적용)
                // 잔액 부족 시 회수 가능한 만큼만 차감하고, 트랜잭션 기록도 실제 차감량으로 남긴다
                long reclaimAmount = tx.getAmount();
                long actualReclaim = Math.min(reclaimAmount, userPoint.getBalance());
                if (actualReclaim > 0) {
                    userPoint.use(actualReclaim);
                    pointTransactionRepository.save(PointTransaction.builder()
                            .userId(userId)
                            .amount(-actualReclaim)
                            .type(PointTransactionType.EXPIRE)
                            .description("주문 취소 적립금 회수 (주문 #" + orderId + ")")
                            .orderId(orderId)
                            .build());
                }
                if (actualReclaim < reclaimAmount) {
                    log.warn("[Point] 적립금 전액 회수 불가: userId={}, orderId={}, 요청={}, 실제={}",
                            userId, orderId, reclaimAmount, actualReclaim);
                }
            }
        });
    }

    private UserPoint getOrCreate(Long userId) {
        return userPointRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> userPointRepository.save(
                        UserPoint.builder().userId(userId).build()));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
