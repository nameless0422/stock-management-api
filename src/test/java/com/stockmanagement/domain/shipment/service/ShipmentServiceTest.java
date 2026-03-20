package com.stockmanagement.domain.shipment.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import com.stockmanagement.domain.shipment.dto.ShipmentUpdateRequest;
import com.stockmanagement.domain.shipment.entity.Shipment;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShipmentService 단위 테스트")
class ShipmentServiceTest {

    @Mock ShipmentRepository shipmentRepository;
    @Mock OrderRepository orderRepository;

    @InjectMocks ShipmentService shipmentService;

    private Order confirmedOrder;
    private Shipment preparingShipment;
    private Shipment shippedShipment;

    @BeforeEach
    void setUp() {
        confirmedOrder = Order.builder()
                .userId(1L)
                .totalAmount(BigDecimal.valueOf(5000))
                .idempotencyKey("key-001")
                .build();
        // status = PENDING → confirm() 호출로 CONFIRMED 전환
        confirmedOrder.confirm();

        preparingShipment = Shipment.builder().orderId(1L).build();
        // 테스트용 SHIPPED 배송 — ReflectionTestUtils로 status 세팅
        shippedShipment = Shipment.builder().orderId(2L).build();
        shippedShipment.ship("CJ대한통운", "123456789");
    }

    @Nested
    @DisplayName("배송 생성 (createForOrder)")
    class CreateForOrder {

        @Test
        @DisplayName("CONFIRMED 주문 → PREPARING 배송 생성")
        void createsShipmentForConfirmedOrder() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(confirmedOrder));
            given(shipmentRepository.existsByOrderId(1L)).willReturn(false);
            given(shipmentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ShipmentResponse response = shipmentService.createForOrder(1L);

            assertThat(response.getStatus()).isEqualTo(ShipmentStatus.PREPARING);
            verify(shipmentRepository).save(any(Shipment.class));
        }

        @Test
        @DisplayName("이미 배송이 존재하는 주문 → SHIPMENT_ALREADY_EXISTS 예외")
        void throwsWhenShipmentAlreadyExists() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(confirmedOrder));
            given(shipmentRepository.existsByOrderId(1L)).willReturn(true);

            assertThatThrownBy(() -> shipmentService.createForOrder(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.SHIPMENT_ALREADY_EXISTS.getMessage());
        }

        @Test
        @DisplayName("PENDING 주문 → INVALID_ORDER_STATUS 예외")
        void throwsWhenOrderNotConfirmed() {
            Order pendingOrder = Order.builder()
                    .userId(1L).totalAmount(BigDecimal.valueOf(1000)).idempotencyKey("k").build();
            given(orderRepository.findById(2L)).willReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> shipmentService.createForOrder(2L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_ORDER_STATUS.getMessage());
        }
    }

    @Nested
    @DisplayName("배송 출고 (startShipping)")
    class StartShipping {

        @Test
        @DisplayName("PREPARING → SHIPPED, 택배사·운송장 저장")
        void shipsSuccessfully() {
            given(shipmentRepository.findByOrderId(1L)).willReturn(Optional.of(preparingShipment));
            ShipmentUpdateRequest request = mock(ShipmentUpdateRequest.class);
            given(request.getCarrier()).willReturn("한진택배");
            given(request.getTrackingNumber()).willReturn("987654321");

            ShipmentResponse response = shipmentService.startShipping(1L, request);

            assertThat(response.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
            assertThat(response.getCarrier()).isEqualTo("한진택배");
            assertThat(response.getTrackingNumber()).isEqualTo("987654321");
        }

        @Test
        @DisplayName("배송 없음 → SHIPMENT_NOT_FOUND 예외")
        void throwsWhenNotFound() {
            given(shipmentRepository.findByOrderId(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.startShipping(99L, mock(ShipmentUpdateRequest.class)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.SHIPMENT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("배송 완료 (completeDelivery)")
    class CompleteDelivery {

        @Test
        @DisplayName("SHIPPED → DELIVERED")
        void deliversSuccessfully() {
            given(shipmentRepository.findByOrderId(2L)).willReturn(Optional.of(shippedShipment));

            ShipmentResponse response = shipmentService.completeDelivery(2L);

            assertThat(response.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(response.getDeliveredAt()).isNotNull();
        }

        @Test
        @DisplayName("PREPARING 상태에서 완료 처리 → INVALID_SHIPMENT_STATUS 예외")
        void throwsWhenNotShipped() {
            given(shipmentRepository.findByOrderId(1L)).willReturn(Optional.of(preparingShipment));

            assertThatThrownBy(() -> shipmentService.completeDelivery(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_SHIPMENT_STATUS.getMessage());
        }
    }

    @Nested
    @DisplayName("반품 처리 (processReturn)")
    class ProcessReturn {

        @Test
        @DisplayName("PREPARING 상태에서 반품 처리 성공")
        void returnsFromPreparing() {
            given(shipmentRepository.findByOrderId(1L)).willReturn(Optional.of(preparingShipment));

            ShipmentResponse response = shipmentService.processReturn(1L);

            assertThat(response.getStatus()).isEqualTo(ShipmentStatus.RETURNED);
        }

        @Test
        @DisplayName("SHIPPED 상태에서 반품 처리 성공")
        void returnsFromShipped() {
            given(shipmentRepository.findByOrderId(2L)).willReturn(Optional.of(shippedShipment));

            ShipmentResponse response = shipmentService.processReturn(2L);

            assertThat(response.getStatus()).isEqualTo(ShipmentStatus.RETURNED);
        }

        @Test
        @DisplayName("DELIVERED 상태에서 반품 → INVALID_SHIPMENT_STATUS 예외")
        void throwsWhenDelivered() {
            shippedShipment.deliver();
            given(shipmentRepository.findByOrderId(2L)).willReturn(Optional.of(shippedShipment));

            assertThatThrownBy(() -> shipmentService.processReturn(2L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_SHIPMENT_STATUS.getMessage());
        }
    }
}
