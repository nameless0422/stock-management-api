package com.stockmanagement.domain.user.address.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressRequest;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressResponse;
import com.stockmanagement.domain.user.address.service.DeliveryAddressService;
import com.stockmanagement.domain.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 배송지 REST API 컨트롤러.
 *
 * <pre>
 * POST   /api/delivery-addresses              배송지 등록            → 201 Created
 * GET    /api/delivery-addresses              내 배송지 목록         → 200 OK
 * GET    /api/delivery-addresses/{id}         배송지 단건 조회       → 200 OK
 * PUT    /api/delivery-addresses/{id}         배송지 수정            → 200 OK
 * DELETE /api/delivery-addresses/{id}         배송지 삭제            → 204 No Content
 * POST   /api/delivery-addresses/{id}/default 기본 배송지 설정       → 200 OK
 * </pre>
 */
@Tag(name = "배송지", description = "배송지 등록 · 조회 · 수정 · 삭제 · 기본 설정")
@RestController
@RequestMapping("/api/delivery-addresses")
@RequiredArgsConstructor
public class DeliveryAddressController {

    private final DeliveryAddressService deliveryAddressService;
    private final UserRepository userRepository;

    @Operation(summary = "배송지 등록", description = "첫 번째 배송지는 자동으로 기본 배송지로 설정된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeliveryAddressResponse> create(
            @AuthenticationPrincipal String username,
            @RequestBody @Valid DeliveryAddressRequest request) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(deliveryAddressService.create(userId, request));
    }

    @Operation(summary = "내 배송지 목록 조회", description = "기본 배송지가 맨 앞에 온다.")
    @GetMapping
    public ApiResponse<List<DeliveryAddressResponse>> getList(
            @AuthenticationPrincipal String username) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(deliveryAddressService.getList(userId));
    }

    @Operation(summary = "배송지 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<DeliveryAddressResponse> getById(
            @AuthenticationPrincipal String username,
            @PathVariable Long id) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(deliveryAddressService.getById(id, userId));
    }

    @Operation(summary = "배송지 수정")
    @PutMapping("/{id}")
    public ApiResponse<DeliveryAddressResponse> update(
            @AuthenticationPrincipal String username,
            @PathVariable Long id,
            @RequestBody @Valid DeliveryAddressRequest request) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(deliveryAddressService.update(id, userId, request));
    }

    @Operation(summary = "배송지 삭제", description = "기본 배송지 삭제 시 다른 배송지가 자동으로 기본으로 승격된다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal String username,
            @PathVariable Long id) {
        Long userId = resolveUserId(username);
        deliveryAddressService.delete(id, userId);
    }

    @Operation(summary = "기본 배송지 설정", description = "기존 기본 배송지를 해제하고 지정된 배송지를 기본으로 변경한다.")
    @PostMapping("/{id}/default")
    public ApiResponse<DeliveryAddressResponse> setDefault(
            @AuthenticationPrincipal String username,
            @PathVariable Long id) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(deliveryAddressService.setDefault(id, userId));
    }

    private Long resolveUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getId();
    }
}
