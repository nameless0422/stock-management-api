package com.stockmanagement.domain.order.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.order.service.OrderDetailService;
import com.stockmanagement.domain.order.service.OrderService;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@DisplayName("OrderController 단위 테스트")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderDetailService orderDetailService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtBlacklist jwtBlacklist;

    @BeforeEach
    void setUp() {
        given(userService.resolveUserId(any())).willReturn(1L);
    }

    // ===== POST /api/orders =====

    @Nested
    @DisplayName("POST /api/orders")
    class Create {

        private static final String VALID_JSON =
                "{\"userId\":1,\"idempotencyKey\":\"key-001\"," +
                "\"items\":[{\"productId\":1,\"quantity\":2,\"unitPrice\":5000}]}";

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 주문 생성 성공 → 201")
        void createsOrder() throws Exception {
            given(orderService.create(any(), anyLong())).willReturn(mock(OrderResponse.class));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("필수 필드(idempotencyKey, items) 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("재고 부족 → 409")
        void insufficientStock() throws Exception {
            given(orderService.create(any(), anyLong()))
                    .willThrow(new BusinessException(ErrorCode.INSUFFICIENT_STOCK));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/orders/{id} =====

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetById {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 주문 단건 조회 → 200")
        void returnsOrder() throws Exception {
            given(orderService.getByIdForUser(anyLong(), any(), anyBoolean())).willReturn(mock(OrderResponse.class));

            mockMvc.perform(get("/api/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/orders/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 주문 → 404")
        void returnsNotFound() throws Exception {
            given(orderService.getByIdForUser(eq(999L), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

            mockMvc.perform(get("/api/orders/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @WithMockUser
        @DisplayName("다른 사용자의 주문 조회 → 403")
        void accessDeniedForOtherUserOrder() throws Exception {
            given(orderService.getByIdForUser(eq(1L), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.ORDER_ACCESS_DENIED));

            mockMvc.perform(get("/api/orders/1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/orders =====

    @Nested
    @DisplayName("GET /api/orders")
    class GetList {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 주문 목록 페이징 조회 → 200")
        void returnsList() throws Exception {
            given(orderService.getList(any(), anyBoolean(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== POST /api/orders/{id}/cancel =====

    @Nested
    @DisplayName("POST /api/orders/{id}/cancel")
    class Cancel {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 주문 취소 성공 → 200")
        void cancelsOrder() throws Exception {
            given(orderService.cancel(anyLong(), any(), anyBoolean(), any())).willReturn(mock(OrderResponse.class));

            mockMvc.perform(post("/api/orders/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/orders/1/cancel"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("취소 불가 상태의 주문 → 409")
        void invalidStatus() throws Exception {
            given(orderService.cancel(anyLong(), any(), anyBoolean(), any()))
                    .willThrow(new BusinessException(ErrorCode.INVALID_ORDER_STATUS));

            mockMvc.perform(post("/api/orders/1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/orders/{id}/history =====

    @Nested
    @DisplayName("GET /api/orders/{id}/history")
    class GetHistory {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 주문 상태 이력 조회 → 200")
        void returnsHistory() throws Exception {
            given(orderService.getHistory(anyLong(), any(), anyBoolean()))
                    .willReturn(List.of(mock(OrderStatusHistoryResponse.class)));

            mockMvc.perform(get("/api/orders/1/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/orders/1/history"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 주문 → 404")
        void returnsNotFound() throws Exception {
            given(orderService.getHistory(eq(999L), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

            mockMvc.perform(get("/api/orders/999/history"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("다른 사용자의 주문 이력 조회 → 403")
        void accessDeniedForOtherUserHistory() throws Exception {
            given(orderService.getHistory(eq(1L), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.ORDER_ACCESS_DENIED));

            mockMvc.perform(get("/api/orders/1/history"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
