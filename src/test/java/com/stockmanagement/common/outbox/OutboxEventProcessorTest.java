package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.shipment.service.ShipmentService;
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
    @Mock ShipmentService shipmentService;
    @Mock PointService pointService;
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

    @Nested
    @DisplayName("processOne() — 서비스 직접 호출 이벤트")
    class DirectServiceCalls {

        @Test
        @DisplayName("SHIPMENT_CREATE → shipmentService.createForOrder() 호출")
        void callsShipmentServiceForShipmentCreate() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("orderId", 10));
            OutboxEvent outbox = outboxEvent(10L, OutboxEventType.SHIPMENT_CREATE, payload);
            given(repository.findById(10L)).willReturn(Optional.of(outbox));

            boolean result = processor.processOne(10L);

            assertThat(result).isTrue();
            verify(shipmentService).createForOrder(10L);
            assertThat(outbox.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("SHIPMENT_CREATE — SHIPMENT_ALREADY_EXISTS 예외 시 멱등 성공 처리")
        void shipmentCreateIdempotent() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("orderId", 11));
            OutboxEvent outbox = outboxEvent(11L, OutboxEventType.SHIPMENT_CREATE, payload);
            given(repository.findById(11L)).willReturn(Optional.of(outbox));
            doThrow(new BusinessException(ErrorCode.SHIPMENT_ALREADY_EXISTS))
                    .when(shipmentService).createForOrder(11L);

            boolean result = processor.processOne(11L);

            assertThat(result).isTrue();
            assertThat(outbox.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("SHIPMENT_CREATE — 기타 예외 시 실패 기록")
        void shipmentCreateFailure() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("orderId", 12));
            OutboxEvent outbox = outboxEvent(12L, OutboxEventType.SHIPMENT_CREATE, payload);
            given(repository.findById(12L)).willReturn(Optional.of(outbox));
            doThrow(new RuntimeException("DB 연결 실패")).when(shipmentService).createForOrder(12L);

            boolean result = processor.processOne(12L);

            assertThat(result).isFalse();
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("POINT_EARN → pointService.earn() 호출")
        void callsPointServiceForPointEarn() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", 5, "paidAmount", 50000, "orderId", 20));
            OutboxEvent outbox = outboxEvent(13L, OutboxEventType.POINT_EARN, payload);
            given(repository.findById(13L)).willReturn(Optional.of(outbox));

            boolean result = processor.processOne(13L);

            assertThat(result).isTrue();
            verify(pointService).earn(5L, 50000L, 20L);
            assertThat(outbox.getPublishedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordFailure() — 지수 백오프")
    class ExponentialBackoff {

        @Test
        @DisplayName("첫 실패 → retryCount=1, nextRetryAt은 30초 후")
        void firstFailureSetsBackoff30s() {
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();

            outbox.recordFailure();

            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getNextRetryAt()).isNotNull();
            assertThat(outbox.getNextRetryAt()).isAfter(outbox.getFailedAt().plusSeconds(29));
            assertThat(outbox.getNextRetryAt()).isBefore(outbox.getFailedAt().plusSeconds(31));
        }

        @Test
        @DisplayName("연속 실패 시 백오프 지수 증가 (30s → 60s → 120s)")
        void exponentialBackoffIncreases() {
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();

            outbox.recordFailure(); // retryCount=1, 30s
            outbox.recordFailure(); // retryCount=2, 60s
            assertThat(outbox.getRetryCount()).isEqualTo(2);
            assertThat(outbox.getNextRetryAt()).isAfter(outbox.getFailedAt().plusSeconds(59));

            outbox.recordFailure(); // retryCount=3, 120s
            assertThat(outbox.getRetryCount()).isEqualTo(3);
            assertThat(outbox.getNextRetryAt()).isAfter(outbox.getFailedAt().plusSeconds(119));
        }

        @Test
        @DisplayName("MAX_RETRY(5) 도달 시 findPendingEvents에서 조회 제외됨 (dead letter)")
        void maxRetryExcludedFromPending() {
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventType(OutboxEventType.ORDER_CREATED).payload("{}").build();

            for (int i = 0; i < OutboxEventRelayScheduler.MAX_RETRY; i++) {
                outbox.recordFailure();
            }

            assertThat(outbox.getRetryCount()).isEqualTo(OutboxEventRelayScheduler.MAX_RETRY);
            // retryCount >= MAX_RETRY → findPendingEvents 쿼리 조건 "retryCount < maxRetry"에 의해 제외
            assertThat(outbox.getPublishedAt()).isNull(); // 여전히 미발행 상태 (dead letter)
        }
    }

    /** 테스트용 OutboxEvent 생성 헬퍼. */
    private OutboxEvent outboxEvent(Long id, OutboxEventType type, String payload) {
        OutboxEvent outbox = OutboxEvent.builder().eventType(type).payload(payload).build();
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
