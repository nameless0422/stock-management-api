package com.stockmanagement.domain.product.review.dto;

import com.stockmanagement.domain.product.review.entity.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private Long productId;
    private Long userId;
    /** 마스킹된 작성자 이름 (예: "ab***") — null이면 정보 미포함 */
    private String username;
    private int rating;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    public static ReviewResponse from(Review review) {
        return from(review, null);
    }

    public static ReviewResponse from(Review review, String username) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .username(maskUsername(username))
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .build();
    }

    /** 앞 2자리 유지, 나머지 마스킹. 길이 2 이하면 전체 마스킹. */
    private static String maskUsername(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() <= 2) return "***";
        return raw.substring(0, 2) + "***";
    }
}
