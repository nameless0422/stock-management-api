package com.stockmanagement.domain.order.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.service.OrderService;
import jakarta.validation.Valid;
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
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문을 생성한다.
     *
     * <p>멱등성 키({@code idempotencyKey})가 동일한 재요청의 경우
     * 기존 주문을 그대로 반환한다 (새 주문 미생성).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@RequestBody @Valid OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    /** 주문 단건을 조회한다 (주문 항목 포함). */
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
    }

    /**
     * 주문 목록을 페이징 조회한다.
     * 기본값: 최신 순 정렬, 페이지당 20건.
     */
    @GetMapping
    public ApiResponse<Page<OrderResponse>> getList(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(orderService.getList(pageable));
    }

    /** 주문을 취소한다. PENDING 상태인 주문만 취소 가능하다. */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id));
    }
}
