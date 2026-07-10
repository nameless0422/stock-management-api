package com.stockmanagement.domain.product.service;

import com.stockmanagement.common.event.ProductSyncEvent;
import com.stockmanagement.common.outbox.OutboxEventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductEventListener 단위 테스트")
class ProductEventListenerTest {

    @Mock ProductIndexSynchronizer indexSynchronizer;
    @Mock OutboxEventStore outboxEventStore;

    @InjectMocks ProductEventListener listener;

    @Test
    @DisplayName("색인 성공 → Outbox 저장 없음")
    void syncSuccess_noOutbox() {
        listener.onProductSync(new ProductSyncEvent(1L, false));

        verify(indexSynchronizer).sync(1L);
        verify(outboxEventStore, never()).saveInNewTransaction(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("색인 삭제 성공 → deleteFromIndex 호출, Outbox 저장 없음")
    void deleteSuccess_noOutbox() {
        listener.onProductSync(new ProductSyncEvent(2L, true));

        verify(indexSynchronizer).deleteFromIndex(2L);
        verify(outboxEventStore, never()).saveInNewTransaction(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("색인 실패 → Outbox에 PRODUCT_SYNC 이벤트 저장 (재시도 위임)")
    void syncFailure_savesOutbox() {
        doThrow(new RuntimeException("ES 연결 실패")).when(indexSynchronizer).sync(3L);
        ProductSyncEvent event = new ProductSyncEvent(3L, false);

        listener.onProductSync(event); // 예외를 전파하지 않는다

        ArgumentCaptor<ProductSyncEvent> captor = ArgumentCaptor.forClass(ProductSyncEvent.class);
        verify(outboxEventStore).saveInNewTransaction(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(3L);
        assertThat(captor.getValue().isDelete()).isFalse();
    }

    @Test
    @DisplayName("색인 삭제 실패 → Outbox에 delete=true 이벤트 저장")
    void deleteFailure_savesOutbox() {
        doThrow(new RuntimeException("ES 연결 실패")).when(indexSynchronizer).deleteFromIndex(4L);

        listener.onProductSync(new ProductSyncEvent(4L, true));

        ArgumentCaptor<ProductSyncEvent> captor = ArgumentCaptor.forClass(ProductSyncEvent.class);
        verify(outboxEventStore).saveInNewTransaction(captor.capture());
        assertThat(captor.getValue().isDelete()).isTrue();
    }

    @Test
    @DisplayName("Outbox 저장마저 실패 → 예외를 전파하지 않는다 (로그만)")
    void outboxFailure_swallowed() {
        doThrow(new RuntimeException("ES 연결 실패")).when(indexSynchronizer).sync(5L);
        doThrow(new RuntimeException("DB 연결 실패")).when(outboxEventStore)
                .saveInNewTransaction(org.mockito.ArgumentMatchers.any());

        listener.onProductSync(new ProductSyncEvent(5L, false)); // 예외 없이 종료
    }
}
