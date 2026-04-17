package com.stockmanagement.domain.refund.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.service.RefundService;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.common.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefundController.class)
@Import(SecurityConfig.class)
@DisplayName("RefundController 단위 테스트")
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RefundService refundService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private static final String REFUND_JSON = "{\"paymentId\":1,\"reason\":\"단순 변심\"}";

    // ===== POST /api/refunds =====

    @Nested
    @DisplayName("POST /api/refunds")
    class RequestRefund {

        @Test
        @DisplayName("인증된 사용자 — 환불 요청 → 201")
        void requestsRefund() throws Exception {
            given(refundService.requestRefund(any(), anyString())).willReturn(mock(RefundResponse.class));

            mockMvc.perform(post("/api/refunds")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REFUND_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/refunds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REFUND_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("이미 환불된 결제 → 409")
        void duplicateRefund() throws Exception {
            given(refundService.requestRefund(any(), anyString()))
                    .willThrow(new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS));

            mockMvc.perform(post("/api/refunds")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REFUND_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/refunds/{id} =====

    @Nested
    @DisplayName("GET /api/refunds/{id}")
    class GetById {

        @Test
        @DisplayName("인증된 사용자 — 환불 조회 → 200")
        void returnsRefund() throws Exception {
            given(refundService.getById(1L, "user1", false)).willReturn(mock(RefundResponse.class));

            mockMvc.perform(get("/api/refunds/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/refunds/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("존재하지 않는 환불 → 404")
        void notFound() throws Exception {
            given(refundService.getById(999L, "user1", false))
                    .willThrow(new BusinessException(ErrorCode.REFUND_NOT_FOUND));

            mockMvc.perform(get("/api/refunds/999").with(authentication(USER_AUTH)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("타인의 환불 조회 → 403")
        void accessDenied() throws Exception {
            given(refundService.getById(1L, "user1", false))
                    .willThrow(new BusinessException(ErrorCode.REFUND_ACCESS_DENIED));

            mockMvc.perform(get("/api/refunds/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/refunds/payments/{paymentId} =====

    @Nested
    @DisplayName("GET /api/refunds/payments/{paymentId}")
    class GetByPaymentId {

        @Test
        @DisplayName("인증된 사용자 — 결제 ID로 환불 조회 → 200")
        void returnsRefundByPaymentId() throws Exception {
            given(refundService.getByPaymentId(1L, "user1", false)).willReturn(mock(RefundResponse.class));

            mockMvc.perform(get("/api/refunds/payments/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/refunds/payments/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
