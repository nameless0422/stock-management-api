package com.stockmanagement.domain.product.review.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.review.dto.ReviewCreateRequest;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.dto.ReviewUpdateRequest;
import com.stockmanagement.domain.product.review.service.ReviewService;
import com.stockmanagement.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review", description = "상품 리뷰 API")
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    @Operation(summary = "리뷰 작성 (실구매자 한정, 상품당 1회)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            Authentication authentication,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ApiResponse.ok(reviewService.create(productId, resolveUserId(authentication, username), request));
    }

    @Operation(summary = "상품 리뷰 목록 조회 (최신순)")
    @GetMapping
    public ApiResponse<Page<ReviewResponse>> getList(
            @PathVariable Long productId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(reviewService.getList(productId, pageable));
    }

    @Operation(summary = "리뷰 수정 (작성자 본인)")
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> update(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String username,
            Authentication authentication,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ApiResponse.ok(reviewService.update(productId, reviewId, resolveUserId(authentication, username), request));
    }

    @Operation(summary = "리뷰 삭제 (작성자 본인)")
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        reviewService.delete(productId, reviewId, resolveUserId(authentication, username));
    }

    /** JWT claim details에서 userId 추출. 구 토큰이면 DB fallback. */
    private Long resolveUserId(Authentication auth, String username) {
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return userService.resolveUserId(username);
    }
}
