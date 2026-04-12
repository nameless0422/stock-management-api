package com.stockmanagement.domain.product.wishlist.dto;

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

    public static WishlistResponse of(WishlistItem item, String productName,
                                       BigDecimal productPrice, String thumbnailUrl) {
        return WishlistResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(productName)
                .productPrice(productPrice)
                .thumbnailUrl(thumbnailUrl)
                .addedAt(item.getCreatedAt())
                .build();
    }
}
