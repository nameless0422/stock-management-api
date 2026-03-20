package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExpiryScheduler 단위 테스트")
class OrderExpirySchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        // @Value 필드는 Spring 컨텍스트 없이 @InjectMocks로 주입되지 않으므로 직접 설정
        ReflectionTestUtils.setField(scheduler, "expiryMinutes", 30);
    }

    @Test
    @DisplayName("만료 주문이 없으면 cancel 호출 없이 종료한다")
    void noExpiredOrders_doesNothing() {
        given(orderRepository.findExpiredPendingOrderIds(any(LocalDateTime.class))).willReturn(List.of());

        scheduler.cancelExpiredOrders();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("만료 주문이 있으면 각 주문에 대해 cancel을 호출한다")
    void expiredOrdersExist_cancelsAll() {
        given(orderRepository.findExpiredPendingOrderIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L, 2L, 3L));

        scheduler.cancelExpiredOrders();

        verify(orderService).cancel(1L);
        verify(orderService).cancel(2L);
        verify(orderService).cancel(3L);
        verifyNoMoreInteractions(orderService);
    }

    @Test
    @DisplayName("일부 취소가 실패해도 나머지 주문 취소는 계속 진행한다")
    void partialFailure_continuesWithOthers() {
        given(orderRepository.findExpiredPendingOrderIds(any(LocalDateTime.class)))
                .willReturn(List.of(1L, 2L, 3L));
        doThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND)).when(orderService).cancel(2L);

        scheduler.cancelExpiredOrders();

        verify(orderService).cancel(1L);
        verify(orderService).cancel(2L);
        verify(orderService).cancel(3L);
    }

    @Test
    @DisplayName("threshold는 현재 시각 기준 expiryMinutes(기본 30분) 이전으로 계산된다")
    void threshold_isCalculatedFromExpiryMinutes() {
        given(orderRepository.findExpiredPendingOrderIds(any(LocalDateTime.class))).willReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusMinutes(30);
        scheduler.cancelExpiredOrders();
        LocalDateTime after = LocalDateTime.now().minusMinutes(30);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findExpiredPendingOrderIds(captor.capture());

        LocalDateTime captured = captor.getValue();
        assertThat(captured).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }
}
