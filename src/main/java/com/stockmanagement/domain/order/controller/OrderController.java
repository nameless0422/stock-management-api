package com.stockmanagement.domain.order.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.ratelimit.RateLimit;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.order.dto.OrderCancelRequest;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderDetailResponse;
import com.stockmanagement.domain.order.dto.OrderPreviewRequest;
import com.stockmanagement.domain.order.dto.OrderPreviewResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.service.OrderDetailService;
import com.stockmanagement.domain.order.service.OrderService;
import com.stockmanagement.domain.user.service.UserService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 REST API 컨트롤러.
 *
 * <p>Base URL: {@code /api/orders}
 *
 * <pre>
 * POST /api/orders                  주문 생성 (재고 예약)              → 201 Created
 * GET  /api/orders/{id}             주문 단건 조회                     → 200 OK
 * GET  /api/orders                  주문 목록 조회 (페이징)            → 200 OK
 * POST /api/orders/{id}/cancel      주문 취소 (재고 예약 해제)         → 200 OK
 * GET  /api/orders/{id}/history     주문 상태 변경 이력 조회           → 200 OK
 * </pre>
 *
 * <p>confirm (결제 완료 처리)은 Payment 도메인에서 {@link OrderService}를 직접 호출하므로
 * 외부 API로 노출하지 않는다.
 */
@Tag(name = "주문", description = "주문 생성 · 조회 · 취소 · 상태 이력")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final UserService userService;

    @Operation(summary = "주문 금액 미리보기", description = "쿠폰·포인트 적용 시 최종 결제 금액을 계산한다. 주문 생성 없이 순수 계산만 수행.")
    @PostMapping("/preview")
    public ApiResponse<OrderPreviewResponse> preview(
            @RequestBody @Valid OrderPreviewRequest request,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        return ApiResponse.ok(orderService.preview(request, resolveUserId(authentication, username)));
    }

    @Operation(summary = "주문 생성", description = "재고 예약(reserved++) 후 PENDING 주문 생성. 동일 idempotencyKey 재요청 시 기존 주문 반환.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 10, windowSeconds = 60)
    public ApiResponse<OrderResponse> create(
            @RequestBody @Valid OrderCreateRequest request,
            @AuthenticationPrincipal String username) {
        Long userId = userService.resolveUserId(username);
        return ApiResponse.ok(orderService.create(request, userId));
    }

    @Operation(summary = "주문 단건 조회", description = "주문 항목(items) 포함. ADMIN은 모든 주문, USER는 본인 주문만 가능.")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderService.getByIdForUser(id, resolveUserId(authentication, username), isAdmin));
    }

    @Operation(summary = "주문 상세 통합 조회",
               description = "주문 + 결제 + 배송 정보를 단일 응답으로 반환한다. 결제/배송이 없으면 해당 필드는 null.")
    @GetMapping("/{id}/detail")
    public ApiResponse<OrderDetailResponse> getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderDetailService.getDetail(id, resolveUserId(authentication, username), isAdmin));
    }

    @Operation(summary = "주문 목록 조회 (필터 + 페이징)",
               description = "status / startDate / endDate 필터 지원. ADMIN은 userId 파라미터로 특정 사용자 주문 조회 가능. USER는 본인 주문만 조회된다.")
    @GetMapping
    public ApiResponse<Page<OrderResponse>> getList(
            @AuthenticationPrincipal String username,
            Authentication authentication,
            @ModelAttribute OrderSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        // ADMIN은 request.userId 파라미터로 필터링하므로 userId 해결 불필요
        Long userId = isAdmin ? null : resolveUserId(authentication, username);
        return ApiResponse.ok(orderService.getList(userId, isAdmin, request, pageable));
    }

    @Operation(
        summary = "주문 목록 커서 스크롤 (사용자용)",
        description = """
            커서 기반 페이지네이션으로 주문 목록을 최신순 조회한다.
            - 첫 조회: lastId 없이 호출
            - 다음 페이지: 이전 응답의 nextCursor를 lastId로 전달
            - status 필터 지원 (PENDING / CONFIRMED / CANCELLED / REFUNDED)
            - ADMIN: userId 파라미터로 특정 사용자 주문 스크롤 가능
            - USER: 본인 주문만 조회 (userId 파라미터 무시)
            """
    )
    @GetMapping("/scroll")
    public ApiResponse<CursorPage<OrderResponse>> scroll(
            @AuthenticationPrincipal String username,
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        Long userId = resolveUserId(authentication, username);
        return ApiResponse.ok(orderService.getOrderScroll(userId, isAdmin, status, lastId, size));
    }

    @Operation(summary = "주문 취소", description = "PENDING 상태만 취소 가능. 재고 예약 해제(reserved--). ADMIN은 모든 주문 취소 가능.")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid OrderCancelRequest request,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(orderService.cancel(id, resolveUserId(authentication, username), isAdmin, reason));
    }

    @Operation(summary = "주문 상태 변경 이력 조회", description = "생성·취소·확정·환불 등 모든 상태 전이를 시간순으로 반환. ADMIN은 모든 주문, USER는 본인 주문만 가능.")
    @GetMapping("/{id}/history")
    public ApiResponse<List<OrderStatusHistoryResponse>> getHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(orderService.getHistory(id, resolveUserId(authentication, username), isAdmin));
    }

    /**
     * JWT claim 또는 DB 조회로 userId를 결정한다.
     *
     * <p>JwtAuthenticationFilter가 {@code auth.setDetails(userId)}에 저장한 경우 DB 조회를 건너뛴다.
     * 구 토큰 또는 테스트 환경처럼 details가 없으면 userService.resolveUserId(username)로 DB 조회.
     */
    private Long resolveUserId(Authentication auth, String username) {
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return userService.resolveUserId(username);
    }
}
