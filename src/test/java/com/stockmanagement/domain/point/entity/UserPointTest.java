package com.stockmanagement.domain.point.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserPoint 엔티티 단위 테스트")
class UserPointTest {

    private UserPoint createUserPoint(long balance) throws Exception {
        UserPoint up = UserPoint.builder().userId(1L).build();
        Field f = UserPoint.class.getDeclaredField("balance");
        f.setAccessible(true);
        f.set(up, balance);
        return up;
    }

    @Test
    @DisplayName("earn — 양수 금액 적립 성공")
    void earn_success() throws Exception {
        UserPoint up = createUserPoint(0L);
        up.earn(1000L);
        assertThat(up.getBalance()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("earn — amount <= 0이면 IllegalArgumentException")
    void earn_rejectsNonPositive() throws Exception {
        UserPoint up = createUserPoint(0L);
        assertThatThrownBy(() -> up.earn(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> up.earn(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("use — 잔액 차감 성공")
    void use_success() throws Exception {
        UserPoint up = createUserPoint(500L);
        up.use(300L);
        assertThat(up.getBalance()).isEqualTo(200L);
    }

    @Test
    @DisplayName("use — amount <= 0이면 IllegalArgumentException")
    void use_rejectsNonPositive() throws Exception {
        UserPoint up = createUserPoint(500L);
        assertThatThrownBy(() -> up.use(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> up.use(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("use — 잔액 부족 시 INSUFFICIENT_POINTS")
    void use_insufficientBalance() throws Exception {
        UserPoint up = createUserPoint(100L);
        assertThatThrownBy(() -> up.use(200L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
    }

    @Test
    @DisplayName("refund — 환불 적립 성공")
    void refund_success() throws Exception {
        UserPoint up = createUserPoint(0L);
        up.refund(500L);
        assertThat(up.getBalance()).isEqualTo(500L);
    }

    @Test
    @DisplayName("refund — amount <= 0이면 IllegalArgumentException")
    void refund_rejectsNonPositive() throws Exception {
        UserPoint up = createUserPoint(0L);
        assertThatThrownBy(() -> up.refund(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> up.refund(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
