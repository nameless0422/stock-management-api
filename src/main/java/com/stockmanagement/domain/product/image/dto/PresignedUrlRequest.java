package com.stockmanagement.domain.product.image.dto;

import com.stockmanagement.domain.product.image.entity.ImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class PresignedUrlRequest {

    /** 업로드할 파일의 원본 확장자 (예: jpg, png, webp) */
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9]{1,10}$", message = "허용되지 않는 파일 확장자입니다.")
    private String fileExtension;

    /** 업로드할 파일의 MIME 타입 (예: image/jpeg) */
    @NotBlank
    private String contentType;

    @NotNull
    private ImageType imageType;
}
