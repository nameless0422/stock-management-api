package com.stockmanagement.domain.product.image.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 이미지 순서 일괄 변경 요청 DTO. */
@Getter
@NoArgsConstructor
public class ImageOrderUpdateRequest {

    @NotEmpty(message = "순서 목록은 필수입니다.")
    @Valid
    private List<ImageOrderItem> orders;
}
