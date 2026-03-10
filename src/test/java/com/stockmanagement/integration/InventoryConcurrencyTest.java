package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재고 동시성 테스트.
 *
 * <p>Redisson 분산 락은 Mock(항상 획득 성공)으로 대체하므로,
 * 실질적인 동시성 제어는 DB 레벨 비관적 락({@code PESSIMISTIC_WRITE})이 담당한다.
 * 멀티스레드 환경에서 lost update 없이 재고가 정확히 반영되는지 검증한다.
 *
 * <p>주의: 첫 번째 입고 시 inventory 레코드가 없으면 여러 스레드가 동시에
 * INSERT를 시도해 unique constraint 위반이 발생한다. 분산 락이 이를 방지하지만
 * 테스트에서는 Mock으로 대체되므로, 동시성 테스트 전 초기 입고(1건)로
 * inventory 레코드를 미리 생성해둔다.
 */
@DisplayName("재고 동시성 테스트")
class InventoryConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 10;

    // ===== 헬퍼 =====

    /** 상품을 등록하고 초기 입고(seedQuantity)를 수행해 inventory 레코드를 생성한다. */
    private long setupProduct(String adminToken, String sku, int seedQuantity) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"동시성상품\",\"sku\":\"%s\",\"price\":1000}", sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(body).path("data").path("id").asLong();

        // inventory 레코드 사전 생성 — 동시성 테스트에서 첫 INSERT 경합 방지
        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + seedQuantity + "}"))
                .andExpect(status().isOk());

        return productId;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * 태스크를 THREAD_COUNT개 스레드에서 동시에 실행한다.
     * startLatch.countDown()으로 모든 스레드가 동시에 출발하도록 동기화한다.
     * 스레드 중 예외가 발생하면 assertions로 실패시킨다.
     */
    private void runConcurrently(ThrowingRunnable task) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(THREAD_COUNT);
        List<Throwable> errors    = new CopyOnWriteArrayList<>();
        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.run();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 출발
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("모든 스레드가 30초 내 완료돼야 한다").isTrue();
        assertThat(errors).as("스레드 실행 중 예외: %s", errors).isEmpty();
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("동시 입고 10건 → lost update 없이 onHand 정확히 반영 (비관적 락 검증)")
    void concurrentReceive_noLostUpdate() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        // seedQuantity=5로 inventory 레코드 생성, 이후 동시 입고 10건(각 1)
        long productId = setupProduct(adminToken, "SKU-CONC-R", 5);

        runConcurrently(() ->
                mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1}"))
                        .andExpect(status().isOk())
        );

        // 최종 onHand = seed(5) + 동시 입고(10 × 1) = 15
        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHand").value(15));
    }

    @Test
    @DisplayName("동시 예약 10건 — 가용 재고(5) 초과분은 409, overselling 없음")
    void concurrentReserve_partialSuccess_noOverselling() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = setupProduct(adminToken, "SKU-CONC-S", 5); // 가용 재고 5

        String userToken = signupAndLogin("buyer", "buyerpass1", "buyer@example.com");
        long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        CountDownLatch startLatch  = new CountDownLatch(1);
        CountDownLatch doneLatch   = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor   = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String orderJson = String.format(
                            "{\"userId\":%d,\"idempotencyKey\":\"conc-key-%d\"," +
                            "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":1000}]}",
                            buyerId, idx, productId);

                    int httpStatus = mockMvc.perform(post("/api/orders")
                                    .header("Authorization", "Bearer " + userToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(orderJson))
                            .andReturn().getResponse().getStatus();

                    if (httpStatus == 201) successCount.incrementAndGet();
                    else                   failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("모든 스레드가 30초 내 완료돼야 한다").isTrue();

        // 성공 5건, 실패 5건
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // 최종 재고: reserved=5, available=0 — overselling 없음
        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserved").value(5))
                .andExpect(jsonPath("$.data.available").value(0));
    }
}
