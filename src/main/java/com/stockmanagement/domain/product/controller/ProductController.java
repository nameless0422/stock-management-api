package com.stockmanagement.domain.product.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.dto.ProductUpdateRequest;
import com.stockmanagement.domain.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 상품 REST API 컨트롤러.
 *
 * <p>Base URL: {@code /api/products}
 *
 * <p>컨트롤러는 HTTP 요청/응답 변환만 담당한다.
 * 비즈니스 로직은 {@link ProductService}에 위임한다.
 *
 * <pre>
 * POST   /api/products       상품 등록 → 201 Created
 * GET    /api/products/{id}  단건 조회 → 200 OK
 * GET    /api/products       목록 조회 → 200 OK (페이징)
 * PUT    /api/products/{id}  수정      → 200 OK
 * DELETE /api/products/{id}  삭제      → 204 No Content
 * </pre>
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 상품 등록 — @Valid로 요청 DTO 유효성 검증, 성공 시 201 반환 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@RequestBody @Valid ProductCreateRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    /** 상품 단건 조회 */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    /**
     * 상품 목록 조회 (페이징).
     * 기본값: 페이지당 20개, id 오름차순 정렬.
     * 쿼리 파라미터 예시: ?page=0&size=10&sort=name,asc
     */
    @GetMapping
    public ApiResponse<Page<ProductResponse>> getList(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(productService.getList(pageable));
    }

    /** 상품 수정 */
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductUpdateRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    /** 상품 삭제 (소프트 삭제) — 응답 바디 없이 204 반환 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
