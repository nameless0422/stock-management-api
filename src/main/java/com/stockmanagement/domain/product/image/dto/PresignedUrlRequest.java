package com.stockmanagement.domain.product.image.dto;

import com.stockmanagement.domain.product.image.entity.ImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class PresignedUrlRequest {

    /** 업로드할 파일의 원본 확장자 (예: jpg, png, webp) */
    @NotBlank
    private String fileExtension;

    /** 업로드할 파일의 MIME 타입 (예: image/jpeg) */
    @NotBlank
    private String contentType;

    @NotNull
    private ImageType imageType;
}
