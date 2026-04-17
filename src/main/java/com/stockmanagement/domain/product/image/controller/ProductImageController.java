package com.stockmanagement.domain.product.image.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.image.dto.*;
import com.stockmanagement.domain.product.image.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Product Images", description = "상품 이미지 관리 API")
@RestController
@RequestMapping("/api/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    @Operation(summary = "Presigned URL 발급", description = "클라이언트가 MinIO/S3에 직접 업로드할 URL을 발급합니다. (ADMIN 전용)")
    @PostMapping("/presigned")
    public ApiResponse<PresignedUrlResponse> generatePresignedUrl(
            @PathVariable Long productId,
            @Valid @RequestBody PresignedUrlRequest request) {
        return ApiResponse.ok(productImageService.generatePresignedUrl(productId, request));
    }

    @Operation(summary = "이미지 저장", description = "업로드 완료 후 이미지 메타데이터를 DB에 저장합니다. (ADMIN 전용)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageResponse> saveImage(
            @PathVariable Long productId,
            @Valid @RequestBody ProductImageSaveRequest request) {
        return ApiResponse.ok(productImageService.saveImage(productId, request));
    }

    @Operation(summary = "이미지 목록 조회", description = "상품에 등록된 이미지를 순서대로 조회합니다. (공개)")
    @GetMapping
    public ApiResponse<List<ProductImageResponse>> getImages(@PathVariable Long productId) {
        return ApiResponse.ok(productImageService.getImages(productId));
    }

    @Operation(summary = "이미지 순서 변경", description = "이미지 displayOrder를 일괄 변경합니다. (ADMIN 전용)")
    @PatchMapping("/order")
    public ApiResponse<List<ProductImageResponse>> updateImageOrder(
            @PathVariable Long productId,
            @Valid @RequestBody ImageOrderUpdateRequest request) {
        return ApiResponse.ok(productImageService.updateImageOrder(productId, request));
    }

    @Operation(summary = "이미지 삭제", description = "이미지를 스토리지와 DB에서 모두 삭제합니다. (ADMIN 전용)")
    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        productImageService.deleteImage(productId, imageId);
        return ApiResponse.ok();
    }
}
