package com.stockmanagement.domain.shipment.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.security.EmailVerificationTokenStore;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import com.stockmanagement.domain.shipment.service.ShipmentService;
import com.stockmanagement.common.security.JwtTokenProvider;
import com.stockmanagement.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import com.stockmanagement.common.dto.CursorPage;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipmentController.class)
@Import(SecurityConfig.class)
@DisplayName("ShipmentController 단위 테스트")
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ShipmentService shipmentService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;
    @MockBean private UserService userService;
    @MockBean private EmailVerificationTokenStore emailVerificationTokenStore;

    // ===== GET /api/shipments/my =====

    @Nested
    @DisplayName("GET /api/shipments/my")
    class GetMyShipments {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 내 배송 목록 → 200")
        void returnsMyShipments() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);
            given(shipmentService.getMyShipments(anyLong(), any(), anyInt()))
                    .willReturn(CursorPage.of(List.of(mock(ShipmentResponse.class)), 20, ShipmentResponse::getId));

            mockMvc.perform(get("/api/v1/shipments/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/shipments/my"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== GET /api/shipments/orders/{orderId} =====

    @Nested
    @DisplayName("GET /api/shipments/orders/{orderId}")
    class GetByOrderId {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 배송 조회 → 200")
        void returnsShipment() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);
            given(shipmentService.getByOrderId(anyLong(), any(), anyBoolean())).willReturn(mock(ShipmentResponse.class));

            mockMvc.perform(get("/api/v1/shipments/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/shipments/orders/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 주문 → 404")
        void notFound() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);
            given(shipmentService.getByOrderId(anyLong(), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

            mockMvc.perform(get("/api/v1/shipments/orders/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== PATCH /api/shipments/orders/{orderId}/ship =====

    @Nested
    @DisplayName("PATCH /api/shipments/orders/{orderId}/ship")
    class Ship {

        private static final String SHIP_JSON = "{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"123456789\"}";

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 출고 처리 → 200")
        void adminShips() throws Exception {
            given(shipmentService.startShipping(anyLong(), any())).willReturn(mock(ShipmentResponse.class));

            mockMvc.perform(patch("/api/v1/shipments/orders/1/ship")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SHIP_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 출고 처리 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/orders/1/ship")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SHIP_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("필수 필드 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/orders/1/ship")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== PATCH /api/shipments/orders/{orderId}/deliver =====

    @Nested
    @DisplayName("PATCH /api/shipments/orders/{orderId}/deliver")
    class Deliver {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 배송 완료 → 200")
        void adminDelivers() throws Exception {
            given(shipmentService.completeDelivery(1L)).willReturn(mock(ShipmentResponse.class));

            mockMvc.perform(patch("/api/v1/shipments/orders/1/deliver"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 배송 완료 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/orders/1/deliver"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== PATCH /api/shipments/orders/{orderId}/return =====

    @Nested
    @DisplayName("PATCH /api/shipments/orders/{orderId}/return")
    class Return {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 반품 처리 → 200")
        void adminReturns() throws Exception {
            given(shipmentService.processReturn(1L)).willReturn(mock(ShipmentResponse.class));

            mockMvc.perform(patch("/api/v1/shipments/orders/1/return"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 반품 처리 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/orders/1/return"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== POST /api/shipments/orders/{orderId}/return-request =====

    @Nested
    @DisplayName("POST /api/shipments/orders/{orderId}/return-request")
    class ReturnRequest {

        private static final String RETURN_JSON = "{\"reason\":\"단순 변심\"}";

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 반품 신청 → 200")
        void requestsReturn() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);
            ShipmentResponse response = ShipmentResponse.builder()
                    .id(1L).orderId(1L).status(ShipmentStatus.RETURN_REQUESTED)
                    .returnReason("단순 변심").build();
            given(shipmentService.requestReturn(anyLong(), anyLong(), anyString())).willReturn(response);

            mockMvc.perform(post("/api/v1/shipments/orders/1/return-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RETURN_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/shipments/orders/1/return-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RETURN_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("사유 누락 → 400")
        void validationFailure() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);

            mockMvc.perform(post("/api/v1/shipments/orders/1/return-request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== PATCH /api/shipments/orders/{orderId}/return-approve =====

    @Nested
    @DisplayName("PATCH /api/shipments/orders/{orderId}/return-approve")
    class ReturnApprove {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 반품 승인 → 200")
        void adminApproves() throws Exception {
            given(shipmentService.approveReturn(1L)).willReturn(mock(ShipmentResponse.class));

            mockMvc.perform(patch("/api/v1/shipments/orders/1/return-approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 반품 승인 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/orders/1/return-approve"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== PATCH /api/shipments/orders/{orderId}/return-reject =====

    @Nested
    @DisplayName("PATCH /api/shipments/orders/{orderId}/return-reject")
    class ReturnReject {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 반품 거부 → 200")
        void adminRejects() throws Exception {
            given(shipmentService.rejectReturn(1L)).willReturn(mock(ShipmentResponse.class));

            mockMvc.perform(patch("/api/v1/shipments/orders/1/return-reject"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 반품 거부 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/orders/1/return-reject"))
                    .andExpect(status().isForbidden());
        }
    }
}
