package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventRelayScheduler 단위 테스트")
class OutboxEventRelaySchedulerTest {

    @Mock OutboxEventRepository repository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Spy  ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Spy  MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks OutboxEventRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "objectMapper", objectMapper);
        scheduler.registerMetrics();
    }

    @Nested
    @DisplayName("relay() — 이벤트 발행")
    class Relay {

        @Test
        @DisplayName("미발행 이벤트 없음 → eventPublisher 미호출")
        void doesNothingWhenNoPending() {
            given(repository.findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(anyInt()))
                    .willReturn(List.of());

            scheduler.relay();

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("ORDER_CREATED → OrderCreatedEvent 발행 + publishedAt 설정")
        void relaysOrderCreated() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("orderId", 1, "userId", 2, "totalAmount", "10000"));
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.ORDER_CREATED)
                    .payload(payload)
                    .build();
            given(repository.findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(anyInt()))
                    .willReturn(List.of(outbox));

            scheduler.relay();

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(OrderCreatedEvent.class);
            OrderCreatedEvent event = (OrderCreatedEvent) captor.getValue();
            assertThat(event.getOrderId()).isEqualTo(1L);
            assertThat(event.getUserId()).isEqualTo(2L);
            assertThat(event.getTotalAmount()).isEqualByComparingTo("10000");
            assertThat(outbox.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("ORDER_CANCELLED → OrderCancelledEvent 발행")
        void relaysOrderCancelled() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("orderId", 1, "userId", 2, "reason", "PENDING_CANCELLED"));
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.ORDER_CANCELLED)
                    .payload(payload)
                    .build();
            given(repository.findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(anyInt()))
                    .willReturn(List.of(outbox));

            scheduler.relay();

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(OrderCancelledEvent.class);
        }

        @Test
        @DisplayName("SHIPMENT_SHIPPED → ShipmentShippedEvent 발행")
        void relaysShipmentShipped() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("orderId", 5, "carrier", "한진택배", "trackingNumber", "111222333"));
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.SHIPMENT_SHIPPED)
                    .payload(payload)
                    .build();
            given(repository.findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(anyInt()))
                    .willReturn(List.of(outbox));

            scheduler.relay();

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            ShipmentShippedEvent event = (ShipmentShippedEvent) captor.getValue();
            assertThat(event.getCarrier()).isEqualTo("한진택배");
            assertThat(event.getTrackingNumber()).isEqualTo("111222333");
        }

        @Test
        @DisplayName("발행 실패 시 retryCount 증가 + failedAt 설정")
        void recordsFailureOnException() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("orderId", 1, "userId", 2, "totalAmount", "5000"));
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.ORDER_CREATED)
                    .payload(payload)
                    .build();
            given(repository.findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(anyInt()))
                    .willReturn(List.of(outbox));
            doThrow(new RuntimeException("publisher 오류"))
                    .when(eventPublisher).publishEvent(any());

            scheduler.relay();

            assertThat(outbox.getPublishedAt()).isNull();
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getFailedAt()).isNotNull();
        }
    }
}
