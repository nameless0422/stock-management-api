package com.stockmanagement.domain.product.image.dto;

import com.stockmanagement.domain.product.image.entity.ImageType;
import com.stockmanagement.domain.product.image.entity.ProductImage;

import java.time.LocalDateTime;

public record ProductImageResponse(
        Long id,
        Long productId,
        String imageUrl,
        ImageType imageType,
        int displayOrder,
        LocalDateTime createdAt
) {
    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getProduct().getId(),
                image.getImageUrl(),
                image.getImageType(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
