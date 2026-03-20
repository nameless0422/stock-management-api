package com.stockmanagement.domain.product.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.dto.ProductSearchRequest;
import com.stockmanagement.domain.product.dto.ProductStatusRequest;
import com.stockmanagement.domain.product.dto.ProductUpdateRequest;
import com.stockmanagement.domain.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "상품", description = "상품 CRUD — 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록", description = "ADMIN 전용. SKU 중복 시 409.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@RequestBody @Valid ProductCreateRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @Operation(summary = "상품 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @Operation(summary = "상품 목록 조회 (검색 + 페이징)",
               description = "검색 조건이 있으면 Elasticsearch, 없으면 MySQL 조회.\n\n" +
                       "- `q`: 키워드 (name, description, category, sku 검색)\n" +
                       "- `minPrice` / `maxPrice`: 가격 범위\n" +
                       "- `category`: 카테고리 정확 일치\n" +
                       "- `sort`: relevance(기본) | price_asc | price_desc | newest")
    @GetMapping
    public ApiResponse<Page<ProductResponse>> getList(
            @ModelAttribute ProductSearchRequest request,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(productService.getList(pageable, request));
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
}
