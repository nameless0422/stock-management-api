package com.stockmanagement.domain.inventory.dto;

import com.stockmanagement.domain.inventory.entity.InventoryStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 재고 목록 검색 요청 DTO.
 * 모든 필드는 선택적이며, null이면 해당 조건은 무시된다.
 *
 * <p>사용 예: {@code GET /api/inventory?status=LOW_STOCK&productName=노트북&page=0&size=20}
 */
@Getter
@Setter
@NoArgsConstructor
public class InventorySearchRequest {

    /** 재고 상태 필터 (IN_STOCK / LOW_STOCK / OUT_OF_STOCK) */
    private InventoryStatus status;

    /** 가용 재고 최솟값 (포함) */
    private Integer minAvailable;

    /** 가용 재고 최댓값 (포함) */
    private Integer maxAvailable;

    /** 상품명 키워드 (부분 일치, 대소문자 무시) */
    private String productName;

    /** 카테고리 키워드 (부분 일치, 대소문자 무시) */
    private String category;
}
