package com.stockmanagement.domain.notification.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.domain.notification.dto.NotificationResponse;
import com.stockmanagement.domain.notification.dto.UnreadCountResponse;
import com.stockmanagement.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 인앱 알림 REST API.
 *
 * <pre>
 * GET  /api/notifications              내 알림 목록 (페이징)
 * POST /api/notifications/{id}/read    읽음 처리
 * POST /api/notifications/read-all     전체 읽음 처리
 * GET  /api/notifications/unread-count 미읽음 개수
 * </pre>
 */
@Tag(name = "알림", description = "인앱 알림 — 목록 조회 · 읽음 처리")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록", description = "?read=true|false 로 필터. 미지정 시 전체. 기본: 최신순, 20건.")
    @GetMapping
    public ApiResponse<Page<NotificationResponse>> getNotifications(
            @RequestParam(required = false) Boolean read,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @CurrentUserId Long userId) {
        return ApiResponse.ok(notificationService.getNotifications(userId, read, pageable));
    }

    @Operation(summary = "알림 읽음 처리", description = "단건 알림을 읽음 처리한다.")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id, @CurrentUserId Long userId) {
        notificationService.markAsRead(userId, id);
    }

    @Operation(summary = "전체 읽음 처리", description = "내 미읽음 알림을 전체 읽음 처리한다.")
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@CurrentUserId Long userId) {
        notificationService.markAllAsRead(userId);
    }

    @Operation(summary = "미읽음 알림 개수", description = "미읽음 알림 개수를 반환한다.")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@CurrentUserId Long userId) {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.getUnreadCount(userId)));
    }
}
