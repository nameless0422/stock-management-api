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
        return of(request, null);
    }

    /**
     * 검색 요청 + 강제 userId 적용 Specification을 생성한다.
     *
     * <p>USER 권한에서는 {@code forceUserId}로 본인 ID를 주입하여 타인 주문 조회를 차단한다.
     * 요청 DTO 자체를 변경하지 않으므로 멀티스레드 환경에서도 안전하다.
     *
     * @param request     클라이언트 검색 조건 (status, startDate, endDate 등)
     * @param forceUserId null이면 request.userId 사용, non-null이면 강제 덮어쓰기
     */
    public static Specification<Order> of(OrderSearchRequest request, Long forceUserId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 주문 상태 필터
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }

            // 사용자 ID 필터 — USER 권한은 forceUserId로 강제 적용 (DTO 변경 없음)
            Long effectiveUserId = forceUserId != null ? forceUserId : request.getUserId();
            if (effectiveUserId != null) {
                predicates.add(cb.equal(root.get("userId"), effectiveUserId));
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
