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
    }
}
