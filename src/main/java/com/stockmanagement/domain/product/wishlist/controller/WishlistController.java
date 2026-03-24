package com.stockmanagement.domain.product.wishlist.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Wishlist", description = "위시리스트 API")
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "위시리스트에 상품 추가")
    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WishlistResponse> add(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(wishlistService.add(productId, username));
    }

    @Operation(summary = "위시리스트에서 상품 제거")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username) {
        wishlistService.remove(productId, username);
    }

    @Operation(summary = "내 위시리스트 목록 조회")
    @GetMapping
    public ApiResponse<List<WishlistResponse>> getList(
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(wishlistService.getList(username));
    }
}
