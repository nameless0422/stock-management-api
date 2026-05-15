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

            boolean result = processor.processOne(99L);

            assertThat(result).isTrue();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("성공 시 true 반환")
        void returnsTrueOnSuccess() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("orderId", 1, "userId", 2, "totalAmount", "5000"));
            OutboxEvent outbox = outboxEvent(10L, OutboxEventType.ORDER_CREATED, payload);
            given(repository.findById(10L)).willReturn(Optional.of(outbox));

            boolean result = processor.processOne(10L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("실패 시 false 반환")
        void returnsFalseOnFailure() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("orderId", 1, "userId", 2, "totalAmount", "5000"));
            OutboxEvent outbox = outboxEvent(11L, OutboxEventType.ORDER_CREATED, payload);
            given(repository.findById(11L)).willReturn(Optional.of(outbox));
            doThrow(new RuntimeException("발행 실패")).when(eventPublisher).publishEvent(any());

            boolean result = processor.processOne(11L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("processOne() — 서비스 직접 호출")
    class ServiceDirectCall {

        @Test
        @DisplayName("SHIPMENT_CREATE → shipmentService.createForOrder() 호출")
        void callsShipmentService() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("orderId", 42));
            OutboxEvent outbox = outboxEvent(20L, OutboxEventType.SHIPMENT_CREATE, payload);
            given(repository.findById(20L)).willReturn(Optional.of(outbox));

            boolean result = processor.processOne(20L);

            assertThat(result).isTrue();
            verify(shipmentService).createForOrder(42L);
            assertThat(outbox.getPublishedAt()).isNotNull();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("SHIPMENT_CREATE + SHIPMENT_ALREADY_EXISTS → 멱등성 처리 (성공)")
        void treatsShipmentAlreadyExistsAsSuccess() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("orderId", 42));
            OutboxEvent outbox = outboxEvent(21L, OutboxEventType.SHIPMENT_CREATE, payload);
            given(repository.findById(21L)).willReturn(Optional.of(outbox));
            doThrow(new BusinessException(ErrorCode.SHIPMENT_ALREADY_EXISTS))
                    .when(shipmentService).createForOrder(42L);

            boolean result = processor.processOne(21L);

            assertThat(result).isTrue();
            assertThat(outbox.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("SHIPMENT_CREATE + 기타 예외 → 실패 처리")
        void failsOnOtherShipmentException() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("orderId", 42));
            OutboxEvent outbox = outboxEvent(22L, OutboxEventType.SHIPMENT_CREATE, payload);
            given(repository.findById(22L)).willReturn(Optional.of(outbox));
            doThrow(new RuntimeException("DB 오류")).when(shipmentService).createForOrder(42L);

            boolean result = processor.processOne(22L);

            assertThat(result).isFalse();
            assertThat(outbox.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("POINT_EARN → pointService.earn() 호출")
        void callsPointService() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", 10, "paidAmount", 50000, "orderId", 7));
            OutboxEvent outbox = outboxEvent(30L, OutboxEventType.POINT_EARN, payload);
            given(repository.findById(30L)).willReturn(Optional.of(outbox));

            boolean result = processor.processOne(30L);

            assertThat(result).isTrue();
            verify(pointService).earn(10L, 50000L, 7L);
            assertThat(outbox.getPublishedAt()).isNotNull();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("POINT_EARN 실패 → retryCount 증가")
        void failsOnPointServiceException() throws Exception {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", 10, "paidAmount", 50000, "orderId", 7));
            OutboxEvent outbox = outboxEvent(31L, OutboxEventType.POINT_EARN, payload);
            given(repository.findById(31L)).willReturn(Optional.of(outbox));
            doThrow(new RuntimeException("포인트 적립 실패")).when(pointService).earn(10L, 50000L, 7L);

            boolean result = processor.processOne(31L);

            assertThat(result).isFalse();
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getFailedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordFailure() — 지수 백오프")
    class ExponentialBackoff {

        @Test
        @DisplayName("첫 실패: nextRetryAt = 30초 후")
        void firstRetryBackoff30Seconds() {
            OutboxEvent outbox = outboxEvent(40L, OutboxEventType.ORDER_CREATED, "{}");

            outbox.recordFailure();

            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getNextRetryAt()).isAfter(outbox.getFailedAt().plusSeconds(29));
            assertThat(outbox.getNextRetryAt()).isBefore(outbox.getFailedAt().plusSeconds(31));
        }

        @Test
        @DisplayName("두 번째 실패: nextRetryAt = 60초 후")
        void secondRetryBackoff60Seconds() {
            OutboxEvent outbox = outboxEvent(41L, OutboxEventType.ORDER_CREATED, "{}");

            outbox.recordFailure(); // 1차: 30초
            outbox.recordFailure(); // 2차: 60초

            assertThat(outbox.getRetryCount()).isEqualTo(2);
            assertThat(outbox.getNextRetryAt()).isAfter(outbox.getFailedAt().plusSeconds(59));
            assertThat(outbox.getNextRetryAt()).isBefore(outbox.getFailedAt().plusSeconds(61));
        }

        @Test
        @DisplayName("백오프 상한: 3600초 초과 불가")
        void backoffCapsAt3600Seconds() {
            OutboxEvent outbox = outboxEvent(42L, OutboxEventType.ORDER_CREATED, "{}");

            // 8회 실패: 30 * 2^7 = 3840 → cap at 3600
            for (int i = 0; i < 8; i++) {
                outbox.recordFailure();
            }

            assertThat(outbox.getRetryCount()).isEqualTo(8);
            assertThat(outbox.getNextRetryAt()).isBefore(outbox.getFailedAt().plusSeconds(3601));
        }
    }

    /** 테스트용 OutboxEvent 생성 헬퍼. */
    private OutboxEvent outboxEvent(Long id, OutboxEventType type, String payload) {
        OutboxEvent outbox = OutboxEvent.builder().eventType(type).payload(payload).build();
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
