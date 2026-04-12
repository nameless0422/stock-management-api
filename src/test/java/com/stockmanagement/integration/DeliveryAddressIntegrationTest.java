package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("DeliveryAddress 통합 테스트")
class DeliveryAddressIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_REQUEST =
            "{\"alias\":\"집\",\"recipient\":\"홍길동\",\"phone\":\"01012345678\"," +
            "\"zipCode\":\"06000\",\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":\"101호\"}";

    // ===== 등록 =====

    @Test
    @DisplayName("배송지 등록 → 201, 첫 번째 배송지는 자동 기본")
    void create_firstAddressIsDefault() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");

        mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.alias").value("집"))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    @DisplayName("두 번째 배송지 등록 → isDefault = false")
    void create_secondAddressNotDefault() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");

        mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated());

        String officeReq = VALID_REQUEST.replace("\"집\"", "\"회사\"");
        mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(false));
    }

    @Test
    @DisplayName("필수 필드 누락 → 400")
    void create_missingRequired_400() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");

        mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"집\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== 목록 조회 =====

    @Test
    @DisplayName("배송지 목록 조회 → 기본 배송지 맨 앞")
    void getList_defaultFirst() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");

        // 첫 번째: 자동으로 기본
        mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated());

        // 두 번째: 기본 아님
        String officeReq = VALID_REQUEST.replace("\"집\"", "\"회사\"");
        mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(officeReq))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    // ===== 수정 =====

    @Test
    @DisplayName("배송지 수정 → 변경된 정보 반환")
    void update_success() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");
        long id = createAddress(token, "집");

        String updateReq = VALID_REQUEST.replace("\"집\"", "\"부모님댁\"");
        mockMvc.perform(put("/api/delivery-addresses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("부모님댁"));
    }

    // ===== 기본 배송지 변경 =====

    @Test
    @DisplayName("기본 배송지 변경 → 기존 기본 해제, 신규 기본 설정")
    void setDefault_switchesDefault() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");

        long id1 = createAddress(token, "집");
        long id2 = createAddress(token, "회사");

        mockMvc.perform(post("/api/delivery-addresses/" + id2 + "/default")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // 기존 기본 배송지(집) 상태 확인
        mockMvc.perform(get("/api/delivery-addresses/" + id1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false));
    }

    // ===== 삭제 =====

    @Test
    @DisplayName("배송지 삭제 → 204, 이후 조회 404")
    void delete_success() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");
        long id = createAddress(token, "집");

        mockMvc.perform(delete("/api/delivery-addresses/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/delivery-addresses/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기본 배송지 삭제 → 다른 배송지가 자동 기본 승격")
    void delete_defaultPromotesAnother() throws Exception {
        String token = signupAndLogin("user1", "password1", "u1@test.com");
        long id1 = createAddress(token, "집");   // 기본 배송지
        long id2 = createAddress(token, "회사"); // 일반 배송지

        mockMvc.perform(delete("/api/delivery-addresses/" + id1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/delivery-addresses/" + id2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    // ===== 주문 연동 =====

    @Test
    @DisplayName("배송지 포함 주문 생성 → OrderResponse.deliveryAddressId 반영")
    void orderWithAddress() throws Exception {
        String adminToken = createAdminAndLogin("admin", "pass1", "a@test.com");
        // 상품 등록 + 입고
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"상품\",\"sku\":\"SKU-A1\",\"price\":5000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(body).path("data").path("id").asLong();
        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isOk());

        String userToken = signupAndLogin("buyer", "password1", "buyer@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();
        long addressId = createAddress(userToken, "집");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"ik-001\"," +
                                "\"deliveryAddressId\":%d," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":5000}]}",
                                userId, addressId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryAddressId").value(addressId));
    }

    // ===== 헬퍼 =====

    /** 배송지를 등록하고 생성된 ID를 반환한다. */
    private long createAddress(String token, String alias) throws Exception {
        String reqBody = VALID_REQUEST.replace("\"집\"", "\"" + alias + "\"");
        String resp = mockMvc.perform(post("/api/delivery-addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }
}
