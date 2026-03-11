package com.stockmanagement.domain.inventory.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.inventory.dto.InventoryAdjustRequest;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.dto.InventoryTransactionResponse;
import com.stockmanagement.domain.inventory.service.InventoryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 재고 REST API 컨트롤러.
 *
 * <p>Base URL: {@code /api/inventory}
 *
 * <pre>
 * GET  /api/inventory/{productId}                재고 현황 조회  → 200 OK
 * GET  /api/inventory/{productId}/transactions   재고 이력 조회  → 200 OK
 * POST /api/inventory/{productId}/receive        입고 처리       → 200 OK
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

    @Operation(summary = "재고 현황 조회", description = "onHand / reserved / allocated / available 반환.")
    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> getByProductId(@PathVariable Long productId) {
        return ApiResponse.ok(inventoryService.getByProductId(productId));
    }

    @Operation(summary = "재고 변동 이력 조회", description = "최신순 정렬.")
    @GetMapping("/{productId}/transactions")
    public ApiResponse<List<InventoryTransactionResponse>> getTransactions(@PathVariable Long productId) {
        return ApiResponse.ok(inventoryService.getTransactions(productId));
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
