package com.stockmanagement.domain.shipment.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import com.stockmanagement.domain.shipment.dto.ShipmentUpdateRequest;
import com.stockmanagement.domain.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 배송 REST API 컨트롤러.
 *
 * <pre>
 * GET   /api/shipments/orders/{orderId}          주문별 배송 조회              → 200 OK (인증)
 * PATCH /api/shipments/orders/{orderId}/ship      배송 출고 처리               → 200 OK (ADMIN)
 * PATCH /api/shipments/orders/{orderId}/deliver   배송 완료 처리               → 200 OK (ADMIN)
 * PATCH /api/shipments/orders/{orderId}/return    반품 처리                    → 200 OK (ADMIN)
 * </pre>
 *
 * <p>배송 생성은 결제 확정({@code PaymentService.confirm()}) 시 자동으로 수행되므로
 * 외부 API로 노출하지 않는다.
 */
@Tag(name = "배송", description = "배송 조회 · 출고 · 완료 · 반품 (상태 전이)")
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @Operation(summary = "주문별 배송 조회", description = "본인 주문의 배송 정보만 조회 가능. ADMIN은 전체 조회 가능.")
    @GetMapping("/orders/{orderId}")
    public ApiResponse<ShipmentResponse> getByOrderId(
            @PathVariable Long orderId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(shipmentService.getByOrderId(orderId, username, isAdmin));
    }

    @Operation(summary = "배송 출고 처리 (ADMIN)",
               description = "PREPARING → SHIPPED. 택배사와 운송장 번호를 등록한다.")
    @PatchMapping("/orders/{orderId}/ship")
    public ApiResponse<ShipmentResponse> ship(
            @PathVariable Long orderId,
            @RequestBody @Valid ShipmentUpdateRequest request) {
        return ApiResponse.ok(shipmentService.startShipping(orderId, request));
    }

    @Operation(summary = "배송 완료 처리 (ADMIN)", description = "SHIPPED → DELIVERED.")
    @PatchMapping("/orders/{orderId}/deliver")
    public ApiResponse<ShipmentResponse> deliver(@PathVariable Long orderId) {
        return ApiResponse.ok(shipmentService.completeDelivery(orderId));
    }

    @Operation(summary = "반품 처리 (ADMIN)", description = "PREPARING|SHIPPED → RETURNED.")
    @PatchMapping("/orders/{orderId}/return")
    public ApiResponse<ShipmentResponse> processReturn(@PathVariable Long orderId) {
        return ApiResponse.ok(shipmentService.processReturn(orderId));
    }
}
