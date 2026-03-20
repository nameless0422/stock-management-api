package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 주문 목록 검색 요청 DTO.
 * 모든 필드는 선택적이며, null이면 해당 조건은 무시된다.
 *
 * <p>사용 예: {@code GET /api/orders?status=PENDING&startDate=2025-01-01&endDate=2025-12-31}
 * <p>ADMIN은 userId 파라미터로 특정 사용자 주문을 조회할 수 있다.
 * USER 권한은 본인 주문만 조회되므로 userId 파라미터는 무시된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderSearchRequest {

    /** 주문 상태 필터 (PENDING / CONFIRMED / CANCELLED) */
    private OrderStatus status;

    /**
     * 사용자 ID 필터 — ADMIN 전용.
     * USER 권한에서는 서비스 레이어에서 본인 ID로 강제 대체된다.
     */
    private Long userId;

    /** 주문 생성일 시작 (포함) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 주문 생성일 종료 (포함) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
