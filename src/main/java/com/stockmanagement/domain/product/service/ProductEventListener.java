package com.stockmanagement.domain.product.service;

import com.stockmanagement.common.event.ProductSyncEvent;
import com.stockmanagement.common.outbox.OutboxEventStore;
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
 *
 * <p>동기 색인 실패 시 이벤트를 Outbox({@code PRODUCT_SYNC})에 저장하여
 * 릴레이 스케줄러가 최대 5회 재시도한다 — ES 일시 장애가 영구 불일치로 남지 않는다.
 * AFTER_COMMIT 컨텍스트에서는 이미 커밋된 트랜잭션에 참여하면 INSERT가 유실되므로
 * {@link OutboxEventStore#saveInNewTransaction}(REQUIRES_NEW)을 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductIndexSynchronizer indexSynchronizer;
    private final OutboxEventStore outboxEventStore;

    /**
     * 상품 변경이 DB에 커밋된 후 ES 색인을 동기화한다.
     *
     * <p>이벤트가 {@code delete=true}이면 색인 삭제, {@code false}이면 DB에서 재로드하여 색인.
     * DB 재로드 이유: 커밋 이후 최신 데이터 보장 + Lazy 관계 안전 접근.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductSync(ProductSyncEvent event) {
        try {
            if (event.isDelete()) {
                indexSynchronizer.deleteFromIndex(event.getProductId());
            } else {
                indexSynchronizer.sync(event.getProductId());
            }
        } catch (Exception e) {
            log.error("[ProductEventListener] ES 색인 동기화 실패 — Outbox 재시도 위임. productId={}, delete={}",
                    event.getProductId(), event.isDelete(), e);
            enqueueRetry(event);
        }
    }

    /** 색인 실패 이벤트를 Outbox에 저장한다. 저장마저 실패하면 로그만 남긴다 (DB 장애 등). */
    private void enqueueRetry(ProductSyncEvent event) {
        try {
            outboxEventStore.saveInNewTransaction(event);
        } catch (Exception e) {
            log.error("[ProductEventListener] Outbox 저장 실패 — DB/ES 불일치 발생, 재색인 API로 복구 필요. productId={}",
                    event.getProductId(), e);
        }
    }
}
