package com.stockmanagement.domain.order.entity;

import com.stockmanagement.domain.product.entity.ProductVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("Order 엔티티 단위 테스트")
class OrderTest {

    @Test
    @DisplayName("getItemsSortedByVariant()는 variantId 오름차순으로 반환한다 — 재고 락 획득 순서 통일")
    void getItemsSortedByVariant_returnsAscendingVariantIdOrder() {
        Order order = Order.builder()
                .userId(1L)
                .totalAmount(BigDecimal.valueOf(10_000))
                .idempotencyKey("sort-test-001")
                .build();

        // 삽입 순서: variantId 3 → 1 → 2
        order.addItem(itemWithVariantId(3L));
        order.addItem(itemWithVariantId(1L));
        order.addItem(itemWithVariantId(2L));

        assertThat(order.getItemsSortedByVariant())
                .extracting(i -> i.getVariant().getId())
                .containsExactly(1L, 2L, 3L);
        // 원본 items 순서는 변경되지 않는다
        assertThat(order.getItems())
                .extracting(i -> i.getVariant().getId())
                .containsExactly(3L, 1L, 2L);
    }

    private OrderItem itemWithVariantId(Long variantId) {
        ProductVariant variant = mock(ProductVariant.class);
        given(variant.getId()).willReturn(variantId);
        OrderItem item = mock(OrderItem.class);
        given(item.getVariant()).willReturn(variant);
        return item;
    }
}
