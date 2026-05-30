package com.stockmanagement.domain.product.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.dto.HomeResponse;
import com.stockmanagement.domain.product.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 화면 REST API 컨트롤러.
 *
 * <pre>
 * GET /api/home   홈 화면 집계 데이터 (신상품·인기상품·카테고리) → 200 OK
 * </pre>
 */
@Tag(name = "홈", description = "홈 화면 전용 집계 API")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 화면 데이터", description = "신상품, 인기 상품, 추천 카테고리를 단일 응답으로 반환한다. Redis 캐시(TTL 5분).")
    @GetMapping
    public ApiResponse<HomeResponse> getHome() {
        return ApiResponse.ok(homeService.getHomeScreen());
    }
}
