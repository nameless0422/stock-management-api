package com.stockmanagement.domain.product.image.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이미지 순서 변경 요청의 개별 항목. */
@Getter
@NoArgsConstructor
public class ImageOrderItem {

    @NotNull(message = "이미지 ID는 필수입니다.")
    private Long imageId;

    @NotNull(message = "displayOrder는 필수입니다.")
    @Min(value = 0, message = "displayOrder는 0 이상이어야 합니다.")
    private Integer displayOrder;
}
