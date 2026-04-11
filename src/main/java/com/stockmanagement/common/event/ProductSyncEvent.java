package com.stockmanagement.common.event;

/**
 * 상품 Elasticsearch 동기화 이벤트.
 *
 * <p>{@code delete=false}: 상품 색인(create/update/changeStatus→ACTIVE)
 * <p>{@code delete=true}: 색인 삭제(delete/changeStatus→DISCONTINUED)
 *
 * <p>트랜잭션 커밋 이후에 발행되도록 {@link ProductEventListener}에서
 * {@code @TransactionalEventListener(AFTER_COMMIT)}으로 처리한다.
 */
public class ProductSyncEvent extends DomainEvent {

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
}
