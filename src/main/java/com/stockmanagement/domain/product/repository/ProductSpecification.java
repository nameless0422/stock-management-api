package com.stockmanagement.domain.product.repository;

import com.stockmanagement.common.util.SqlUtils;
import com.stockmanagement.domain.product.dto.ProductSearchRequest;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 상품 동적 검색 조건 빌더 (MySQL fallback용).
 * JPA Criteria API를 사용해 null이 아닌 조건만 AND 결합한다.
 *
 * <p>ES 장애 시 fallback 경로와 categoryId 단독 조회에서 사용되며,
 * 키워드(name/SKU LIKE)·가격 범위·카테고리명·카테고리 ID 필터를 모두 지원한다.
 */
public class ProductSpecification {

    private ProductSpecification() {}

    /**
     * ACTIVE 상품 대상 검색 Specification을 생성한다.
     *
     * @param request     검색 조건 (q, minPrice, maxPrice, category) — 필드가 null이면 해당 조건 무시
     * @param categoryIds 카테고리 ID 필터 (하위 카테고리 포함 가능) — 비어 있으면 무시
     */
    public static Specification<Product> activeWithFilters(ProductSearchRequest request,
                                                           Collection<Long> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));

            // 키워드 — 상품명/SKU LIKE (대소문자 무시, ES multi_match의 축소판)
            if (request.getQ() != null && !request.getQ().isBlank()) {
                String pattern = "%" + SqlUtils.escapeLike(request.getQ()).toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '!'),
                        cb.like(cb.lower(root.get("sku")), pattern, '!')));
            }

            // 카테고리 ID 필터
            if (categoryIds != null && !categoryIds.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }

            // 카테고리명 필터 (정확 일치)
            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                predicates.add(cb.equal(root.get("category").get("name"), request.getCategory()));
            }

            // 가격 범위 필터
            if (request.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), request.getMaxPrice()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
