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
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductQnaService 단위 테스트")
class ProductQnaServiceTest {

    @Mock private ProductQnaRepository qnaRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProductQnaService qnaService;

    private ProductQna createQna(Long id, Long productId, Long userId, String content, boolean secret) {
        ProductQna qna = ProductQna.builder()
                .productId(productId)
                .userId(userId)
                .content(content)
                .secret(secret)
                .build();
        ReflectionTestUtils.setField(qna, "id", id);
        return qna;
    }

    private User createUser(Long id, String username) {
        User user = User.builder()
                .username(username)
                .password("encoded")
                .email(username + "@test.com")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ===== create() =====

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("정상 작성 — Q&A 저장 후 응답 반환")
        void createsQna() {
            QnaCreateRequest request = new QnaCreateRequest();
            ReflectionTestUtils.setField(request, "content", "배송 기간 문의");
            ReflectionTestUtils.setField(request, "secret", false);

            given(productRepository.existsById(1L)).willReturn(true);
            given(qnaRepository.save(any(ProductQna.class))).willAnswer(inv -> {
                ProductQna qna = inv.getArgument(0);
                ReflectionTestUtils.setField(qna, "id", 10L);
                return qna;
            });
            given(userRepository.findById(1L)).willReturn(Optional.of(createUser(1L, "buyer")));

            QnaResponse response = qnaService.create(1L, 1L, request);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getContent()).isEqualTo("배송 기간 문의");
            assertThat(response.isSecret()).isFalse();
            verify(qnaRepository).save(any(ProductQna.class));
        }

        @Test
        @DisplayName("상품 미존재 — PRODUCT_NOT_FOUND")
        void throwsWhenProductNotFound() {
            QnaCreateRequest request = new QnaCreateRequest();
            ReflectionTestUtils.setField(request, "content", "문의");

            given(productRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> qnaService.create(999L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        }
    }

    // ===== getList() =====

    @Nested
    @DisplayName("getList()")
    class GetList {

        @Test
        @DisplayName("정상 조회 — 커서 페이지 반환")
        void returnsPaged() {
            ProductQna qna = createQna(1L, 1L, 10L, "문의합니다", false);
            given(productRepository.existsById(1L)).willReturn(true);
            given(qnaRepository.findByProductIdOrderByIdDesc(any(), any()))
                    .willReturn(List.of(qna));
            given(userRepository.findAllById(List.of(10L)))
                    .willReturn(List.of(createUser(10L, "buyer")));

            CursorPage<QnaResponse> result = qnaService.getList(1L, null, 20, null, false);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getContent()).isEqualTo("문의합니다");
        }

        @Test
        @DisplayName("비밀글 — 타인이면 마스킹 처리")
        void masksSecretForOthers() {
            ProductQna qna = createQna(1L, 1L, 10L, "비밀 내용", true);
            given(productRepository.existsById(1L)).willReturn(true);
            given(qnaRepository.findByProductIdOrderByIdDesc(any(), any()))
                    .willReturn(List.of(qna));
            given(userRepository.findAllById(List.of(10L)))
                    .willReturn(List.of(createUser(10L, "buyer")));

            CursorPage<QnaResponse> result = qnaService.getList(1L, null, 20, 99L, false);

            assertThat(result.getContent().get(0).getContent()).isEqualTo("비밀글입니다");
        }

        @Test
        @DisplayName("비밀글 — 작성자 본인이면 원문 노출")
        void showsSecretToAuthor() {
            ProductQna qna = createQna(1L, 1L, 10L, "비밀 내용", true);
            given(productRepository.existsById(1L)).willReturn(true);
            given(qnaRepository.findByProductIdOrderByIdDesc(any(), any()))
                    .willReturn(List.of(qna));
            given(userRepository.findAllById(List.of(10L)))
                    .willReturn(List.of(createUser(10L, "buyer")));

            CursorPage<QnaResponse> result = qnaService.getList(1L, null, 20, 10L, false);

            assertThat(result.getContent().get(0).getContent()).isEqualTo("비밀 내용");
        }

        @Test
        @DisplayName("비밀글 — ADMIN이면 원문 노출")
        void showsSecretToAdmin() {
            ProductQna qna = createQna(1L, 1L, 10L, "비밀 내용", true);
            given(productRepository.existsById(1L)).willReturn(true);
            given(qnaRepository.findByProductIdOrderByIdDesc(any(), any()))
                    .willReturn(List.of(qna));
            given(userRepository.findAllById(List.of(10L)))
                    .willReturn(List.of(createUser(10L, "buyer")));

            CursorPage<QnaResponse> result = qnaService.getList(1L, null, 20, 99L, true);

            assertThat(result.getContent().get(0).getContent()).isEqualTo("비밀 내용");
        }
    }

    // ===== answer() =====

    @Nested
    @DisplayName("answer()")
    class Answer {

        @Test
        @DisplayName("정상 답변 — answer/answeredBy 설정")
        void answersQna() {
            ProductQna qna = createQna(1L, 1L, 10L, "문의", false);
            QnaAnswerRequest request = new QnaAnswerRequest();
            ReflectionTestUtils.setField(request, "answer", "답변 드립니다");

            given(qnaRepository.findById(1L)).willReturn(Optional.of(qna));
            given(userRepository.findById(10L)).willReturn(Optional.of(createUser(10L, "buyer")));

            QnaResponse response = qnaService.answer(1L, 1L, 100L, request);

            assertThat(response.isAnswered()).isTrue();
            assertThat(response.getAnswer()).isEqualTo("답변 드립니다");
            assertThat(qna.getAnsweredBy()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Q&A 미존재 — QNA_NOT_FOUND")
        void throwsWhenNotFound() {
            QnaAnswerRequest request = new QnaAnswerRequest();
            ReflectionTestUtils.setField(request, "answer", "답변");

            given(qnaRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> qnaService.answer(1L, 999L, 100L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.QNA_NOT_FOUND));
        }
    }

    // ===== delete() =====

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("작성자 삭제 — 정상 처리")
        void deletesOwnQna() {
            ProductQna qna = createQna(1L, 1L, 10L, "문의", false);
            given(qnaRepository.findById(1L)).willReturn(Optional.of(qna));

            qnaService.delete(1L, 1L, 10L, false);

            verify(qnaRepository).delete(qna);
        }

        @Test
        @DisplayName("ADMIN 삭제 — 타인 Q&A 삭제 가능")
        void adminDeletesOthersQna() {
            ProductQna qna = createQna(1L, 1L, 10L, "문의", false);
            given(qnaRepository.findById(1L)).willReturn(Optional.of(qna));

            qnaService.delete(1L, 1L, 100L, true);

            verify(qnaRepository).delete(qna);
        }

        @Test
        @DisplayName("타인 삭제 — QNA_ACCESS_DENIED")
        void throwsWhenNotOwner() {
            ProductQna qna = createQna(1L, 1L, 10L, "문의", false);
            given(qnaRepository.findById(1L)).willReturn(Optional.of(qna));

            assertThatThrownBy(() -> qnaService.delete(1L, 1L, 99L, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.QNA_ACCESS_DENIED));
        }
    }
}
