package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;

import java.util.Map;

/**
 * 상품 Elasticsearch 동기화 이벤트.
 *
 * <p>{@code delete=false}: 상품 색인(create/update/changeStatus→ACTIVE)
 * <p>{@code delete=true}: 색인 삭제(delete/changeStatus→DISCONTINUED)
 *
 * <p>트랜잭션 커밋 이후에 발행되도록 {@link ProductEventListener}에서
 * {@code @TransactionalEventListener(AFTER_COMMIT)}으로 처리한다.
 *
 * <p>동기 색인 실패 시에만 Outbox({@code PRODUCT_SYNC})에 저장되어
 * 릴레이 스케줄러가 최대 5회 재시도한다.
 */
public class ProductSyncEvent extends DomainEvent implements OutboxSupport {

    private final Long productId;
    private final boolean delete;

    public ProductSyncEvent(Long productId, boolean delete) {
        super();
        this.productId = productId;
        this.delete = delete;
    }

    public Long getProductId() {
        return productId;
    }

    public boolean isDelete() {
        return delete;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.PRODUCT_SYNC;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("productId", productId, "delete", delete);
    }
}
