package com.stockmanagement.domain.order.cart.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.order.cart.dto.CartCheckoutRequest;
import com.stockmanagement.domain.order.cart.dto.CartItemRequest;
import com.stockmanagement.domain.order.cart.dto.CartResponse;
import com.stockmanagement.domain.order.cart.service.CartService;
import com.stockmanagement.domain.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 장바구니 REST API 컨트롤러.
 *
 * <pre>
 * GET    /api/cart                장바구니 조회                          → 200 OK
 * POST   /api/cart/items          상품 추가 또는 수량 변경               → 200 OK
 * DELETE /api/cart/items/{productId}  특정 상품 제거                   → 204 No Content
 * DELETE /api/cart                장바구니 비우기                        → 204 No Content
 * POST   /api/cart/checkout       장바구니 → 주문 전환                  → 201 Created
 * </pre>
 *
 * <p>인증된 사용자 본인의 장바구니만 접근 가능하다.
 * {@code @AuthenticationPrincipal}로 현재 사용자명을 받아 userId를 조회한다.
 */
@Tag(name = "장바구니", description = "상품 담기 · 수량 변경 · 삭제 · 주문 전환")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final com.stockmanagement.domain.user.repository.UserRepository userRepository;

    @Operation(summary = "장바구니 조회")
    @GetMapping
    public ApiResponse<CartResponse> getCart(@AuthenticationPrincipal String username) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(cartService.getCart(userId));
    }

    @Operation(summary = "상품 추가 또는 수량 변경",
               description = "동일 상품이 이미 있으면 수량을 요청값으로 교체한다.")
    @PostMapping("/items")
    public ApiResponse<CartResponse> addOrUpdate(
            @AuthenticationPrincipal String username,
            @RequestBody @Valid CartItemRequest request) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(cartService.addOrUpdate(userId, request));
    }

    @Operation(summary = "특정 상품 제거")
    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(
            @AuthenticationPrincipal String username,
            @PathVariable Long productId) {
        Long userId = resolveUserId(username);
        cartService.removeItem(userId, productId);
    }

    @Operation(summary = "장바구니 전체 비우기")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal String username) {
        Long userId = resolveUserId(username);
        cartService.clear(userId);
    }

    @Operation(summary = "장바구니 → 주문 전환",
               description = "현재 장바구니의 상품으로 주문을 생성하고 장바구니를 비운다.")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> checkout(
            @AuthenticationPrincipal String username,
            @RequestBody @Valid CartCheckoutRequest request) {
        Long userId = resolveUserId(username);
        return ApiResponse.ok(cartService.checkout(userId, request));
    }

    private Long resolveUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new com.stockmanagement.common.exception.BusinessException(
                        com.stockmanagement.common.exception.ErrorCode.USER_NOT_FOUND))
                .getId();
    }
}
