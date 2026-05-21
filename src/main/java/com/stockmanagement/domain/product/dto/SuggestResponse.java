package com.stockmanagement.domain.product.dto;

import java.util.List;

/** 검색 자동완성 응답 DTO. */
public record SuggestResponse(List<String> suggestions) {
}
