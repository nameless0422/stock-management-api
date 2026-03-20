package com.stockmanagement.domain.coupon.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.RefreshTokenStore;
import com.stockmanagement.domain.coupon.dto.CouponResponse;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import com.stockmanagement.security.JwtTokenProvider;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
@Import(SecurityConfig.class)
@DisplayName("CouponController 단위 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CouponService couponService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;
    @MockBean private RefreshTokenStore refreshTokenStore;

    private static final LocalDateTime PAST   = LocalDateTime.now().minusDays(1);
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

    private static final String CREATE_JSON =
            "{\"code\":\"FIXED5000\",\"name\":\"5천원 할인\"," +
            "\"discountType\":\"FIXED_AMOUNT\",\"discountValue\":5000," +
            "\"maxUsagePerUser\":1," +
            "\"validFrom\":\"2020-01-01T00:00:00\",\"validUntil\":\"2099-12-31T23:59:59\"}";

    // ===== POST /api/coupons =====

    @Nested
    @DisplayName("POST /api/coupons")
    class Create {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN 쿠폰 생성 → 201")
        void adminCreates() throws Exception {
            given(couponService.create(any())).willReturn(mock(CouponResponse.class));

            mockMvc.perform(post("/api/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER 쿠폰 생성 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(post("/api/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== GET /api/coupons =====

    @Nested
    @DisplayName("GET /api/coupons")
    class GetList {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN 목록 조회 → 200")
        void adminGetsList() throws Exception {
            given(couponService.getList(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(mock(CouponResponse.class))));

            mockMvc.perform(get("/api/coupons"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== PATCH /api/coupons/{id}/deactivate =====

    @Nested
    @DisplayName("PATCH /api/coupons/{id}/deactivate")
    class Deactivate {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN 비활성화 → 200")
        void adminDeactivates() throws Exception {
            given(couponService.deactivate(1L)).willReturn(mock(CouponResponse.class));

            mockMvc.perform(patch("/api/coupons/1/deactivate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER 비활성화 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/coupons/1/deactivate"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== POST /api/coupons/validate =====

    @Nested
    @DisplayName("POST /api/coupons/validate")
    class Validate {

        private static final String VALIDATE_JSON =
                "{\"couponCode\":\"FIXED5000\",\"orderAmount\":30000}";

        @Test
        @DisplayName("인증 사용자 유효성 확인 → 200 + 할인 금액 포함")
        void userValidates() throws Exception {
            User mockUser = mock(User.class);
            given(mockUser.getId()).willReturn(1L);
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(mockUser));

            CouponValidateResponse response = CouponValidateResponse.builder()
                    .couponId(1L)
                    .couponCode("FIXED5000")
                    .couponName("5천원 할인")
                    .orderAmount(BigDecimal.valueOf(30000))
                    .discountAmount(BigDecimal.valueOf(5000))
                    .finalAmount(BigDecimal.valueOf(25000))
                    .build();
            given(couponService.validate(anyLong(), any())).willReturn(response);

            // @AuthenticationPrincipal String 타입 — String principal로 authentication 직접 주입
            var auth = new UsernamePasswordAuthenticationToken("testuser", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

            mockMvc.perform(post("/api/coupons/validate")
                            .with(authentication(auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALIDATE_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.discountAmount").value(5000))
                    .andExpect(jsonPath("$.data.finalAmount").value(25000));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/coupons/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALIDATE_JSON))
                    .andExpect(status().isForbidden());
        }
    }
}
