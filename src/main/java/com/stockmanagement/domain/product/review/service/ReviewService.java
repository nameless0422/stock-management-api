package com.stockmanagement.domain.product.review.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.review.dto.ReviewCreateRequest;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.dto.ReviewUpdateRequest;
import com.stockmanagement.domain.product.review.entity.Review;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * 리뷰를 작성한다.
     *
     * <p>실구매자(CONFIRMED 주문 보유) + 상품당 1인 1리뷰 제약을 검증한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ReviewResponse create(Long productId, String username, ReviewCreateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!orderRepository.existsPurchaseByUserIdAndProductId(user.getId(), product.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED);
        }
        if (reviewRepository.existsByProductIdAndUserId(product.getId(), user.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .productId(product.getId())
                .userId(user.getId())
                .rating(request.getRating())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    /** 상품의 리뷰 목록을 최신순 페이징 조회한다. */
    public Page<ReviewResponse> getList(Long productId, Pageable pageable) {
        productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(ReviewResponse::from);
    }

    /** 리뷰를 수정한다. 작성자 본인만 가능하다. */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ReviewResponse update(Long productId, Long reviewId, String username, ReviewUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        review.update(request.getRating(), request.getTitle(), request.getContent());
        return ReviewResponse.from(review);
    }

    /** 리뷰를 삭제한다. 작성자 본인만 가능하다. */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public void delete(Long productId, Long reviewId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        reviewRepository.delete(review);
    }
}
