package com.stockmanagement.domain.product.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.dto.HomeResponse;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.service.HomeService;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import com.stockmanagement.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@DisplayName("HomeController 단위 테스트")
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeService homeService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtBlacklist jwtBlacklist;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /api/home — 인증 없이 200 반환")
    void returnsHomeScreenWithoutAuth() throws Exception {
        ProductResponse product = ProductResponse.builder()
                .id(1L).name("테스트 상품").price(BigDecimal.valueOf(10000))
                .status(ProductStatus.ACTIVE).build();
        CategoryResponse category = CategoryResponse.builder()
                .id(1L).name("전자기기").build();
        HomeResponse response = HomeResponse.builder()
                .newArrivals(List.of(product))
                .popularProducts(List.of(product))
                .featuredCategories(List.of(category))
                .build();

        given(homeService.getHomeScreen()).willReturn(response);

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newArrivals").isArray())
                .andExpect(jsonPath("$.data.newArrivals[0].name").value("테스트 상품"))
                .andExpect(jsonPath("$.data.popularProducts").isArray())
                .andExpect(jsonPath("$.data.featuredCategories").isArray())
                .andExpect(jsonPath("$.data.featuredCategories[0].name").value("전자기기"));
    }

    @Test
    @DisplayName("GET /api/home — 빈 데이터도 정상 반환")
    void returnsEmptyHome() throws Exception {
        HomeResponse response = HomeResponse.builder()
                .newArrivals(List.of())
                .popularProducts(List.of())
                .featuredCategories(List.of())
                .build();

        given(homeService.getHomeScreen()).willReturn(response);

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newArrivals").isEmpty())
                .andExpect(jsonPath("$.data.popularProducts").isEmpty())
                .andExpect(jsonPath("$.data.featuredCategories").isEmpty());
    }
}
