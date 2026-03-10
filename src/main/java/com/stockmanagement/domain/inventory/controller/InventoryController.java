package com.stockmanagement.domain.inventory.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.dto.InventoryTransactionResponse;
import com.stockmanagement.domain.inventory.service.InventoryService;
import jakarta.validation.Valid;
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
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** 상품의 재고 현황 조회 (onHand / reserved / allocated / available) */
    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> getByProductId(@PathVariable Long productId) {
        return ApiResponse.ok(inventoryService.getByProductId(productId));
    }

    /** 상품의 재고 변동 이력 조회 (최신순) */
    @GetMapping("/{productId}/transactions")
    public ApiResponse<List<InventoryTransactionResponse>> getTransactions(@PathVariable Long productId) {
        return ApiResponse.ok(inventoryService.getTransactions(productId));
    }

    /**
     * 입고 처리 — 창고에 실물 재고가 들어올 때 호출한다.
     * onHand가 증가하고, 이에 따라 available도 늘어난다.
     */
    @PostMapping("/{productId}/receive")
    public ApiResponse<InventoryResponse> receive(
            @PathVariable Long productId,
            @RequestBody @Valid InventoryReceiveRequest request) {
        return ApiResponse.ok(inventoryService.receive(productId, request));
    }
}
