package com.stockmanagement.domain.order.controller;

import com.stockmanagement.common.annotation.RequireEmailVerified;
import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.ratelimit.RateLimit;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.order.dto.OrderCancelRequest;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderDetailResponse;
import com.stockmanagement.domain.order.dto.OrderItemCancelRequest;
import com.stockmanagement.domain.order.dto.OrderItemCancelResponse;
import com.stockmanagement.domain.order.dto.OrderPreviewRequest;
import com.stockmanagement.domain.order.dto.OrderPreviewResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.order.service.OrderCommandService;
import com.stockmanagement.domain.order.service.OrderDetailService;
import com.stockmanagement.domain.order.service.OrderQueryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 REST API 컨트롤러.
 *
 * <p>Base URL: {@code /api/orders}
 */
@Tag(name = "주문", description = "주문 생성 · 조회 · 취소 · 상태 이력")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;
    private final OrderDetailService orderDetailService;

    @Operation(summary = "주문 금액 미리보기", description = "쿠폰·포인트 적용 시 최종 결제 금액을 계산한다. 주문 생성 없이 순수 계산만 수행.")
    @PostMapping("/preview")
    public ApiResponse<OrderPreviewResponse> preview(
            @RequestBody @Valid OrderPreviewRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.ok(orderQueryService.preview(request, userId));
    }

    @Operation(summary = "주문 생성", description = "재고 예약(reserved++) 후 PENDING 주문 생성. 동일 idempotencyKey 재요청 시 기존 주문 반환.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 10, windowSeconds = 60)
    @RequireEmailVerified
    public ApiResponse<OrderResponse> create(
            @RequestBody @Valid OrderCreateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.ok(orderCommandService.create(request, userId));
    }

    @Operation(summary = "주문 단건 조회", description = "주문 항목(items) 포함. ADMIN은 모든 주문, USER는 본인 주문만 가능.")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(
            @PathVariable Long id,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderQueryService.getByIdForUser(id, userId, isAdmin));
    }

    @Operation(summary = "주문 상세 통합 조회",
               description = "주문 + 결제 + 배송 정보를 단일 응답으로 반환한다. 결제/배송이 없으면 해당 필드는 null.")
    @GetMapping("/{id}/detail")
    public ApiResponse<OrderDetailResponse> getDetail(
            @PathVariable Long id,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderDetailService.getDetail(id, userId, isAdmin));
    }

    @Operation(summary = "주문 목록 조회 (필터 + 페이징)",
               description = "status / startDate / endDate 필터 지원. ADMIN은 userId 파라미터로 특정 사용자 주문 조회 가능. USER는 본인 주문만 조회된다.")
    @GetMapping
    public ApiResponse<Page<OrderResponse>> getList(
            @CurrentUserId(required = false) Long userId,
            Authentication authentication,
            @ModelAttribute OrderSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        Long effectiveUserId = isAdmin ? null : userId;
        return ApiResponse.ok(orderQueryService.getList(effectiveUserId, isAdmin, request, pageable));
    }

    @Operation(summary = "주문 취소", description = "PENDING 상태만 취소 가능. 재고 예약 해제(reserved--). ADMIN은 모든 주문 취소 가능.")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid OrderCancelRequest request,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(orderCommandService.cancel(id, userId, isAdmin, reason));
    }

    @Operation(summary = "주문 아이템 부분 취소",
               description = "CONFIRMED/PARTIAL_CANCELLED 주문에서 지정 아이템을 취소한다. Toss 부분 환불 + 재고 해제 + 포인트 비례 반환.")
    @PostMapping("/{id}/items/cancel")
    public ApiResponse<OrderItemCancelResponse> cancelItems(
            @PathVariable Long id,
            @RequestBody @Valid OrderItemCancelRequest request,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderCommandService.cancelItems(id, userId, isAdmin, request));
    }

    @Operation(summary = "주문 상태 변경 이력 조회", description = "생성·취소·확정·환불 등 모든 상태 전이를 시간순으로 반환. ADMIN은 모든 주문, USER는 본인 주문만 가능.")
    @GetMapping("/{id}/history")
    public ApiResponse<List<OrderStatusHistoryResponse>> getHistory(
            @PathVariable Long id,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderQueryService.getHistory(id, userId, isAdmin));
    }
}
