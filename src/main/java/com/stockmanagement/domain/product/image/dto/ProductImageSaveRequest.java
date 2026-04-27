package com.stockmanagement.domain.product.image.dto;

import com.stockmanagement.domain.product.image.entity.ImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class ProductImageSaveRequest {

    /** presigned URL 발급 시 받은 공개 이미지 URL */
    @NotBlank
    private String imageUrl;

    /**
     * presigned URL 발급 시 서버가 반환한 오브젝트 경로.
     * 형식: products/{productId}/{uuid}.{extension}
     */
    @NotBlank
    @Pattern(
            regexp = "^products/[0-9]+/[0-9a-f-]+[.][a-zA-Z0-9]{1,10}$",
            message = "올바른 오브젝트 경로 형식이 아닙니다."
    )
    private String objectKey;

    @NotNull
    private ImageType imageType;

    /** 상세 이미지 정렬 순서 (THUMBNAIL이면 관습적으로 0) */
    private int displayOrder;
}
