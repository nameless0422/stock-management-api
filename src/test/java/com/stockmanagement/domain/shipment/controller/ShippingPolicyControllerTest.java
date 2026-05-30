package com.stockmanagement.domain.shipment.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import com.stockmanagement.domain.admin.dto.ShippingPolicyResponse;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShippingPolicyController.class)
@Import(SecurityConfig.class)
@DisplayName("ShippingPolicyController 단위 테스트")
class ShippingPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private SystemSettingService systemSettingService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;
    @MockBean private UserService userService;

    @Test
    @DisplayName("비로그인 — 배송비 정책 조회 → 200")
    void publicAccessReturnsPolicy() throws Exception {
        given(systemSettingService.getShippingPolicyDetails())
                .willReturn(new ShippingPolicyResponse(
                        new BigDecimal("3000"), new BigDecimal("50000"), "SYSTEM", LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/shipping/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defaultFee").value(3000))
                .andExpect(jsonPath("$.data.freeShippingThreshold").value(50000));
    }
}
