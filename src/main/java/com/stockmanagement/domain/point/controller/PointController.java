package com.stockmanagement.domain.point.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.point.dto.PointBalanceResponse;
import com.stockmanagement.domain.point.dto.PointTransactionResponse;
import com.stockmanagement.domain.point.service.PointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Point", description = "포인트/적립금 API")
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @Operation(summary = "포인트 잔액 조회")
    @GetMapping("/balance")
    public ApiResponse<PointBalanceResponse> getBalance(
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(pointService.getBalance(username));
    }

    @Operation(summary = "포인트 변동 이력 조회 (최신순)")
    @GetMapping("/history")
    public ApiResponse<Page<PointTransactionResponse>> getHistory(
            @AuthenticationPrincipal String username,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(pointService.getHistory(username, pageable));
    }
}
