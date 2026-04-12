package com.stockmanagement.domain.product.image.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresignedUrlResponse {

    /** 클라이언트가 파일을 직접 PUT할 URL (만료 시간 포함) */
    private String presignedUrl;

    /** DB에 저장할 공개 이미지 URL */
    private String imageUrl;

    /** 스토리지 내 오브젝트 경로 (이후 saveImage 요청 시 함께 전송) */
    private String objectKey;
}
