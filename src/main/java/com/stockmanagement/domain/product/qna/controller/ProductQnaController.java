package com.stockmanagement.domain.product.qna.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.product.qna.dto.QnaAnswerRequest;
import com.stockmanagement.domain.product.qna.dto.QnaCreateRequest;
import com.stockmanagement.domain.product.qna.dto.QnaResponse;
import com.stockmanagement.domain.product.qna.service.ProductQnaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 상품 Q&A REST API 컨트롤러.
 *
 * <pre>
 * GET    /api/products/{productId}/qna              Q&A 목록 (공개)         → 200
 * POST   /api/products/{productId}/qna              질문 작성 (인증 필요)    → 201
 * POST   /api/products/{productId}/qna/{qnaId}/answer  답변 작성 (ADMIN)   → 200
 * DELETE /api/products/{productId}/qna/{qnaId}      삭제 (작성자 or ADMIN)  → 204
 * </pre>
 */
@Tag(name = "상품 Q&A", description = "상품 질문·답변 관리")
@RestController
@RequestMapping("/api/v1/products/{productId}/qna")
@RequiredArgsConstructor
public class ProductQnaController {

    private final ProductQnaService qnaService;

    @Operation(summary = "Q&A 목록 조회", description = "비밀글은 작성자 본인과 ADMIN에게만 원문 노출.")
    @GetMapping
    public ApiResponse<Page<QnaResponse>> getList(
            @PathVariable Long productId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUserId(required = false) Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(qnaService.getList(productId, pageable, userId, isAdmin));
    }

    @Operation(summary = "질문 작성", description = "구매 여부 무관, 인증된 사용자만 작성 가능.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QnaResponse> create(
            @PathVariable Long productId,
            @CurrentUserId Long userId,
            @RequestBody @Valid QnaCreateRequest request) {
        return ApiResponse.ok(qnaService.create(productId, userId, request));
    }

    @Operation(summary = "답변 작성", description = "ADMIN 전용. 기존 답변이 있으면 덮어쓴다.")
    @PostMapping("/{qnaId}/answer")
    public ApiResponse<QnaResponse> answer(
            @PathVariable Long productId,
            @PathVariable Long qnaId,
            @CurrentUserId Long userId,
            @RequestBody @Valid QnaAnswerRequest request) {
        return ApiResponse.ok(qnaService.answer(productId, qnaId, userId, request));
    }

    @Operation(summary = "Q&A 삭제", description = "작성자 본인 또는 ADMIN만 삭제 가능.")
    @DeleteMapping("/{qnaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long qnaId,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        qnaService.delete(productId, qnaId, userId, isAdmin);
    }
}
