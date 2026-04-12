package com.stockmanagement.domain.product.review.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 리뷰 수정 요청 DTO. */
@Getter
@NoArgsConstructor
public class ReviewUpdateRequest {

    @NotNull
    @Min(1) @Max(5)
    private int rating;

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    private String content;
}
