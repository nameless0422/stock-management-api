package com.stockmanagement.integration;

import com.stockmanagement.common.exception.InsufficientStockException;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 재고 동시성 통합 테스트.
 *
 * <p>분산 락(Redisson) + 비관적 락(SELECT FOR UPDATE) 2중 제어가
 * 실제 MySQL + Redis 컨테이너 환경에서 재고 초과 예약을 방지하는지 검증한다.
 *
 * <p>JaCoCo 라인 커버리지는 {@code <} 를 {@code <=}로 바꿔도 동일하게 통과하지만,
 * 이 테스트는 경계값 상황(동시 요청 수 > 재고)에서 실제로 실패하는지 확인한다.
 */
@DisplayName("재고 동시성 통합 테스트")
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired InventoryService inventoryService;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired ProductRepository productRepository;

    /**
     * 재고 10개 상품에 20개 스레드가 동시에 각 1개 예약 시도.
     * 성공 건수는 10 이하이며, 최종 reserved + available = onHand를 유지해야 한다.
     */
    @Test
    @DisplayName("동시 reserve() 20개 → 재고 10개 이하 성공, 초과분 예외 — 재고 불변 조건 유지")
    void concurrentReserve_doesNotExceedStock() throws Exception {
        // ===== 준비: 상품 + 재고 직접 생성 (HTTP 불필요, 서비스 레이어 직접 호출) =====
        Product product = productRepository.save(Product.builder()
                .name("동시성 테스트 상품")
                .sku("SKU-CONCURRENT-001")
                .price(BigDecimal.valueOf(10000))
                .build());

        int stockQty = 10;
        // Inventory.builder()는 product만 받고 onHand=0 초기화 → receive()로 재고 추가
        Inventory inventory = Inventory.builder().product(product).build();
        inventory.receive(stockQty);
        inventoryRepository.save(inventory);

        long productId = product.getId();

        // ===== 동시 실행 =====
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount); // 모든 스레드 준비 대기
        CountDownLatch startLatch = new CountDownLatch(1);           // 일제히 시작 신호

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 일제히 시작
                    inventoryService.reserve(productId, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        readyLatch.await(); // 모든 스레드 준비 완료
        startLatch.countDown(); // 동시 출발

        for (Future<?> f : futures) {
            f.get(); // 모든 스레드 종료 대기
        }
        executor.shutdown();

        // ===== 검증 =====
        int succeeded = successCount.get();
        int failed    = failCount.get();

        // 성공 + 실패 = 전체 스레드
        assertThat(succeeded + failed).isEqualTo(threadCount);

        // 성공 건수는 재고 이하여야 한다 (락 없으면 20개 모두 성공 → 재고 음수 발생)
        assertThat(succeeded)
                .as("동시 예약 성공 건수는 재고(%d) 이하여야 한다", stockQty)
                .isLessThanOrEqualTo(stockQty);

        // 실패 건수는 최소 (threadCount - stockQty)개 이상
        assertThat(failed)
                .as("재고 초과분은 InsufficientStockException으로 실패해야 한다")
                .isGreaterThanOrEqualTo(threadCount - stockQty);

        // DB 최종 상태: reserved == 성공 건수, available >= 0 (불변 조건)
        Inventory finalState = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(finalState.getReserved())
                .as("최종 reserved는 성공한 예약 건수와 일치해야 한다")
                .isEqualTo(succeeded);
        assertThat(finalState.getAvailable())
                .as("available은 음수가 되어서는 안 된다")
                .isGreaterThanOrEqualTo(0);
        assertThat(finalState.getOnHand())
                .as("onHand는 변하지 않아야 한다 (reserve는 reserved만 변경)")
                .isEqualTo(stockQty);
    }

    /**
     * 재고 딱 맞게 예약 후 추가 예약 시도 → 남은 재고 없음 예외.
     * 경계값 테스트: available이 정확히 0일 때 1개 추가 시도.
     */
    @Test
    @DisplayName("재고 소진 후 추가 예약 시도 → InsufficientStockException")
    void reserveWhenNoStock_throwsException() {
        Product product = productRepository.save(Product.builder()
                .name("재고소진 테스트 상품")
                .sku("SKU-ZERO-001")
                .price(BigDecimal.valueOf(5000))
                .build());

        Inventory inventory = Inventory.builder().product(product).build();
        inventory.receive(2);
        inventoryRepository.save(inventory);

        long productId = product.getId();

        // 2개 정상 예약
        inventoryService.reserve(productId, 1);
        inventoryService.reserve(productId, 1);

        // 재고 소진 상태에서 1개 추가 시도 → 예외 발생 확인
        assertThatThrownBy(() -> inventoryService.reserve(productId, 1))
                .isInstanceOf(InsufficientStockException.class);

        // available = 0 유지
        Inventory finalState = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(finalState.getAvailable()).isZero();
        assertThat(finalState.getReserved()).isEqualTo(2);
    }
}
