package com.stockmanagement.common.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventRelayScheduler 단위 테스트")
class OutboxEventRelaySchedulerTest {

    @Mock OutboxEventRepository repository;
    @Mock OutboxEventProcessor processor;
    @Spy  MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks OutboxEventRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 100);
        scheduler.registerMetrics();
    }

    @Nested
    @DisplayName("relay() — processor 위임")
    class Relay {

        @Test
        @DisplayName("미발행 이벤트 없음 → processor 미호출")
        void doesNothingWhenNoPending() {
            given(repository.findPendingEvents(anyInt(), any(), any()))
                    .willReturn(List.of());

            scheduler.relay();

            verifyNoInteractions(processor);
        }

        @Test
        @DisplayName("미발행 이벤트 2건 → 각 이벤트 ID로 processOne 호출")
        void callsProcessorForEachPendingEvent() {
            OutboxEvent e1 = OutboxEvent.builder().eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();
            OutboxEvent e2 = OutboxEvent.builder().eventType(OutboxEventType.ORDER_CANCELLED).payload("{}").build();
            ReflectionTestUtils.setField(e1, "id", 1L);
            ReflectionTestUtils.setField(e2, "id", 2L);

            given(repository.findPendingEvents(anyInt(), any(), any()))
                    .willReturn(List.of(e1, e2));

            scheduler.relay();

            verify(processor).processOne(1L);
            verify(processor).processOne(2L);
        }

        @Test
        @DisplayName("성공 시 relayedCounter 증가")
        void incrementsRelayedCounterOnSuccess() {
            OutboxEvent e1 = OutboxEvent.builder().eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();
            ReflectionTestUtils.setField(e1, "id", 1L);

            given(repository.findPendingEvents(anyInt(), any(), any()))
                    .willReturn(List.of(e1));
            given(processor.processOne(1L)).willReturn(true);

            double before = meterRegistry.counter("outbox.relayed").count();
            scheduler.relay();
            double after = meterRegistry.counter("outbox.relayed").count();

            assertThat(after - before).isEqualTo(1.0);
        }

        @Test
        @DisplayName("실패 시 failedCounter 증가")
        void incrementsFailedCounterOnFailure() {
            OutboxEvent e1 = OutboxEvent.builder().eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();
            ReflectionTestUtils.setField(e1, "id", 1L);

            given(repository.findPendingEvents(anyInt(), any(), any()))
                    .willReturn(List.of(e1));
            given(processor.processOne(1L)).willReturn(false);

            double before = meterRegistry.counter("outbox.relay_failed").count();
            scheduler.relay();
            double after = meterRegistry.counter("outbox.relay_failed").count();

            assertThat(after - before).isEqualTo(1.0);
        }

        @Test
        @DisplayName("성공/실패 혼합 → 각 카운터 정확히 증가")
        void incrementsBothCountersOnMixedResults() {
            OutboxEvent e1 = OutboxEvent.builder().eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();
            OutboxEvent e2 = OutboxEvent.builder().eventType(OutboxEventType.ORDER_CANCELLED).payload("{}").build();
            OutboxEvent e3 = OutboxEvent.builder().eventType(OutboxEventType.SHIPMENT_CREATE).payload("{}").build();
            ReflectionTestUtils.setField(e1, "id", 1L);
            ReflectionTestUtils.setField(e2, "id", 2L);
            ReflectionTestUtils.setField(e3, "id", 3L);

            given(repository.findPendingEvents(anyInt(), any(), any()))
                    .willReturn(List.of(e1, e2, e3));
            given(processor.processOne(1L)).willReturn(true);
            given(processor.processOne(2L)).willReturn(false);
            given(processor.processOne(3L)).willReturn(true);

            double relayedBefore = meterRegistry.counter("outbox.relayed").count();
            double failedBefore = meterRegistry.counter("outbox.relay_failed").count();

            scheduler.relay();

            assertThat(meterRegistry.counter("outbox.relayed").count() - relayedBefore).isEqualTo(2.0);
            assertThat(meterRegistry.counter("outbox.relay_failed").count() - failedBefore).isEqualTo(1.0);
        }
    }
}
