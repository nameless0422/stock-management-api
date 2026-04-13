package com.stockmanagement.domain.product.category.dto;

import com.stockmanagement.domain.product.category.entity.Category;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 카테고리 응답 DTO.
 *
 * <p>flat 목록 조회 시 children은 빈 리스트.
 * 트리 조회 시 children에 하위 카테고리 응답이 채워진다.
 */
@Getter
@Builder
@Jacksonized
public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    /** null이면 최상위 카테고리 */
    private Long parentId;
    /** UI 표시 편의용 부모 이름 */
    private String parentName;
    private List<CategoryResponse> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** flat 목록용 — children 빈 리스트 */
    public static CategoryResponse from(Category category) {
        return from(category, new ArrayList<>());
    }

    /** 명시적 children 목록으로 생성 (트리 빌드 시 사용) */
    public static CategoryResponse from(Category category, List<CategoryResponse> children) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .children(children)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
