package com.stockmanagement.domain.product.notification.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.domain.product.notification.dto.RestockNotificationResponse;
import com.stockmanagement.domain.product.notification.service.RestockNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Restock Notification", description = "재입고 알림 API")
@RestController
@RequiredArgsConstructor
public class RestockNotificationController {

    private final RestockNotificationService restockNotificationService;

    @Operation(summary = "재입고 알림 신청")
    @PostMapping("/api/products/{productId}/restock-notify")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RestockNotificationResponse> subscribe(
            @PathVariable Long productId, @CurrentUserId Long userId) {
        return ApiResponse.ok(restockNotificationService.subscribe(userId, productId));
    }

    @Operation(summary = "재입고 알림 취소")
    @DeleteMapping("/api/products/{productId}/restock-notify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @PathVariable Long productId, @CurrentUserId Long userId) {
        restockNotificationService.unsubscribe(userId, productId);
    }

    @Operation(summary = "내 재입고 알림 목록 조회")
    @GetMapping("/api/users/me/restock-notifications")
    public ApiResponse<List<RestockNotificationResponse>> getMyNotifications(
            @CurrentUserId Long userId) {
        return ApiResponse.ok(restockNotificationService.getMyNotifications(userId));
    }
}
