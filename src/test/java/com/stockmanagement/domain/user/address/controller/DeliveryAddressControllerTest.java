package com.stockmanagement.domain.user.address.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressResponse;
import com.stockmanagement.domain.user.address.service.DeliveryAddressService;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeliveryAddressController.class)
@Import(SecurityConfig.class)
@DisplayName("DeliveryAddressController 단위 테스트")
class DeliveryAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private DeliveryAddressService deliveryAddressService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private static final String VALID_JSON =
            "{\"alias\":\"집\",\"recipient\":\"홍길동\",\"phone\":\"01012345678\"," +
            "\"zipCode\":\"12345\",\"address1\":\"서울시 강남구\",\"address2\":\"101호\"}";

    @BeforeEach
    void setUp() {
        given(userService.resolveUserId("user1")).willReturn(1L);
    }

    // ===== POST /api/delivery-addresses =====

    @Nested
    @DisplayName("POST /api/delivery-addresses")
    class Create {

        @Test
        @DisplayName("인증된 사용자 — 배송지 등록 → 201")
        void createsAddress() throws Exception {
            given(deliveryAddressService.create(anyLong(), any())).willReturn(mock(DeliveryAddressResponse.class));

            mockMvc.perform(post("/api/delivery-addresses")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/delivery-addresses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("필수 필드 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/delivery-addresses")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== GET /api/delivery-addresses =====

    @Nested
    @DisplayName("GET /api/delivery-addresses")
    class GetList {

        @Test
        @DisplayName("인증된 사용자 — 배송지 목록 조회 → 200")
        void returnsList() throws Exception {
            given(deliveryAddressService.getList(1L))
                    .willReturn(List.of(mock(DeliveryAddressResponse.class)));

            mockMvc.perform(get("/api/delivery-addresses").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/delivery-addresses"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== DELETE /api/delivery-addresses/{id} =====

    @Nested
    @DisplayName("DELETE /api/delivery-addresses/{id}")
    class Delete {

        @Test
        @DisplayName("인증된 사용자 — 배송지 삭제 → 204")
        void deletesAddress() throws Exception {
            mockMvc.perform(delete("/api/delivery-addresses/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/delivery-addresses/1"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== POST /api/delivery-addresses/{id}/default =====

    @Nested
    @DisplayName("POST /api/delivery-addresses/{id}/default")
    class SetDefault {

        @Test
        @DisplayName("인증된 사용자 — 기본 배송지 설정 → 200")
        void setsDefault() throws Exception {
            given(deliveryAddressService.setDefault(anyLong(), anyLong()))
                    .willReturn(mock(DeliveryAddressResponse.class));

            mockMvc.perform(post("/api/delivery-addresses/1/default").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
