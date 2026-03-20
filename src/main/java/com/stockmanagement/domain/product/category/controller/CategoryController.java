package com.stockmanagement.domain.product.category.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.product.category.dto.CategoryCreateRequest;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.category.dto.CategoryUpdateRequest;
import com.stockmanagement.domain.product.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Category", description = "카테고리 관리 API")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 생성 [ADMIN]")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @Operation(summary = "카테고리 flat 목록 [공개]")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getList() {
        return ApiResponse.ok(categoryService.getList());
    }

    @Operation(summary = "카테고리 트리 [공개]", description = "최상위 카테고리와 그 하위 카테고리를 트리 구조로 반환합니다.")
    @GetMapping("/tree")
    public ApiResponse<List<CategoryResponse>> getTree() {
        return ApiResponse.ok(categoryService.getTree());
    }

    @Operation(summary = "카테고리 단건 조회 [공개]", description = "children(하위 카테고리) 목록 포함")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getById(id));
    }

    @Operation(summary = "카테고리 수정 [ADMIN]")
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    @Operation(summary = "카테고리 삭제 [ADMIN]", description = "하위 카테고리 또는 연결된 상품이 있으면 409 반환.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
