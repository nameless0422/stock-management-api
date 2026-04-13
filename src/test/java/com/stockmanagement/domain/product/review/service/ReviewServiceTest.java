package com.stockmanagement.domain.product.review.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.review.dto.ReviewCreateRequest;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.entity.Review;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private ReviewService reviewService;

    private Product mockProduct(Long id) {
        Product p = Product.builder().name("테스트상품").price(java.math.BigDecimal.valueOf(10000)).sku("SKU-001").build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private ReviewCreateRequest request() {
        ReviewCreateRequest r = new ReviewCreateRequest();
        ReflectionTestUtils.setField(r, "rating", 5);
        ReflectionTestUtils.setField(r, "title", "좋아요");
        ReflectionTestUtils.setField(r, "content", "아주 만족스럽습니다.");
        return r;
    }

    @Nested
    @DisplayName("create() — 리뷰 작성")
    class Create {

        @Test
        @DisplayName("정상 작성 → 저장 호출")
        void success() {
            given(productRepository.existsById(1L)).willReturn(true);
            given(orderRepository.existsPurchaseByUserIdAndProductId(2L, 1L)).willReturn(true);
            given(reviewRepository.existsByProductIdAndUserId(1L, 2L)).willReturn(false);

            Review saved = Review.builder().productId(1L).userId(2L).rating(5).title("좋아요").content("만족").build();
            ReflectionTestUtils.setField(saved, "id", 10L);
            given(reviewRepository.save(any())).willReturn(saved);

            ReviewResponse response = reviewService.create(1L, 2L, request());

            assertThat(response.getRating()).isEqualTo(5);
            verify(reviewRepository).save(any());
        }

        @Test
        @DisplayName("미구매 상품 → REVIEW_NOT_PURCHASED")
        void notPurchased() {
            given(productRepository.existsById(1L)).willReturn(true);
            given(orderRepository.existsPurchaseByUserIdAndProductId(2L, 1L)).willReturn(false);

            assertThatThrownBy(() -> reviewService.create(1L, 2L, request()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_PURCHASED);
        }

        @Test
        @DisplayName("이미 리뷰 존재 → REVIEW_ALREADY_EXISTS")
        void alreadyExists() {
            given(productRepository.existsById(1L)).willReturn(true);
            given(orderRepository.existsPurchaseByUserIdAndProductId(2L, 1L)).willReturn(true);
            given(reviewRepository.existsByProductIdAndUserId(1L, 2L)).willReturn(true);

            assertThatThrownBy(() -> reviewService.create(1L, 2L, request()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("getList() — 리뷰 목록")
    class GetList {

        @Test
        @DisplayName("정상 조회 → 페이지 반환")
        void success() {
            given(productRepository.existsById(1L)).willReturn(true);
            Review r = Review.builder().productId(1L).userId(2L).rating(4).title("보통").content("그냥 그래요").build();
            ReflectionTestUtils.setField(r, "id", 5L);
            given(reviewRepository.findByProductIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(r)));

            Page<ReviewResponse> page = reviewService.getList(1L, Pageable.unpaged());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getRating()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("delete() — 리뷰 삭제")
    class Delete {

        @Test
        @DisplayName("작성자 삭제 성공")
        void success() {
            Review r = Review.builder().productId(1L).userId(2L).rating(5).title("좋아요").content("최고").build();
            ReflectionTestUtils.setField(r, "id", 10L);
            given(reviewRepository.findById(10L)).willReturn(Optional.of(r));

            reviewService.delete(1L, 10L, 2L);

            verify(reviewRepository).delete(r);
        }

        @Test
        @DisplayName("타인 리뷰 삭제 시도 → REVIEW_ACCESS_DENIED")
        void accessDenied() {
            Review r = Review.builder().productId(1L).userId(2L).rating(5).title("좋아요").content("최고").build();
            ReflectionTestUtils.setField(r, "id", 10L);
            given(reviewRepository.findById(10L)).willReturn(Optional.of(r));

            assertThatThrownBy(() -> reviewService.delete(1L, 10L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_ACCESS_DENIED);
        }
    }
}
