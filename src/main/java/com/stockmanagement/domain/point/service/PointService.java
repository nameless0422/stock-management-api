package com.stockmanagement.domain.point.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.point.dto.PointBalanceResponse;
import com.stockmanagement.domain.point.dto.PointTransactionResponse;
import com.stockmanagement.domain.point.entity.PointTransaction;
import com.stockmanagement.domain.point.entity.PointTransactionStatus;
import com.stockmanagement.domain.point.entity.PointTransactionType;
import com.stockmanagement.domain.point.entity.UserPoint;
import com.stockmanagement.domain.point.repository.PointTransactionRepository;
import com.stockmanagement.domain.point.repository.UserPointRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 적립/사용/환불을 처리하는 서비스.
 *
 * <p>적립 흐름:
 * <ol>
 *   <li>결제 확정 → {@code earn()} — PENDING 상태로 트랜잭션 생성 (잔액 미반영)
 *   <li>배송 완료 → {@code confirmPending()} — CONFIRMED 전환 + 잔액 반영
 *   <li>주문 취소 → {@code expirePending()} — EXPIRED 전환 (잔액 변동 없음)
 * </ol>
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>{@code earn}, {@code confirmPending} — {@code REQUIRES_NEW}: 독립 커밋
 *   <li>{@code use}, {@code refundByOrder} — {@code REQUIRED}: 주문 트랜잭션에 참여
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PointService {

    /** 결제 완료 시 적립 비율 (1%) */
    private static final double EARN_RATE = 0.01;

    @Value("${point.expiry.retention-days:365}")
    private int retentionDays;

    private final UserPointRepository userPointRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    /** 적립금 부분 회수 발생 횟수 카운터 — 잔액 부족으로 전액 회수 불가 시 증가 */
    private Counter pointPartialReclaimCounter;

    @PostConstruct
    public void initMetrics() {
        pointPartialReclaimCounter = Counter.builder("point.partial_reclaim")
                .description("포인트 주문 취소 시 잔액 부족으로 적립금이 부분 회수된 횟수")
                .register(meterRegistry);
    }

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

    /** 적립 예정 (PENDING) 포인트 이력을 최신순 페이징 조회한다. */
    public Page<PointTransactionResponse> getPendingHistory(String username, Pageable pageable) {
        User user = findUser(username);
        return pointTransactionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                        user.getId(), PointTransactionStatus.PENDING, pageable)
                .map(PointTransactionResponse::from);
    }

    /** 만료 예정 CONFIRMED 포인트를 만료일 가까운 순으로 페이징 조회한다. */
    public Page<PointTransactionResponse> getExpiringSoon(String username, int withinDays, Pageable pageable) {
        User user = findUser(username);
        LocalDateTime deadline = LocalDateTime.now().plusDays(withinDays);
        return pointTransactionRepository.findByUserIdAndStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        user.getId(), PointTransactionStatus.CONFIRMED, deadline, pageable)
                .map(PointTransactionResponse::from);
    }

    // ===== 변경 =====

    /**
     * 포인트 잔액이 충분한지 사전 검증한다.
     *
     * <p>주문 생성 시 재고 예약·쿠폰 적용 등 뮤테이션 전에 호출하여,
     * 포인트 부족으로 인한 불필요한 롤백을 방지한다.
     * 잔액과 요청 사이 경합이 있을 수 있으므로 실제 차감은 {@link #use}에서 한 번 더 검증한다.
     *
     * @throws BusinessException 잔액 부족 시 {@code INSUFFICIENT_POINTS}
     */
    @Transactional(readOnly = true)
    public void validateBalance(Long userId, long requiredPoints) {
        long balance = userPointRepository.findByUserId(userId)
                .map(UserPoint::getBalance)
                .orElse(0L);
        if (balance < requiredPoints) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
        }
    }

    /**
     * 결제 완료 시 포인트 적립 예정 트랜잭션을 생성한다 (PENDING, 잔액 미반영).
     *
     * <p>배송 완료 시 {@link #confirmPending(Long)}으로 CONFIRMED 전환 후 잔액에 반영된다.
     * 주문 취소 시 {@link #expirePending(Long)}으로 EXPIRED 전환된다.
     *
     * <p>{@code REQUIRES_NEW}: 결제 확정 트랜잭션과 독립적으로 커밋/롤백된다.
     *
     * @param userId     수혜 사용자 ID
     * @param paidAmount 실 결제 금액 (포인트/쿠폰 차감 후)
     * @param orderId    연관 주문 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void earn(Long userId, long paidAmount, Long orderId) {
        if (paidAmount <= 0) return;
        // 멱등성 보장 — Outbox 재처리 시 이중 적립 방지 (앱 레벨 1차, DB UNIQUE 2차)
        if (pointTransactionRepository.existsByOrderIdAndType(orderId, PointTransactionType.EARN)) {
            return;
        }
        long earnAmount = Math.max(1L, Math.round(paidAmount * EARN_RATE));

        pointTransactionRepository.save(PointTransaction.builder()
                .userId(userId)
                .amount(earnAmount)
                .type(PointTransactionType.EARN)
                .status(PointTransactionStatus.PENDING)
                .description("주문 구매 적립 예정 (주문 #" + orderId + ")")
                .orderId(orderId)
                .expiresAt(LocalDateTime.now().plusDays(retentionDays))
                .build());
    }

    /**
     * 배송 완료 시 PENDING 적립 포인트를 확정한다 (CONFIRMED + 잔액 반영).
     *
     * <p>PENDING 트랜잭션이 없으면 skip (이미 확정되었거나 적립 없음).
     *
     * @param orderId 배송 완료된 주문 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmPending(Long orderId) {
        pointTransactionRepository.findByOrderIdAndTypeAndStatus(
                orderId, PointTransactionType.EARN, PointTransactionStatus.PENDING
        ).ifPresent(tx -> {
            tx.confirm();
            UserPoint userPoint = getOrCreate(tx.getUserId());
            userPoint.earn(tx.getAmount());
            log.info("[Point] 적립 확정: userId={}, orderId={}, amount={}", tx.getUserId(), orderId, tx.getAmount());
        });
    }

    /**
     * 주문 취소 시 PENDING 적립 포인트를 만료시킨다 (잔액 변동 없음).
     *
     * @param orderId 취소된 주문 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expirePending(Long orderId) {
        pointTransactionRepository.findByOrderIdAndTypeAndStatus(
                orderId, PointTransactionType.EARN, PointTransactionStatus.PENDING
        ).ifPresent(tx -> {
            tx.expire();
            log.info("[Point] 적립 만료: userId={}, orderId={}, amount={}", tx.getUserId(), orderId, tx.getAmount());
        });
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
        UserPoint userPoint = getOrCreate(userId);
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
     * <p>PENDING 적립 포인트는 만료 처리하고, CONFIRMED 적립 포인트는 회수한다.
     * 사용된 포인트는 반환한다.
     *
     * @param userId  사용자 ID
     * @param orderId 취소된 주문 ID
     */
    @Transactional
    public void refundByOrder(Long userId, Long orderId) {
        // 멱등성: 이미 REFUND 처리된 주문이면 중복 실행 방지
        if (pointTransactionRepository.existsByOrderIdAndType(orderId, PointTransactionType.REFUND)) {
            return;
        }
        List<PointTransaction> orderTxns = pointTransactionRepository.findByOrderId(orderId);
        if (orderTxns.isEmpty()) return;

        UserPoint userPoint = getOrCreate(userId);
        List<PointTransaction> newTxns = new ArrayList<>();

        orderTxns.forEach(tx -> {
            if (tx.getType() == PointTransactionType.USE) {
                // 사용 포인트 반환
                long refundAmount = Math.abs(tx.getAmount());
                userPoint.refund(refundAmount);
                newTxns.add(PointTransaction.builder()
                        .userId(userId)
                        .amount(refundAmount)
                        .type(PointTransactionType.REFUND)
                        .description("주문 취소 포인트 반환 (주문 #" + orderId + ")")
                        .orderId(orderId)
                        .build());
            } else if (tx.getType() == PointTransactionType.EARN) {
                if (tx.getStatus() == PointTransactionStatus.PENDING) {
                    // PENDING 적립 → EXPIRED (잔액 변동 없음)
                    tx.expire();
                } else if (tx.getStatus() == PointTransactionStatus.CONFIRMED) {
                    // CONFIRMED 적립 → 회수 (잔액에서 차감)
                    long reclaimAmount = tx.getAmount();
                    long actualReclaim = Math.min(reclaimAmount, userPoint.getBalance());
                    if (actualReclaim > 0) {
                        userPoint.use(actualReclaim);
                        newTxns.add(PointTransaction.builder()
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
                        pointPartialReclaimCounter.increment();
                    }
                }
            }
        });

        if (!newTxns.isEmpty()) {
            pointTransactionRepository.saveAll(newTxns);
        }
    }

    /**
     * 만료일이 경과한 CONFIRMED 포인트를 일괄 만료 처리한다.
     * 사용자별로 잔액 차감 후 EXPIRED 전환.
     *
     * @return 만료 처리된 트랜잭션 수
     */
    @Transactional
    public int expireBySchedule() {
        LocalDateTime now = LocalDateTime.now();
        List<PointTransaction> expired = pointTransactionRepository
                .findByStatusAndExpiresAtBefore(PointTransactionStatus.CONFIRMED, now);
        if (expired.isEmpty()) return 0;

        // 사용자별 그룹핑하여 잔액 차감
        Map<Long, List<PointTransaction>> byUser = expired.stream()
                .collect(Collectors.groupingBy(PointTransaction::getUserId));

        byUser.forEach((userId, txns) -> {
            UserPoint userPoint = getOrCreate(userId);
            long totalExpire = 0;
            for (PointTransaction tx : txns) {
                long actualExpire = Math.min(tx.getAmount(), userPoint.getBalance());
                if (actualExpire > 0) {
                    userPoint.use(actualExpire);
                }
                tx.expireConfirmed();
                totalExpire += tx.getAmount();
            }
            log.info("[Point] 기간 만료: userId={}, 건수={}, 총액={}", userId, txns.size(), totalExpire);
        });

        return expired.size();
    }

    private UserPoint getOrCreate(Long userId) {
        return userPointRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    try {
                        return userPointRepository.save(
                                UserPoint.builder().userId(userId).build());
                    } catch (DataIntegrityViolationException e) {
                        return userPointRepository.findByUserIdWithLock(userId)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
