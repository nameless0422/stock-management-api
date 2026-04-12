package com.stockmanagement.domain.inventory.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.domain.inventory.dto.InventoryAdjustRequest;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.dto.InventorySearchRequest;
import com.stockmanagement.domain.inventory.dto.InventoryTransactionResponse;
import com.stockmanagement.domain.inventory.service.InventoryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;


/**
 * 재고 REST API 컨트롤러.
 *
 * <p>Base URL: {@code /api/inventory}
 *
 * <pre>
 * GET  /api/inventory                            재고 목록 검색   → 200 OK (Page)
 * GET  /api/inventory/{productId}                재고 현황 조회   → 200 OK
 * GET  /api/inventory/{productId}/transactions   재고 이력 조회   → 200 OK
 * POST /api/inventory/{productId}/receive        입고 처리        → 200 OK
 * POST /api/inventory/{productId}/adjust         수동 조정        → 200 OK
 * </pre>
 *
 * <p>reserve / releaseReservation / confirmAllocation 은 Order·Payment 도메인에서
 * {@link InventoryService}를 직접 호출하므로 외부 API로 노출하지 않는다.
 */
@Tag(name = "재고", description = "재고 현황 조회 · 이력 조회 · 입고 처리")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(
        summary = "재고 목록 검색",
        description = """
            조건 필터링 + 페이지네이션으로 여러 재고를 조회한다. 모든 파라미터는 선택적.
            - status: IN_STOCK(available≥10) / LOW_STOCK(0<available<10) / OUT_OF_STOCK(available≤0)
            - minAvailable / maxAvailable: 가용 수량 범위 (포함)
            - productName: 상품명 부분 검색 (대소문자 무시)
            - category: 카테고리 부분 검색 (대소문자 무시)
            - page / size / sort: 페이지네이션 (기본 page=0, size=20)
            """
    )
    @GetMapping
    public ApiResponse<Page<InventoryResponse>> search(
            @ModelAttribute InventorySearchRequest request,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(inventoryService.search(request, pageable));
    }

    @Operation(summary = "재고 현황 조회", description = "onHand / reserved / allocated / available 반환.")
    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> getByProductId(@PathVariable Long productId) {
        return ApiResponse.ok(inventoryService.getByProductId(productId));
    }

    @Operation(
        summary = "재고 변동 이력 조회",
        description = """
            커서 기반 페이지네이션. 최신순(ID 내림차순) 정렬.
            - 첫 조회: lastId 없이 호출
            - 다음 페이지: 이전 응답의 nextCursor를 lastId로 전달
            - hasNext=false이면 마지막 페이지
            """
    )
    @GetMapping("/{productId}/transactions")
    public ApiResponse<CursorPage<InventoryTransactionResponse>> getTransactions(
            @PathVariable Long productId,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryService.getTransactions(productId, lastId, size));
    }

    @Operation(summary = "입고 처리", description = "ADMIN 전용. onHand 증가 → available 증가.")
    @PostMapping("/{productId}/receive")
    public ApiResponse<InventoryResponse> receive(
            @PathVariable Long productId,
            @RequestBody @Valid InventoryReceiveRequest request) {
        return ApiResponse.ok(inventoryService.receive(productId, request));
    }

    @Operation(summary = "재고 수동 조정", description = "ADMIN 전용. quantity 양수=증가, 음수=감소.")
    @PostMapping("/{productId}/adjust")
    public ApiResponse<InventoryResponse> adjust(
            @PathVariable Long productId,
            @RequestBody @Valid InventoryAdjustRequest request) {
        return ApiResponse.ok(inventoryService.adjust(productId, request));
    }
}
