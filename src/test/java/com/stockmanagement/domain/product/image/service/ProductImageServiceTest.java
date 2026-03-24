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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageService 단위 테스트")
class ProductImageServiceTest {

    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StorageService storageService;
    @Mock private StorageConfig storageConfig;

    @InjectMocks
    private ProductImageService productImageService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("테스트 상품")
                .description("설명")
                .price(new BigDecimal("10000"))
                .sku("SKU-001")
                .build();
    }

    @Nested
    @DisplayName("generatePresignedUrl()")
    class GeneratePresignedUrl {

        @Test
        @DisplayName("정상 발급 — presignedUrl·imageUrl·objectKey 반환")
        void success() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(storageConfig.getPresignedExpiryMinutes()).willReturn(10);
            given(storageService.generatePresignedPutUrl(anyString(), anyString(), any(Duration.class)))
                    .willReturn("https://minio/presigned");
            given(storageService.buildPublicUrl(anyString())).willReturn("https://minio/public/img.jpg");

            PresignedUrlRequest req = new PresignedUrlRequest();
            setField(req, "fileExtension", "jpg");
            setField(req, "contentType", "image/jpeg");
            setField(req, "imageType", ImageType.THUMBNAIL);

            PresignedUrlResponse res = productImageService.generatePresignedUrl(1L, req);

            assertThat(res.getPresignedUrl()).isEqualTo("https://minio/presigned");
            assertThat(res.getImageUrl()).isEqualTo("https://minio/public/img.jpg");
            assertThat(res.getObjectKey()).isNotBlank();
        }

        @Test
        @DisplayName("존재하지 않는 상품 — PRODUCT_NOT_FOUND")
        void productNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            PresignedUrlRequest req = new PresignedUrlRequest();
            setField(req, "fileExtension", "jpg");
            setField(req, "contentType", "image/jpeg");
            setField(req, "imageType", ImageType.THUMBNAIL);

            assertThatThrownBy(() -> productImageService.generatePresignedUrl(99L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("saveImage()")
    class SaveImage {

        @Test
        @DisplayName("THUMBNAIL 저장 시 products.thumbnail_url 갱신")
        void thumbnailUpdatesThumbnailUrl() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productImageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ProductImageSaveRequest req = new ProductImageSaveRequest();
            setField(req, "imageUrl", "https://minio/img.jpg");
            setField(req, "objectKey", "products/1/uuid.jpg");
            setField(req, "imageType", ImageType.THUMBNAIL);
            setField(req, "displayOrder", 0);

            productImageService.saveImage(1L, req);

            assertThat(product.getThumbnailUrl()).isEqualTo("https://minio/img.jpg");
        }

        @Test
        @DisplayName("DETAIL 저장 시 thumbnail_url 변경 없음")
        void detailDoesNotChangeThumbnail() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productImageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ProductImageSaveRequest req = new ProductImageSaveRequest();
            setField(req, "imageUrl", "https://minio/detail.jpg");
            setField(req, "objectKey", "products/1/uuid2.jpg");
            setField(req, "imageType", ImageType.DETAIL);
            setField(req, "displayOrder", 1);

            productImageService.saveImage(1L, req);

            assertThat(product.getThumbnailUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("getImages()")
    class GetImages {

        @Test
        @DisplayName("등록된 이미지 목록 반환")
        void success() {
            ProductImage img = ProductImage.builder()
                    .product(product)
                    .imageUrl("https://minio/img.jpg")
                    .objectKey("products/1/uuid.jpg")
                    .imageType(ImageType.THUMBNAIL)
                    .displayOrder(0)
                    .build();

            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(1L))
                    .willReturn(List.of(img));

            List<ProductImageResponse> result = productImageService.getImages(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).imageUrl()).isEqualTo("https://minio/img.jpg");
        }
    }

    @Nested
    @DisplayName("deleteImage()")
    class DeleteImage {

        @Test
        @DisplayName("THUMBNAIL 삭제 시 thumbnail_url null로 초기화")
        void deleteThumbnailClearsThumbnailUrl() {
            product.updateThumbnail("https://minio/img.jpg");
            ProductImage img = ProductImage.builder()
                    .product(product)
                    .imageUrl("https://minio/img.jpg")
                    .objectKey("products/1/uuid.jpg")
                    .imageType(ImageType.THUMBNAIL)
                    .displayOrder(0)
                    .build();

            given(productImageRepository.findByIdAndProductId(1L, 1L)).willReturn(Optional.of(img));
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            productImageService.deleteImage(1L, 1L);

            verify(storageService).deleteObject("products/1/uuid.jpg");
            verify(productImageRepository).delete(img);
            assertThat(product.getThumbnailUrl()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 이미지 — PRODUCT_IMAGE_NOT_FOUND")
        void imageNotFound() {
            given(productImageRepository.findByIdAndProductId(99L, 1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productImageService.deleteImage(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_IMAGE_NOT_FOUND);
        }
    }

    // ===== 헬퍼: DTO 필드 직접 설정 =====
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
