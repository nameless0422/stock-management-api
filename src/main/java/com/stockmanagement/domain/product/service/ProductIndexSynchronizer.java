package com.stockmanagement.domain.product.service;

import com.stockmanagement.domain.order.repository.OrderItemRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 상품 ES 색인 동기화 실행 컴포넌트.
 *
 * <p>DB에서 상품을 재로드하고 리뷰·판매 통계를 결합해 색인한다.
 * 실패 시 예외를 전파하므로 호출자가 재시도 정책을 결정한다:
 * <ul>
 *   <li>{@link ProductEventListener} — 동기 1차 시도, 실패 시 Outbox 저장
 *   <li>{@code OutboxEventProcessor} — Outbox 재시도 (최대 5회)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexSynchronizer {

    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 상품을 DB에서 재로드하여 통계와 함께 색인한다.
     * 상품이 존재하지 않으면 경고 로그만 남긴다 (색인 대상 없음 — 재시도 불필요).
     *
     * @throws RuntimeException ES 색인 실패 시 전파
     */
    public void sync(Long productId) {
        productRepository.findById(productId).ifPresentOrElse(
                product -> {
                    long reviewCount = reviewRepository.countByProductId(productId);
                    long salesCount = orderItemRepository.sumSalesCountByProductId(productId);
                    productSearchService.index(product, reviewCount, salesCount);
                },
                () -> log.warn("[ProductIndexSynchronizer] 색인 대상 상품 미존재. productId={}", productId)
        );
    }

    /**
     * 상품을 ES 색인에서 삭제한다.
     *
     * @throws RuntimeException ES 삭제 실패 시 전파
     */
    public void deleteFromIndex(Long productId) {
        productSearchService.deleteFromIndex(productId);
    }
}
