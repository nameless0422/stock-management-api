package com.stockmanagement.domain.product.service;

import com.stockmanagement.common.event.ProductSyncEvent;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 도메인 이벤트 리스너.
 *
 * <p>DB 트랜잭션 커밋 이후 동일 스레드에서 Elasticsearch 동기화를 수행한다.
 * AFTER_COMMIT으로 커밋된 데이터만 ES에 반영하며, ES 장애가 원본 트랜잭션에 영향을 주지 않는다.
 *
 * <p>비동기(@Async)를 사용하지 않는 이유: 검색 색인은 요청 스레드에서 완료되어야 통합 테스트에서
 * 인덱스 refresh 타이밍 문제 없이 즉시 검증 가능하며, 응답 시간 영향은 미미하다 (~10–50 ms).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;

    /**
     * 상품 변경이 DB에 커밋된 후 ES 색인을 동기화한다.
     *
     * <p>이벤트가 {@code delete=true}이면 색인 삭제, {@code false}이면 DB에서 재로드하여 색인.
     * DB 재로드 이유: 커밋 이후 최신 데이터 보장 + Lazy 관계 안전 접근.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductSync(ProductSyncEvent event) {
        if (event.isDelete()) {
            safeDeleteFromIndex(event.getProductId());
        } else {
            syncToEs(event.getProductId());
        }
    }

    private void syncToEs(Long productId) {
        try {
            productRepository.findById(productId).ifPresentOrElse(
                    product -> {
                        productSearchService.index(product);
                        log.debug("[ProductEventListener] ES 색인 완료. productId={}", productId);
                    },
                    () -> log.warn("[ProductEventListener] ES 색인 대상 상품 미존재. productId={}", productId)
            );
        } catch (Exception e) {
            log.error("[ProductEventListener] ES 색인 실패 — DB/ES 불일치 발생. productId={}", productId, e);
        }
    }

    private void safeDeleteFromIndex(Long productId) {
        try {
            productSearchService.deleteFromIndex(productId);
        } catch (Exception e) {
            log.error("[ProductEventListener] ES 색인 삭제 실패 — 삭제된 상품이 검색에 잔존할 수 있음. productId={}", productId, e);
        }
    }
}
