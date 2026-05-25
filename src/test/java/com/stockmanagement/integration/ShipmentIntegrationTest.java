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
        String userToken = signupAndLogin("user1", "Password1!", "u1@test.com");
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
        String userToken = signupAndLogin("user1", "Password1!", "u1@test.com");

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
        String userToken = signupAndLogin("user2", "Password1!", "u2@test.com");
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

    // ===== 사용자 반품 신청 플로우 =====

    /** DELIVERED 상태의 Shipment를 DB에 직접 준비한다. */
    private long createDeliveredShipment(long orderId) {
        Shipment shipment = Shipment.builder().orderId(orderId).build();
        shipment.ship("CJ대한통운", "999888777", null);
        shipment.deliver();
        shipmentRepository.save(shipment);
        return orderId;
    }

    @Test
    @DisplayName("사용자 반품 신청 → RETURN_REQUESTED")
    void requestReturn_success() throws Exception {
        String userToken = signupAndLogin("buyer", "Password1!", "buyer@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createDeliveredShipment(orderId);

        mockMvc.perform(post("/api/shipments/orders/" + orderId + "/return-request")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"))
                .andExpect(jsonPath("$.data.returnReason").value("단순 변심"))
                .andExpect(jsonPath("$.data.returnRequestedAt").isNotEmpty());
    }

    @Test
    @DisplayName("타인 주문 반품 신청 → 404")
    void requestReturn_notOwner() throws Exception {
        String ownerToken = signupAndLogin("owner", "Password1!", "owner@test.com");
        long ownerId = userRepository.findByUsername("owner").orElseThrow().getId();
        long orderId = createConfirmedOrder(ownerId);
        createDeliveredShipment(orderId);

        String otherToken = signupAndLogin("other", "Password1!", "other@test.com");

        mockMvc.perform(post("/api/shipments/orders/" + orderId + "/return-request")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"도용 시도\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADMIN 반품 승인 → RETURNED")
    void approveReturn_success() throws Exception {
        String userToken = signupAndLogin("buyer2", "Password1!", "buyer2@test.com");
        long userId = userRepository.findByUsername("buyer2").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createDeliveredShipment(orderId);

        // 사용자 반품 신청
        mockMvc.perform(post("/api/shipments/orders/" + orderId + "/return-request")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사이즈 불일치\"}"))
                .andExpect(status().isOk());

        // ADMIN 승인
        String adminToken = createAdminAndLogin("admin2", "adminpass1", "admin2@test.com");
        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/return-approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));
    }

    @Test
    @DisplayName("ADMIN 반품 거부 → DELIVERED 복원")
    void rejectReturn_success() throws Exception {
        String userToken = signupAndLogin("buyer3", "Password1!", "buyer3@test.com");
        long userId = userRepository.findByUsername("buyer3").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createDeliveredShipment(orderId);

        // 사용자 반품 신청
        mockMvc.perform(post("/api/shipments/orders/" + orderId + "/return-request")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"마음이 바뀜\"}"))
                .andExpect(status().isOk());

        // ADMIN 거부
        String adminToken = createAdminAndLogin("admin3", "adminpass1", "admin3@test.com");
        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/return-reject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.returnReason").isEmpty());
    }

    @Test
    @DisplayName("USER 권한으로 반품 승인 → 403")
    void approveReturn_userForbidden() throws Exception {
        String userToken = signupAndLogin("buyer4", "Password1!", "buyer4@test.com");
        long userId = userRepository.findByUsername("buyer4").orElseThrow().getId();
        long orderId = createConfirmedOrder(userId);
        createDeliveredShipment(orderId);

        mockMvc.perform(patch("/api/shipments/orders/" + orderId + "/return-approve")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
