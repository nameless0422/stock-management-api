package com.stockmanagement.domain.order.cart.dto;

import com.stockmanagement.domain.inventory.entity.StockStatus;
import com.stockmanagement.domain.order.cart.entity.CartItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** 장바구니 아이템 응답 DTO. */
@Getter
@Builder
public class CartItemResponse {

    private Long productId;
    private String productName;
    private Long variantId;
    private String variantOptionName;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal subtotal;

    /** 상품 대표 이미지 URL — null이면 이미지 없음 */
    private String thumbnailUrl;
    /** 재고 상태 (IN_STOCK / LOW_STOCK / OUT_OF_STOCK) — null이면 재고 정보 미포함 */
    private StockStatus stockStatus;
    /** 담은 수량만큼 구매 가능한지 여부 — null이면 재고 정보 미포함 */
    private Boolean isAvailable;
    /** 장바구니 담은 시점의 단가 — null이면 기록 없음 (이전 데이터) */
    private BigDecimal savedPrice;
    /** 담은 이후 가격이 변동되었는지 여부 — null이면 savedPrice 기록 없음 */
    private Boolean priceChanged;

    public static CartItemResponse from(CartItem item) {
        return from(item, null, null);
    }

    public static CartItemResponse from(CartItem item, Integer availableQuantity, StockStatus stockStatus) {
        BigDecimal unitPrice = item.getVariant().getPrice();
        BigDecimal savedPrice = item.getSavedPrice();
        Boolean priceChanged = savedPrice != null ? savedPrice.compareTo(unitPrice) != 0 : null;
        return CartItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .variantId(item.getVariant().getId())
                .variantOptionName(item.getVariant().getOptionName())
                .thumbnailUrl(item.getProduct().getThumbnailUrl())
                .unitPrice(unitPrice)
                .quantity(item.getQuantity())
                .subtotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                .stockStatus(stockStatus)
                .isAvailable(availableQuantity != null ? availableQuantity >= item.getQuantity() : null)
                .savedPrice(savedPrice)
                .priceChanged(priceChanged)
                .build();
    }
}
