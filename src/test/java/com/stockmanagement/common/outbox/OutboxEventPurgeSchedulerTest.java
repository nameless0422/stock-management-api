package com.stockmanagement.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventPurgeScheduler 단위 테스트")
class OutboxEventPurgeSchedulerTest {

    @Mock OutboxEventPurgeProcessor purgeProcessor;

    @InjectMocks OutboxEventPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
    }

    @Nested
    @DisplayName("purge() — 배치 루프 삭제")
    class Purge {

        @Test
        @DisplayName("삭제 대상 없음 → purgeProcessor 1회 호출 후 루프 종료")
        void stopsLoopWhenNothingDeleted() {
            given(purgeProcessor.deleteBatch(any(), anyInt())).willReturn(0);

            scheduler.purge();

            verify(purgeProcessor, times(1)).deleteBatch(any(LocalDateTime.class), anyInt());
        }

        @Test
        @DisplayName("배치 미만 삭제 → 1회 호출 후 종료")
        void stopsAfterPartialBatch() {
            given(purgeProcessor.deleteBatch(any(), anyInt())).willReturn(5);

            scheduler.purge();

            verify(purgeProcessor, times(1)).deleteBatch(any(LocalDateTime.class), anyInt());
        }

        @Test
        @DisplayName("배치 크기(1000)만큼 삭제되면 다음 배치 호출")
        void continuesLoopWhenFullBatch() {
            given(purgeProcessor.deleteBatch(any(), anyInt()))
                    .willReturn(1000)
                    .willReturn(0);

            scheduler.purge();

            verify(purgeProcessor, times(2)).deleteBatch(any(LocalDateTime.class), anyInt());
        }

        @Test
        @DisplayName("기준 시각은 현재 시각에서 retentionDays 일 전이어야 한다")
        void thresholdIsRetentionDaysAgo() {
            given(purgeProcessor.deleteBatch(any(), anyInt())).willReturn(0);

            LocalDateTime before = LocalDateTime.now();
            scheduler.purge();
            LocalDateTime after = LocalDateTime.now();

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(purgeProcessor).deleteBatch(captor.capture(), anyInt());

            LocalDateTime threshold = captor.getValue();
            assertThat(threshold).isBefore(before.minusDays(6));
            assertThat(threshold).isAfter(after.minusDays(8));
        }
    }
}
