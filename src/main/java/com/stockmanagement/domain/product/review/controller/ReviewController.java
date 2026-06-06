package com.stockmanagement.domain.product.review.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.domain.product.review.dto.RatingStatsResponse;
import com.stockmanagement.domain.product.review.dto.ReviewCreateRequest;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.dto.ReviewUpdateRequest;
import com.stockmanagement.domain.product.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review", description = "상품 리뷰 API")
@Validated
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성 (실구매자 한정, 상품당 1회)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(
            @PathVariable Long productId,
            @CurrentUserId Long userId,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ApiResponse.ok(reviewService.create(productId, userId, request));
    }

    @Operation(summary = "상품 리뷰 목록 조회 (커서 기반)",
               description = "rating: 1~5 별점 필터 (null이면 전체)")
    @GetMapping
    public ApiResponse<CursorPage<ReviewResponse>> getList(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long lastId,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.getList(productId, lastId, size, rating));
    }

    @Operation(summary = "리뷰 별점 분포 통계",
               description = "avgRating, reviewCount, distribution(1~5별 리뷰 수) 반환. 비로그인 공개.")
    @GetMapping("/stats")
    public ApiResponse<RatingStatsResponse> getRatingStats(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.getRatingStats(productId));
    }

    @Operation(summary = "리뷰 수정 (작성자 본인)")
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> update(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @CurrentUserId Long userId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ApiResponse.ok(reviewService.update(productId, reviewId, userId, request));
    }

    @Operation(summary = "리뷰 삭제 (작성자 본인)")
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @CurrentUserId Long userId) {
        reviewService.delete(productId, reviewId, userId);
    }
}
