package com.stockmanagement.domain.product.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 상품 검색 요청 DTO.
 * 모든 필드는 선택적이며, null이면 해당 조건은 무시된다.
 *
 * <p>사용 예:
 * <pre>
 *   GET /api/products?q=노트북&minPrice=500000&maxPrice=2000000&category=전자&sort=price_asc
 * </pre>
 *
 * <p>검색 조건({@code q}, {@code minPrice}, {@code maxPrice}, {@code category})이 하나라도
 * 있으면 Elasticsearch 검색을 사용하고, 없으면 MySQL 페이징 조회를 사용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductSearchRequest {

    /** 키워드 — name, description, category, sku에 대해 multi_match 검색 */
    @Size(max = 200, message = "검색어는 200자 이하여야 합니다.")
    private String q;

    /** 최소 가격 (포함) */
    private BigDecimal minPrice;

    /** 최대 가격 (포함) */
    private BigDecimal maxPrice;

    /** 카테고리 (정확 일치) */
    private String category;

    /**
     * 정렬 기준.
     * <ul>
     *   <li>{@code relevance} — 연관도순 (기본값, 키워드 검색 시)
     *   <li>{@code price_asc} — 가격 오름차순
     *   <li>{@code price_desc} — 가격 내림차순
     *   <li>{@code newest} — 최신 등록순
     * </ul>
     */
    private String sort;

    /**
     * Elasticsearch 검색 조건 존재 여부.
     * sort만 지정한 경우도 ES로 처리한다.
     */
    public boolean hasSearchCondition() {
        return (q != null && !q.isBlank())
                || minPrice != null
                || maxPrice != null
                || (category != null && !category.isBlank())
                || (sort != null && !sort.isBlank());
    }
}
