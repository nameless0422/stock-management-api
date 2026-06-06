package com.stockmanagement.domain.point.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.domain.point.dto.PointBalanceResponse;
import com.stockmanagement.domain.point.dto.PointTransactionResponse;
import com.stockmanagement.domain.point.service.PointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Point", description = "포인트/적립금 API")
@Validated
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @Operation(summary = "포인트 잔액 조회")
    @GetMapping("/balance")
    public ApiResponse<PointBalanceResponse> getBalance(
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(pointService.getBalance(username));
    }

    @Operation(summary = "포인트 변동 이력 조회 (커서 기반, 최신순)")
    @GetMapping("/history")
    public ApiResponse<CursorPage<PointTransactionResponse>> getHistory(
            @AuthenticationPrincipal String username,
            @RequestParam(required = false) Long lastId,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(pointService.getHistory(username, lastId, size));
    }

    @Operation(summary = "적립 예정 포인트 조회 (커서 기반, 배송 완료 전)")
    @GetMapping("/pending")
    public ApiResponse<CursorPage<PointTransactionResponse>> getPending(
            @AuthenticationPrincipal String username,
            @RequestParam(required = false) Long lastId,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(pointService.getPendingHistory(username, lastId, size));
    }

    @Operation(summary = "만료 예정 포인트 조회")
    @GetMapping("/expiring-soon")
    public ApiResponse<Page<PointTransactionResponse>> getExpiringSoon(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "30") int withinDays,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(pointService.getExpiringSoon(username, withinDays, pageable));
    }
}
