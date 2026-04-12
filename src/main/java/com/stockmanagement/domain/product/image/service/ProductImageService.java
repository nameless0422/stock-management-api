package com.stockmanagement.domain.product.image.service;

import com.stockmanagement.common.config.StorageConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.storage.StorageService;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.image.dto.*;
import com.stockmanagement.domain.product.image.entity.ImageType;
import com.stockmanagement.domain.product.image.entity.ProductImage;
import com.stockmanagement.domain.product.image.repository.ProductImageRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final StorageService storageService;
    private final StorageConfig storageConfig;

    /**
     * Presigned PUT URL 발급.
     * 클라이언트는 반환된 presignedUrl로 파일을 직접 PUT한 뒤 saveImage()를 호출한다.
     */
    public PresignedUrlResponse generatePresignedUrl(Long productId, PresignedUrlRequest request) {
        // 상품 존재 검증
        findProduct(productId);

        String objectKey = buildObjectKey(productId, request.getFileExtension());
        Duration expiry = Duration.ofMinutes(storageConfig.getPresignedExpiryMinutes());

        String presignedUrl = storageService.generatePresignedPutUrl(objectKey, request.getContentType(), expiry);
        String imageUrl = storageService.buildPublicUrl(objectKey);

        return new PresignedUrlResponse(presignedUrl, imageUrl, objectKey);
    }

    /** 업로드 완료 후 이미지 메타데이터를 DB에 저장. THUMBNAIL이면 products.thumbnail_url도 갱신. */
    @Transactional
    public ProductImageResponse saveImage(Long productId, ProductImageSaveRequest request) {
        Product product = findProduct(productId);

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(request.getImageUrl())
                .objectKey(request.getObjectKey())
                .imageType(request.getImageType())
                .displayOrder(request.getDisplayOrder())
                .build();
        productImageRepository.save(image);

        if (request.getImageType() == ImageType.THUMBNAIL) {
            product.updateThumbnail(request.getImageUrl());
        }

        return ProductImageResponse.from(image);
    }

    /** 상품에 등록된 이미지 목록 조회 (displayOrder 오름차순) */
    public List<ProductImageResponse> getImages(Long productId) {
        findProduct(productId);
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(ProductImageResponse::from)
                .toList();
    }

    /** 이미지 삭제 — 스토리지 오브젝트 + DB 레코드 순으로 제거 */
    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND));

        storageService.deleteObject(image.getObjectKey());
        productImageRepository.delete(image);

        // THUMBNAIL 삭제 시 products.thumbnail_url 초기화
        if (image.getImageType() == ImageType.THUMBNAIL) {
            findProduct(productId).updateThumbnail(null);
        }
    }

    // ===== 내부 헬퍼 =====

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** objectKey 형식: products/{productId}/{UUID}.{extension} */
    private String buildObjectKey(Long productId, String extension) {
        return "products/" + productId + "/" + UUID.randomUUID() + "." + extension.toLowerCase();
    }
}
