package com.stockmanagement.domain.product.wishlist.dto;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.wishlist.entity.WishlistItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class WishlistResponse {

    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private String thumbnailUrl;
    private LocalDateTime addedAt;

    /** 현재 가용 재고 수량 — null이면 재고 정보 미포함 */
    private Integer availableQuantity;

    /** 상품 판매 상태 */
    private ProductStatus productStatus;

    public static WishlistResponse of(WishlistItem item, Product product, Integer availableQuantity) {
        return WishlistResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(product.getName())
                .productPrice(product.getPrice())
                .thumbnailUrl(product.getThumbnailUrl())
                .addedAt(item.getCreatedAt())
                .availableQuantity(availableQuantity)
                .productStatus(product.getStatus())
                .build();
    }
}
