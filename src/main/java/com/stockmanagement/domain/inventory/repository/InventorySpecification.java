package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.dto.InventorySearchRequest;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.entity.InventoryStatus;
import com.stockmanagement.domain.product.entity.Product;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 재고 동적 검색 조건 빌더.
 * JPA Criteria API를 사용해 null이 아닌 조건만 AND 결합한다.
 *
 * <p>저재고 기준: available < {@value #LOW_STOCK_THRESHOLD}
 */
public class InventorySpecification {

    /** 저재고 임계값 (대시보드 findLowStock 기준과 동일) */
    static final int LOW_STOCK_THRESHOLD = 10;

    private InventorySpecification() {}

    /**
     * 검색 요청 객체로부터 Specification을 생성한다.
     * 모든 조건은 선택적이며, null인 경우 해당 조건은 적용되지 않는다.
     */
    public static Specification<Inventory> of(InventorySearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // product JOIN (필터용 — FETCH 아님, EntityGraph에서 별도 로딩)
            Join<Inventory, Product> product = root.join("product", JoinType.INNER);

            // available = onHand - reserved - allocated (Criteria 연산)
            Expression<Integer> available = cb.diff(
                cb.diff(root.<Integer>get("onHand"), root.<Integer>get("reserved")),
                root.<Integer>get("allocated")
            );

            // 재고 상태 필터
            if (request.getStatus() != null) {
                predicates.add(statusPredicate(request.getStatus(), available, cb));
            }

            // 가용 재고 범위 필터
            if (request.getMinAvailable() != null) {
                predicates.add(cb.greaterThanOrEqualTo(available, request.getMinAvailable()));
            }
            if (request.getMaxAvailable() != null) {
                predicates.add(cb.lessThanOrEqualTo(available, request.getMaxAvailable()));
            }

            // 상품명 키워드 검색 (대소문자 무시 부분 일치)
            if (request.getProductName() != null && !request.getProductName().isBlank()) {
                predicates.add(cb.like(
                    cb.lower(product.get("name")),
                    "%" + request.getProductName().toLowerCase() + "%"
                ));
            }

            // 카테고리 키워드 검색 (대소문자 무시 부분 일치)
            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                predicates.add(cb.like(
                    cb.lower(product.get("category")),
                    "%" + request.getCategory().toLowerCase() + "%"
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate statusPredicate(InventoryStatus status,
                                             Expression<Integer> available,
                                             CriteriaBuilder cb) {
        return switch (status) {
            case IN_STOCK    -> cb.greaterThanOrEqualTo(available, LOW_STOCK_THRESHOLD);
            case LOW_STOCK   -> cb.and(
                cb.greaterThan(available, 0),
                cb.lessThan(available, LOW_STOCK_THRESHOLD)
            );
            case OUT_OF_STOCK -> cb.lessThanOrEqualTo(available, 0);
        };
    }
}
