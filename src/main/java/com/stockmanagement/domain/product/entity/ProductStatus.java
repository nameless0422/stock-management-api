package com.stockmanagement.domain.product.entity;

/**
 * 상품의 판매 상태.
 *
 * <p>상품은 삭제하지 않고 상태를 DISCONTINUED로 변경해 이력을 보존한다.
 * INACTIVE는 일시적 판매 중지, DISCONTINUED는 영구 단종을 의미한다.
 */
public enum ProductStatus {
    ACTIVE,       // 판매 중 — 정상적으로 주문 가능
    INACTIVE,     // 판매 중지 — 일시적으로 판매 불가 (재입고 등으로 ACTIVE 복귀 가능)
    DISCONTINUED  // 단종 — 소프트 삭제 용도, 복귀하지 않는 최종 상태
}
