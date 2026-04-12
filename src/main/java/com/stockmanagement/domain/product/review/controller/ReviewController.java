package com.stockmanagement.domain.product.review.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.review.dto.ReviewCreateRequest;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.dto.ReviewUpdateRequest;
import com.stockmanagement.domain.product.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review", description = "상품 리뷰 API")
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성 (실구매자 한정, 상품당 1회)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(
            @PathVariable Long productId,
            @AuthenticationPrincipal String username,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ApiResponse.ok(reviewService.create(productId, username, request));
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
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ApiResponse.ok(reviewService.update(productId, reviewId, username, request));
    }

    @Operation(summary = "리뷰 삭제 (작성자 본인)")
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal String username) {
        reviewService.delete(productId, reviewId, username);
    }
}
