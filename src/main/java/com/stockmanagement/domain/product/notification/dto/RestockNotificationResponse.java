package com.stockmanagement.domain.product.notification.dto;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.notification.entity.RestockNotification;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class RestockNotificationResponse {

    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private String thumbnailUrl;
    private LocalDateTime createdAt;

    public static RestockNotificationResponse of(RestockNotification notification, Product product) {
        return RestockNotificationResponse.builder()
                .id(notification.getId())
                .productId(notification.getProductId())
                .productName(product.getName())
                .price(product.getPrice())
                .thumbnailUrl(product.getThumbnailUrl())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
