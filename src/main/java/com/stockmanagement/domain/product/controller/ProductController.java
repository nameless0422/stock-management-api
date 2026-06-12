package com.stockmanagement.domain.product.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.dto.ProductSearchRequest;
import com.stockmanagement.domain.product.dto.ProductStatusRequest;
import com.stockmanagement.domain.product.dto.ProductUpdateRequest;
import com.stockmanagement.domain.product.dto.ProductVariantCreateRequest;
import com.stockmanagement.domain.product.dto.ProductVariantResponse;
import com.stockmanagement.domain.product.dto.ProductVariantUpdateRequest;
import com.stockmanagement.domain.product.dto.SuggestResponse;
import com.stockmanagement.domain.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "상품", description = "상품 CRUD — 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록", description = "ADMIN 전용. SKU 중복 시 409. 기본 variant와 Inventory를 자동 생성한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@RequestBody @Valid ProductCreateRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @Operation(summary = "상품 단건 조회", description = "로그인 사용자는 canReview 필드 포함. 비로그인 시 canReview=null.")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(
            @PathVariable Long id,
            @CurrentUserId(required = false) Long userId) {
        if (userId != null) {
            return ApiResponse.ok(productService.getByIdForUser(id, userId));
        }
        return ApiResponse.ok(productService.getById(id));
    }

    @Operation(summary = "상품 목록 조회 (검색 + 페이징)",
               description = "검색 조건이 있으면 Elasticsearch, 없으면 MySQL 조회.\n\n" +
                       "- `q`: 키워드 (name, description, category, sku 검색)\n" +
                       "- `minPrice` / `maxPrice`: 가격 범위\n" +
                       "- `category`: 카테고리 이름 정확 일치\n" +
                       "- `categoryId`: 카테고리 ID (includeChildren=true 시 하위 카테고리 포함)\n" +
                       "- `sort`: relevance(기본) | price_asc | price_desc | newest")
    @GetMapping
    public ApiResponse<Page<ProductResponse>> getList(
            @ModelAttribute @Valid ProductSearchRequest request,
            @PageableDefault(size = 20, sort = "id") Pageable pageable,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.ok(productService.getList(pageable, request, userId));
    }

    @Operation(summary = "검색 자동완성", description = "검색어 prefix로 상품명 자동완성 후보를 반환한다. 최대 10건.")
    @GetMapping("/search/suggestions")
    public ApiResponse<SuggestResponse> suggest(
            @RequestParam @Size(min = 1, max = 200, message = "검색어는 1~200자여야 합니다.") String q) {
        return ApiResponse.ok(new SuggestResponse(productService.suggest(q, 10)));
    }

    @Operation(summary = "상품 수정", description = "ADMIN 전용.")
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductUpdateRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @Operation(summary = "상품 삭제 (soft delete)", description = "ADMIN 전용. status=DISCONTINUED로 변경.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    @Operation(summary = "상품 상태 변경", description = "ADMIN 전용. ACTIVE ↔ DISCONTINUED 전환.")
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductResponse> changeStatus(
            @PathVariable Long id,
            @RequestBody @Valid ProductStatusRequest request) {
        return ApiResponse.ok(productService.changeStatus(id, request));
    }

    // ===== Variant 엔드포인트 =====

    @Operation(summary = "변형 목록 조회", description = "상품의 모든 변형을 조회한다.")
    @GetMapping("/{productId}/variants")
    public ApiResponse<List<ProductVariantResponse>> getVariants(@PathVariable Long productId) {
        return ApiResponse.ok(productService.getVariants(productId));
    }

    @Operation(summary = "변형 추가", description = "ADMIN 전용. SKU 중복 시 409. Inventory를 자동 생성한다.")
    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductVariantResponse> addVariant(
            @PathVariable Long productId,
            @RequestBody @Valid ProductVariantCreateRequest request) {
        return ApiResponse.ok(productService.addVariant(productId, request));
    }

    @Operation(summary = "변형 수정", description = "ADMIN 전용. optionName, price, status를 변경한다.")
    @PutMapping("/{productId}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> updateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @RequestBody @Valid ProductVariantUpdateRequest request) {
        return ApiResponse.ok(productService.updateVariant(productId, variantId, request));
    }

    @Operation(summary = "변형 비활성화", description = "ADMIN 전용. DISCONTINUED 상태로 전환.")
    @DeleteMapping("/{productId}/variants/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        productService.deactivateVariant(productId, variantId);
    }
}
