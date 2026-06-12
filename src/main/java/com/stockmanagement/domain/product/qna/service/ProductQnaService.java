package com.stockmanagement.domain.product.qna.service;

import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.qna.dto.QnaAnswerRequest;
import com.stockmanagement.domain.product.qna.dto.QnaCreateRequest;
import com.stockmanagement.domain.product.qna.dto.QnaResponse;
import com.stockmanagement.domain.product.qna.entity.ProductQna;
import com.stockmanagement.domain.product.qna.repository.ProductQnaRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductQnaService {

    private final ProductQnaRepository qnaRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /** Q&A 목록 조회 (비밀글 마스킹 포함, 커서 기반) */
    public CursorPage<QnaResponse> getList(Long productId, Long lastId, int size, Long currentUserId, boolean isAdmin) {
        validateProductExists(productId);
        PageRequest limit = PageRequest.of(0, size + 1);
        List<ProductQna> items = lastId == null
                ? qnaRepository.findByProductIdOrderByIdDesc(productId, limit)
                : qnaRepository.findByProductIdAndIdLessThanOrderByIdDesc(productId, lastId, limit);

        List<Long> userIds = items.stream()
                .map(ProductQna::getUserId)
                .distinct()
                .toList();
        Map<Long, String> usernameMap = buildUsernameMap(userIds);

        List<QnaResponse> responses = items.stream()
                .map(q -> QnaResponse.from(q, usernameMap.get(q.getUserId()), currentUserId, isAdmin))
                .toList();
        return CursorPage.of(responses, size, QnaResponse::getId);
    }

    /** 질문 작성 */
    @Transactional
    public QnaResponse create(Long productId, Long userId, QnaCreateRequest request) {
        validateProductExists(productId);
        ProductQna qna = ProductQna.builder()
                .productId(productId)
                .userId(userId)
                .content(request.getContent())
                .secret(request.isSecret())
                .build();
        qnaRepository.save(qna);

        String username = userRepository.findById(userId)
                .map(User::getUsername).orElse(null);
        return QnaResponse.from(qna, username, userId, false);
    }

    /** 답변 작성/수정 (ADMIN only) */
    @Transactional
    public QnaResponse answer(Long productId, Long qnaId, Long adminId, QnaAnswerRequest request) {
        ProductQna qna = findByIdAndValidateProduct(qnaId, productId);
        qna.answer(request.getAnswer(), adminId);

        String username = userRepository.findById(qna.getUserId())
                .map(User::getUsername).orElse(null);
        return QnaResponse.from(qna, username, adminId, true);
    }

    /** 삭제 (작성자 또는 ADMIN) */
    @Transactional
    public void delete(Long productId, Long qnaId, Long userId, boolean isAdmin) {
        ProductQna qna = findByIdAndValidateProduct(qnaId, productId);
        if (!isAdmin && !qna.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QNA_ACCESS_DENIED);
        }
        qnaRepository.delete(qna);
    }

    // ===== 내부 헬퍼 =====

    private void validateProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    private ProductQna findByIdAndValidateProduct(Long qnaId, Long productId) {
        ProductQna qna = qnaRepository.findById(qnaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QNA_NOT_FOUND));
        if (!qna.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.QNA_NOT_FOUND);
        }
        return qna;
    }

    private Map<Long, String> buildUsernameMap(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return StreamSupport.stream(userRepository.findAllById(userIds).spliterator(), false)
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }
}
