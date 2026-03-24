package com.stockmanagement.domain.product.review.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewCreateRequest {

    @NotNull
    @Min(1) @Max(5)
    private int rating;

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    private String content;
}
