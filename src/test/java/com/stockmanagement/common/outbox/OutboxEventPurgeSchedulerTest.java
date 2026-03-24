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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventPurgeScheduler 단위 테스트")
class OutboxEventPurgeSchedulerTest {

    @Mock OutboxEventRepository repository;

    @InjectMocks OutboxEventPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
    }

    @Nested
    @DisplayName("purge() — 오래된 레코드 삭제")
    class Purge {

        @Test
        @DisplayName("삭제 대상 없음 → repository 호출만 수행, 로그 미출력")
        void doesNothingWhenNothingDeleted() {
            given(repository.deleteByPublishedAtBefore(any())).willReturn(0);

            scheduler.purge();

            verify(repository).deleteByPublishedAtBefore(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("삭제 발생 시 → deleteByPublishedAtBefore 호출")
        void callsDeleteWhenRecordsExist() {
            given(repository.deleteByPublishedAtBefore(any())).willReturn(5);

            scheduler.purge();

            verify(repository).deleteByPublishedAtBefore(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("기준 시각은 현재 시각에서 retentionDays 일 전이어야 한다")
        void thresholdIsRetentionDaysAgo() {
            given(repository.deleteByPublishedAtBefore(any())).willReturn(0);

            LocalDateTime before = LocalDateTime.now();
            scheduler.purge();
            LocalDateTime after = LocalDateTime.now();

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repository).deleteByPublishedAtBefore(captor.capture());

            LocalDateTime threshold = captor.getValue();
            assertThat(threshold).isBefore(before.minusDays(6));
            assertThat(threshold).isAfter(after.minusDays(8));
        }

        @Test
        @DisplayName("retentionDays=1 이면 기준 시각이 약 1일 전")
        void respectsRetentionDaysOverride() {
            ReflectionTestUtils.setField(scheduler, "retentionDays", 1);
            given(repository.deleteByPublishedAtBefore(any())).willReturn(0);

            LocalDateTime before = LocalDateTime.now();
            scheduler.purge();

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repository).deleteByPublishedAtBefore(captor.capture());

            LocalDateTime threshold = captor.getValue();
            assertThat(threshold).isBefore(before);
            assertThat(threshold).isAfter(before.minusDays(2));
        }
    }
}
