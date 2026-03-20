package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.entity.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 동적 검색 조건 빌더.
 * JPA Criteria API를 사용해 null이 아닌 조건만 AND 결합한다.
 */
public class OrderSpecification {

    private OrderSpecification() {}

    /**
     * 검색 요청 객체로부터 Specification을 생성한다.
     * 모든 조건은 선택적이며, null인 경우 해당 조건은 적용되지 않는다.
     */
    public static Specification<Order> of(OrderSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 주문 상태 필터
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }

            // 사용자 ID 필터
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }

            // 주문 생성일 시작 (해당 날짜 00:00:00 이후)
            if (request.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        request.getStartDate().atStartOfDay()
                ));
            }

            // 주문 생성일 종료 (해당 날짜 23:59:59 이하)
            if (request.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        request.getEndDate().atTime(LocalTime.MAX)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
