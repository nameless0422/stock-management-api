package com.stockmanagement.domain.product.qna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QnaAnswerRequest {

    @NotBlank
    @Size(max = 5000)
    private String answer;
}
