package com.stockmanagement.common.storage;

import java.time.Duration;

/** 오브젝트 스토리지 추상화 인터페이스 — MinIO / AWS S3 공용 */
public interface StorageService {

    /**
     * Presigned PUT URL 발급.
     *
     * @param objectKey   스토리지 내 오브젝트 경로 (예: products/1/uuid.jpg)
     * @param contentType 업로드할 파일의 MIME 타입
     * @param expiry      URL 유효 기간
     * @return 클라이언트가 직접 PUT 요청할 수 있는 Presigned URL
     */
    String generatePresignedPutUrl(String objectKey, String contentType, Duration expiry);

    /**
     * 스토리지에서 오브젝트 삭제.
     *
     * @param objectKey 삭제할 오브젝트 경로
     */
    void deleteObject(String objectKey);

    /**
     * objectKey → 공개 접근 URL 변환.
     *
     * @param objectKey 오브젝트 경로
     * @return 브라우저에서 직접 접근 가능한 URL
     */
    String buildPublicUrl(String objectKey);
}
