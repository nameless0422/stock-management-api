package com.stockmanagement.domain.order.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
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
import org.springframework.web.bind.annotation.*;

/**
 * 주문 REST API 컨트롤러.
 *
 * <p>Base URL: {@code /api/orders}
 *
 * <pre>
 * POST /api/orders              주문 생성 (재고 예약)       → 201 Created
 * GET  /api/orders/{id}         주문 단건 조회              → 200 OK
 * GET  /api/orders              주문 목록 조회 (페이징)     → 200 OK
 * POST /api/orders/{id}/cancel  주문 취소 (재고 예약 해제)  → 200 OK
 * </pre>
 *
 * <p>confirm (결제 완료 처리)은 Payment 도메인에서 {@link OrderService}를 직접 호출하므로
 * 외부 API로 노출하지 않는다.
 */
@Tag(name = "주문", description = "주문 생성 · 조회 · 취소")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 생성", description = "재고 예약(reserved++) 후 PENDING 주문 생성. 동일 idempotencyKey 재요청 시 기존 주문 반환.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@RequestBody @Valid OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @Operation(summary = "주문 단건 조회", description = "주문 항목(items) 포함.")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
    }

    @Operation(summary = "주문 목록 조회 (페이징)", description = "기본: 최신순, 20건.")
    @GetMapping
    public ApiResponse<Page<OrderResponse>> getList(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(orderService.getList(pageable));
    }

    @Operation(summary = "주문 취소", description = "PENDING 상태만 취소 가능. 재고 예약 해제(reserved--).")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }
}
