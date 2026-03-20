package com.stockmanagement.domain.product.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 생성 요청 DTO.
 * parentId가 null이면 최상위 카테고리로 생성된다.
 */
@Getter
@NoArgsConstructor
public class CategoryCreateRequest {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Size(max = 100, message = "카테고리 이름은 100자를 초과할 수 없습니다.")
    private String name;

    @Size(max = 255, message = "카테고리 설명은 255자를 초과할 수 없습니다.")
    private String description;

    /** null이면 최상위 카테고리 */
    private Long parentId;
}
