package com.stockmanagement.domain.shipment.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.admin.dto.ShippingPolicyResponse;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배송비 정책 공개 API.
 *
 * <p>프론트엔드에서 주문 전 배송비 정보를 표시하기 위해 비로그인 사용자도 조회 가능.
 */
@Tag(name = "배송", description = "배송비 정책 조회")
@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingPolicyController {

    private final SystemSettingService systemSettingService;

    @Operation(summary = "배송비 정책 조회", description = "기본 배송비, 무료배송 기준 금액을 반환한다.")
    @GetMapping("/policy")
    public ApiResponse<ShippingPolicyResponse> getShippingPolicy() {
        return ApiResponse.ok(systemSettingService.getShippingPolicyDetails());
    }
}
