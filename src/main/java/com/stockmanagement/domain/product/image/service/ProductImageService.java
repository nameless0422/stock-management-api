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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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

    /** 상품당 최대 이미지 개수 */
    static final int MAX_IMAGES_PER_PRODUCT = 10;

    /** 업로드 완료 후 이미지 메타데이터를 DB에 저장. THUMBNAIL이면 products.thumbnail_url도 갱신. */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ProductImageResponse saveImage(Long productId, ProductImageSaveRequest request) {
        Product product = findProduct(productId);

        if (productImageRepository.countByProductId(productId) >= MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        }

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

    /**
     * 이미지 순서를 일괄 변경한다.
     *
     * <p>요청의 imageId가 모두 해당 productId에 속해야 하며, 개수가 일치해야 한다.
     * Dirty Checking으로 UPDATE가 자동 실행된다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public List<ProductImageResponse> updateImageOrder(Long productId, ImageOrderUpdateRequest request) {
        findProduct(productId);

        List<Long> requestedIds = request.getOrders().stream()
                .map(ImageOrderItem::getImageId)
                .toList();

        List<ProductImage> images = productImageRepository.findByProductIdAndIdIn(productId, requestedIds);

        if (images.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND);
        }

        Map<Long, ProductImage> imageMap = images.stream()
                .collect(Collectors.toMap(ProductImage::getId, img -> img));

        request.getOrders().forEach(item -> imageMap.get(item.getImageId())
                .updateDisplayOrder(item.getDisplayOrder()));

        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(ProductImageResponse::from)
                .toList();
    }

    /** 이미지 삭제 — DB 레코드 먼저 삭제 후 S3 오브젝트 제거 */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND));

        // DB 먼저 삭제 (트랜잭션으로 보호 — S3 실패 시 롤백 가능)
        productImageRepository.delete(image);

        // THUMBNAIL 삭제 시 products.thumbnail_url 초기화
        if (image.getImageType() == ImageType.THUMBNAIL) {
            findProduct(productId).updateThumbnail(null);
        }

        // S3 삭제: 실패 시 경고 로그만 기록 (S3 고아 객체 가능하나 DB 정합은 유지)
        try {
            storageService.deleteObject(image.getObjectKey());
        } catch (Exception e) {
            log.warn("[ProductImage] S3 오브젝트 삭제 실패 — 고아 객체 발생 가능: key={} error={}",
                    image.getObjectKey(), e.getMessage());
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
