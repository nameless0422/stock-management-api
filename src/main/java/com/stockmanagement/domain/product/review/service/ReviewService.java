package com.stockmanagement.domain.product.review.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.repository.OrderRepository;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * 리뷰를 작성한다.
     *
     * <p>실구매자(CONFIRMED 주문 보유) + 상품당 1인 1리뷰 제약을 검증한다.
     * userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ReviewResponse create(Long productId, Long userId, ReviewCreateRequest request) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!orderRepository.existsPurchaseByUserIdAndProductId(userId, productId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED);
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .productId(productId)
                .userId(userId)
                .rating(request.getRating())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    /** 상품의 리뷰 목록을 최신순 페이징 조회한다. 작성자 username을 마스킹하여 포함한다. */
    public Page<ReviewResponse> getList(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Page<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        Map<Long, String> usernameMap = buildUsernameMap(reviews.map(Review::getUserId).toList());
        return reviews.map(r -> ReviewResponse.from(r, usernameMap.get(r.getUserId())));
    }

    /** 특정 사용자의 리뷰 목록을 페이징 조회한다. 별점 필터 지원. */
    public Page<ReviewResponse> getMyReviews(Long userId, Pageable pageable, Integer rating) {
        Page<Review> reviews = (rating != null)
                ? reviewRepository.findByUserIdAndRating(userId, rating, pageable)
                : reviewRepository.findByUserId(userId, pageable);
        return reviews.map(ReviewResponse::from);
    }

    /**
     * 리뷰를 수정한다. 작성자 본인만 가능하다.
     *
     * <p>userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ReviewResponse update(Long productId, Long reviewId, Long userId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        review.update(request.getRating(), request.getTitle(), request.getContent());
        return ReviewResponse.from(review);
    }

    /**
     * 리뷰를 삭제한다. 작성자 본인만 가능하다.
     *
     * <p>userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public void delete(Long productId, Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        reviewRepository.delete(review);
    }

    // ===== 내부 헬퍼 =====

    /** userId 목록으로 username Map을 배치 조회한다 (N+1 방지). */
    private Map<Long, String> buildUsernameMap(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return StreamSupport.stream(userRepository.findAllById(userIds).spliterator(), false)
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }
}
