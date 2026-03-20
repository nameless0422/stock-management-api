package com.stockmanagement.domain.product.category.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 수정 요청 DTO.
 * null 필드는 수정하지 않는다 (단, description은 null 전달 시 null로 갱신).
 * parentId는 null 전달 시 최상위 카테고리로 변경된다.
 */
@Getter
@NoArgsConstructor
public class CategoryUpdateRequest {

    @Size(max = 100, message = "카테고리 이름은 100자를 초과할 수 없습니다.")
    private String name;

    @Size(max = 255, message = "카테고리 설명은 255자를 초과할 수 없습니다.")
    private String description;

    /** null이면 최상위 카테고리로 변경 */
    private Long parentId;
}
