package com.stockmanagement.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 상품 검색 전체 재색인 결과 응답. */
@Getter
@AllArgsConstructor
public class ReindexResponse {

    /** 색인된 상품 수 */
    private final long indexedCount;
}
