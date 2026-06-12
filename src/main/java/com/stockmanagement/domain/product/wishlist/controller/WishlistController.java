package com.stockmanagement.domain.product.wishlist.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wishlist", description = "위시리스트 API")
@Validated
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "위시리스트에 상품 추가")
    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WishlistResponse> add(
            @PathVariable Long productId, @CurrentUserId Long userId) {
        return ApiResponse.ok(wishlistService.add(productId, userId));
    }

    @Operation(summary = "위시리스트에서 상품 제거")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long productId, @CurrentUserId Long userId) {
        wishlistService.remove(productId, userId);
    }

    @Operation(summary = "특정 상품의 위시리스트 추가 여부 조회",
               description = "상품 상세 페이지의 하트 아이콘 상태에 사용. { \"wishlisted\": true/false } 반환.")
    @GetMapping("/{productId}")
    public ApiResponse<java.util.Map<String, Boolean>> isWishlisted(
            @PathVariable Long productId, @CurrentUserId Long userId) {
        boolean wishlisted = wishlistService.isWishlisted(productId, userId);
        return ApiResponse.ok(java.util.Map.of("wishlisted", wishlisted));
    }

    @Operation(summary = "내 위시리스트 목록 조회 (커서 기반)")
    @GetMapping
    public ApiResponse<CursorPage<WishlistResponse>> getList(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long lastId,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(wishlistService.getList(userId, lastId, size));
    }
}
