package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventProcessor 단위 테스트")
class OutboxEventProcessorTest {

    @Mock OutboxEventRepository repository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Spy  ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks OutboxEventProcessor processor;

    @Nested
    @DisplayName("processOne() — 이벤트 발행")
    class ProcessOne {

        @Test
        @DisplayName("ORDER_CREATED → OrderCreatedEvent 발행 + publishedAt 설정")
        void relaysOrderCreated() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("orderId", 1, "userId", 2, "totalAmount", "10000"));
            OutboxEvent outbox = outboxEvent(1L, OutboxEventType.ORDER_CREATED, payload);

            given(repository.findById(1L)).willReturn(Optional.of(outbox));

            processor.processOne(1L);

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
                    Map.of("orderId", 1, "userId", 2, "reason", "PENDING_CANCELLED"));
            OutboxEvent outbox = outboxEvent(2L, OutboxEventType.ORDER_CANCELLED, payload);

            given(repository.findById(2L)).willReturn(Optional.of(outbox));

            processor.processOne(2L);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(OrderCancelledEvent.class);
        }

        @Test
        @DisplayName("SHIPMENT_SHIPPED → ShipmentShippedEvent 발행")
        void relaysShipmentShipped() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("orderId", 5, "carrier", "한진택배", "trackingNumber", "111222333"));
            OutboxEvent outbox = outboxEvent(3L, OutboxEventType.SHIPMENT_SHIPPED, payload);

            given(repository.findById(3L)).willReturn(Optional.of(outbox));

            processor.processOne(3L);

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
                    Map.of("orderId", 1, "userId", 2, "totalAmount", "5000"));
            OutboxEvent outbox = outboxEvent(4L, OutboxEventType.ORDER_CREATED, payload);

            given(repository.findById(4L)).willReturn(Optional.of(outbox));
            doThrow(new RuntimeException("publisher 오류")).when(eventPublisher).publishEvent(any());

            processor.processOne(4L);

            assertThat(outbox.getPublishedAt()).isNull();
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getFailedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 발행된 이벤트 → eventPublisher 미호출 (멱등성)")
        void skipsAlreadyPublishedEvent() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("orderId", 1, "userId", 2, "totalAmount", "5000"));
            OutboxEvent outbox = outboxEvent(5L, OutboxEventType.ORDER_CREATED, payload);
            outbox.markPublished(); // 이미 발행됨

            given(repository.findById(5L)).willReturn(Optional.of(outbox));

            processor.processOne(5L);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("ID 조회 결과 없음 → eventPublisher 미호출 (다른 인스턴스가 먼저 처리)")
        void skipsWhenEventNotFound() {
            given(repository.findById(99L)).willReturn(Optional.empty());

            processor.processOne(99L);

            verifyNoInteractions(eventPublisher);
        }
    }

    /** 테스트용 OutboxEvent 생성 헬퍼. */
    private OutboxEvent outboxEvent(Long id, OutboxEventType type, String payload) {
        OutboxEvent outbox = OutboxEvent.builder().eventType(type).payload(payload).build();
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
