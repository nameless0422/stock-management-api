package com.stockmanagement.domain.product.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
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

    @Operation(summary = "상품 목록 조회 (페이징)", description = "?page=0&size=10&sort=name,asc&search=키워드")
    @GetMapping
    public ApiResponse<Page<ProductResponse>> getList(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(productService.getList(pageable, search));
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
