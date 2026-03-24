package com.stockmanagement.common.storage;

import com.stockmanagement.common.config.StorageConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.enabled", matchIfMissing = true)
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageConfig storageConfig;

    /** 애플리케이션 시작 시 버킷이 없으면 자동 생성 (MinIO 미실행 시 경고만 출력) */
    @PostConstruct
    public void createBucketIfNotExists() {
        String bucket = storageConfig.getBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("[Storage] 버킷 확인 완료: {}", bucket);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("[Storage] 버킷 생성 완료: {}", bucket);
        } catch (Exception e) {
            log.warn("[Storage] MinIO 연결 실패 — 이미지 업로드 기능이 비활성화됩니다. endpoint={}, error={}",
                    storageConfig.getEndpoint(), e.getMessage());
        }
    }

    @Override
    public String generatePresignedPutUrl(String objectKey, String contentType, Duration expiry) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(req -> req
                        .bucket(storageConfig.getBucket())
                        .key(objectKey)
                        .contentType(contentType))
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public void deleteObject(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(storageConfig.getBucket())
                .key(objectKey)
                .build());
        log.info("[Storage] 오브젝트 삭제: {}", objectKey);
    }

    @Override
    public String buildPublicUrl(String objectKey) {
        // {publicUrl}/{bucket}/{objectKey}
        return storageConfig.getPublicUrl() + "/" + storageConfig.getBucket() + "/" + objectKey;
    }
}
