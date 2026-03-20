package com.stockmanagement.integration;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.shipment.entity.Shipment;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 배송 통합 테스트.
 *
 * <p>결제 확정이 Toss API 없이는 불가하므로, 배송 레코드는
 * {@link ShipmentRepository}를 직접 사용해 저장한 후 API를 테스트한다.
 */
@DisplayName("Shipment 통합 테스트")
class ShipmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private OrderRepository orderRepository;

    // ===== 공통 헬퍼 =====

    /** CONFIRMED 상태의 Order를 DB에 직접 저장하고 ID를 반환한다. */
    private long createConfirmedOrder(long userId) {
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(BigDecimal.valueOf(10000))
                .idempotencyKey("order-key-" + System.nanoTime())
                .build();
        order.confirm();
        return orderRepository.save(order).getId();
    }

    /** PREPARING 상태의 Shipment를 DB에 직접 저장하고 orderId를 반환한다. */
    private long createPreparingShipment(long orderId) {
        Shipment shipment = Shipment.builder().orderId(orderId).build();
        shipmentRepository.save(shipment);
        return orderId;
    }

    // ===== 조회 =====

    @Test
    @DisplayName("주문별 배송 조회 → PREPARING 상태 반환")
    void getByOrderId_returnsPreparing() throws Exception {
        String userToken = signupAndLogin("user1", "password1", "u1@test.com");
        long userId = userRepository.findByUsername("user1").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createPreparingShipment(orderId);

        mockMvc.perform(get("/api/shipments/orders/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PREPARING"));
    }

    @Test
    @DisplayName("존재하지 않는 배송 조회 → 404")
    void getByOrderId_notFound() throws Exception {
        String userToken = signupAndLogin("user1", "password1", "u1@test.com");

        mockMvc.perform(get("/api/shipments/orders/9999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    // ===== 출고 처리 =====

    @Test
    @DisplayName("ADMIN 출고 처리 → SHIPPED + 운송장 번호 저장")
    void ship_adminSuccess() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long userId = userRepository.findByUsername("admin").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createPreparingShipment(orderId);

        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/ship")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"123456789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.trackingNumber").value("123456789"))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty());
    }

    @Test
    @DisplayName("USER 권한으로 출고 처리 → 403")
    void ship_userRole_forbidden() throws Exception {
        String userToken = signupAndLogin("user2", "password1", "u2@test.com");
        long userId = userRepository.findByUsername("user2").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createPreparingShipment(orderId);

        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/ship")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"한진택배\",\"trackingNumber\":\"987654321\"}"))
                .andExpect(status().isForbidden());
    }

    // ===== 배송 완료 =====

    @Test
    @DisplayName("출고 후 배송 완료 → DELIVERED + deliveredAt 설정")
    void deliver_afterShipping() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long userId = userRepository.findByUsername("admin").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createPreparingShipment(orderId);

        // 출고 처리
        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/ship")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"로젠택배\",\"trackingNumber\":\"111222333\"}"))
                .andExpect(status().isOk());

        // 완료 처리
        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/deliver")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());
    }

    // ===== 반품 처리 =====

    @Test
    @DisplayName("PREPARING 상태에서 반품 처리 → RETURNED")
    void processReturn_fromPreparing() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long userId = userRepository.findByUsername("admin").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createPreparingShipment(orderId);

        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/return")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));
    }
}
