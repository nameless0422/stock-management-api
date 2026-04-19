package com.stockmanagement.domain.product.wishlist.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.service.WishlistService;
import com.stockmanagement.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wishlist", description = "위시리스트 API")
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserService userService;

    @Operation(summary = "위시리스트에 상품 추가")
    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WishlistResponse> add(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        return ApiResponse.ok(wishlistService.add(productId, resolveUserId(authentication, username)));
    }

    @Operation(summary = "위시리스트에서 상품 제거")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        wishlistService.remove(productId, resolveUserId(authentication, username));
    }

    @Operation(summary = "특정 상품의 위시리스트 추가 여부 조회",
               description = "상품 상세 페이지의 하트 아이콘 상태에 사용. { \"wishlisted\": true/false } 반환.")
    @GetMapping("/{productId}")
    public ApiResponse<java.util.Map<String, Boolean>> isWishlisted(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean wishlisted = wishlistService.isWishlisted(productId, resolveUserId(authentication, username));
        return ApiResponse.ok(java.util.Map.of("wishlisted", wishlisted));
    }

    @Operation(summary = "내 위시리스트 목록 조회 (페이지네이션)")
    @GetMapping
    public ApiResponse<Page<WishlistResponse>> getList(
            @AuthenticationPrincipal String username,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(wishlistService.getList(resolveUserId(authentication, username), pageable));
    }

    /** JWT details에서 userId를 추출하고, 없으면 DB fallback. */
    private Long resolveUserId(Authentication auth, String username) {
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return userService.resolveUserId(username);
    }
}
