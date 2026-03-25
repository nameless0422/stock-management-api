package com.stockmanagement.domain.point.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.point.dto.PointBalanceResponse;
import com.stockmanagement.domain.point.entity.PointTransaction;
import com.stockmanagement.domain.point.entity.PointTransactionType;
import com.stockmanagement.domain.point.entity.UserPoint;
import com.stockmanagement.domain.point.repository.PointTransactionRepository;
import com.stockmanagement.domain.point.repository.UserPointRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointService 단위 테스트")
class PointServiceTest {

    @Mock private UserPointRepository userPointRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private PointService pointService;

    private User mockUser(Long id, String username) {
        User u = User.builder().username(username).password("pw").email("e@e.com").build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private UserPoint mockUserPoint(Long userId, long balance) {
        UserPoint up = UserPoint.builder().userId(userId).build();
        ReflectionTestUtils.setField(up, "balance", balance);
        return up;
    }

    @Nested
    @DisplayName("getBalance() — 잔액 조회")
    class GetBalance {

        @Test
        @DisplayName("포인트 계정 있음 → 잔액 반환")
        void withAccount() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L, "user1")));
            given(userPointRepository.findByUserId(1L)).willReturn(Optional.of(mockUserPoint(1L, 5000)));

            PointBalanceResponse response = pointService.getBalance("user1");

            assertThat(response.getBalance()).isEqualTo(5000);
        }

        @Test
        @DisplayName("포인트 계정 없음 → 0 반환")
        void withoutAccount() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L, "user1")));
            given(userPointRepository.findByUserId(1L)).willReturn(Optional.empty());

            PointBalanceResponse response = pointService.getBalance("user1");

            assertThat(response.getBalance()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("earn() — 포인트 적립")
    class Earn {

        @Test
        @DisplayName("적립 성공 → balance 증가 + 이력 저장")
        void success() {
            UserPoint up = mockUserPoint(1L, 0);
            given(userPointRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(up));
            given(pointTransactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            pointService.earn(1L, 10000L, 100L);

            assertThat(up.getBalance()).isEqualTo(100L); // 10000 * 0.01 = 100
            verify(pointTransactionRepository).save(any());
        }
    }

    @Nested
    @DisplayName("use() — 포인트 사용")
    class Use {

        @Test
        @DisplayName("잔액 충분 → 차감 성공")
        void success() {
            UserPoint up = mockUserPoint(1L, 5000);
            given(userPointRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(up));
            given(pointTransactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            pointService.use(1L, 2000L, 100L);

            assertThat(up.getBalance()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("잔액 부족 → INSUFFICIENT_POINTS")
        void insufficientBalance() {
            UserPoint up = mockUserPoint(1L, 100);
            given(userPointRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(up));

            assertThatThrownBy(() -> pointService.use(1L, 5000L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_POINTS);
        }

        @Test
        @DisplayName("포인트 계정 없음 → 잔액 0으로 생성 후 INSUFFICIENT_POINTS")
        void noAccount() {
            given(userPointRepository.findByUserIdWithLock(1L)).willReturn(Optional.empty());
            given(userPointRepository.save(any(UserPoint.class))).willAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> pointService.use(1L, 1000L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_POINTS);
        }

        @Test
        @DisplayName("0 이하 금액 → INVALID_POINT_AMOUNT")
        void invalidAmount() {
            assertThatThrownBy(() -> pointService.use(1L, 0L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_POINT_AMOUNT);
        }
    }

    @Nested
    @DisplayName("refundByOrder() — 포인트 환불")
    class RefundByOrder {

        @Test
        @DisplayName("USE + EARN 이력 있음 → 반환 + 회수 처리")
        void refundsBothUseAndEarn() {
            UserPoint up = mockUserPoint(1L, 200); // earn된 상태
            given(userPointRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(up));

            PointTransaction useTx = PointTransaction.builder()
                    .userId(1L).amount(-2000L)
                    .type(PointTransactionType.USE)
                    .description("사용").orderId(100L).build();
            PointTransaction earnTx = PointTransaction.builder()
                    .userId(1L).amount(200L)
                    .type(PointTransactionType.EARN)
                    .description("적립").orderId(100L).build();

            given(pointTransactionRepository.findByOrderId(100L)).willReturn(List.of(useTx, earnTx));
            given(pointTransactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            pointService.refundByOrder(1L, 100L);

            // USE → REFUND: +2000, EARN → EXPIRE: -200 (잔액에서 회수)
            assertThat(up.getBalance()).isEqualTo(200 + 2000 - 200);
        }

        @Test
        @DisplayName("이력 없음 → no-op")
        void noTransactions() {
            UserPoint up = mockUserPoint(1L, 0);
            given(userPointRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(up));
            given(pointTransactionRepository.findByOrderId(100L)).willReturn(List.of());

            pointService.refundByOrder(1L, 100L);

            assertThat(up.getBalance()).isEqualTo(0);
        }
    }
}
