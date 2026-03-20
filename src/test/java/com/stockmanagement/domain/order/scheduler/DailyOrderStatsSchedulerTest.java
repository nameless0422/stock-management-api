package com.stockmanagement.domain.order.scheduler;

import com.stockmanagement.domain.order.entity.DailyOrderStats;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.DailyOrderStatsRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyOrderStatsScheduler 단위 테스트")
class DailyOrderStatsSchedulerTest {

    @Mock OrderRepository orderRepository;
    @Mock DailyOrderStatsRepository statsRepository;
    @InjectMocks DailyOrderStatsScheduler scheduler;

    @Test
    @DisplayName("전일 집계 후 레코드 미존재 시 신규 저장")
    void savesNewStatsWhenNotExists() {
        stubOrderCounts(10, 6, 2, BigDecimal.valueOf(50000));
        given(statsRepository.findByStatDate(any(LocalDate.class))).willReturn(Optional.empty());
        given(statsRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        scheduler.aggregateDailyStats();

        ArgumentCaptor<DailyOrderStats> captor = ArgumentCaptor.forClass(DailyOrderStats.class);
        verify(statsRepository).save(captor.capture());
        DailyOrderStats saved = captor.getValue();
        assertThat(saved.getTotalOrders()).isEqualTo(10);
        assertThat(saved.getConfirmedOrders()).isEqualTo(6);
        assertThat(saved.getCancelledOrders()).isEqualTo(2);
        assertThat(saved.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("전일 집계 후 레코드 이미 존재 시 update")
    void updatesExistingStats() {
        stubOrderCounts(5, 3, 1, BigDecimal.valueOf(30000));
        DailyOrderStats existing = buildStats(LocalDate.now().minusDays(1), 0, 0, 0, BigDecimal.ZERO);
        given(statsRepository.findByStatDate(any(LocalDate.class))).willReturn(Optional.of(existing));

        scheduler.aggregateDailyStats();

        verify(statsRepository, never()).save(any());
        assertThat(existing.getTotalOrders()).isEqualTo(5);
        assertThat(existing.getConfirmedOrders()).isEqualTo(3);
        assertThat(existing.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    // ===== 헬퍼 =====

    private void stubOrderCounts(long total, long confirmed, long cancelled, BigDecimal revenue) {
        given(orderRepository.countByCreatedAtBetween(any(), any())).willReturn(total);
        given(orderRepository.countByStatusAndCreatedAtBetween(eq(OrderStatus.CONFIRMED), any(), any()))
                .willReturn(confirmed);
        given(orderRepository.countByStatusAndCreatedAtBetween(eq(OrderStatus.CANCELLED), any(), any()))
                .willReturn(cancelled);
        given(orderRepository.sumRevenueByCreatedAtBetween(any(), any())).willReturn(revenue);
    }

    private DailyOrderStats buildStats(LocalDate date, int total, int confirmed, int cancelled,
                                       BigDecimal revenue) {
        DailyOrderStats stats = DailyOrderStats.builder()
                .statDate(date).totalOrders(total).confirmedOrders(confirmed)
                .cancelledOrders(cancelled).totalRevenue(revenue).build();
        ReflectionTestUtils.setField(stats, "id", 1L);
        return stats;
    }
}
