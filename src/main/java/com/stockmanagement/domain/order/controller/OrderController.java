package com.stockmanagement.domain.order.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.ratelimit.RateLimit;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.order.service.OrderService;
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

    @Operation(summary = "주문 생성", description = "재고 예약(reserved++) 후 PENDING 주문 생성. 동일 idempotencyKey 재요청 시 기존 주문 반환.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 10, windowSeconds = 60)
    public ApiResponse<OrderResponse> create(@RequestBody @Valid OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @Operation(summary = "주문 단건 조회", description = "주문 항목(items) 포함.")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
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
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ApiResponse.ok(orderService.getList(username, isAdmin, request, pageable));
    }

    @Operation(summary = "주문 취소", description = "PENDING 상태만 취소 가능. 재고 예약 해제(reserved--).")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }

    @Operation(summary = "주문 상태 변경 이력 조회", description = "생성·취소·확정·환불 등 모든 상태 전이를 시간순으로 반환.")
    @GetMapping("/{id}/history")
    public ApiResponse<List<OrderStatusHistoryResponse>> getHistory(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getHistory(id));
    }
}
