package com.stockmanagement.integration;

import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import com.stockmanagement.domain.coupon.repository.CouponRepository;
import com.stockmanagement.domain.coupon.scheduler.CouponExpiryScheduler;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.scheduler.InventorySnapshotScheduler;
import com.stockmanagement.domain.order.repository.DailyOrderStatsRepository;
import com.stockmanagement.domain.order.scheduler.DailyOrderStatsScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배치 스케줄러 통합 테스트.
 *
 * <p>{@code @TestPropertySource}로 스케줄러를 활성화하고 메서드를 직접 호출하여 실제 DB 반영을 검증한다.
 * 각 시나리오에서 {@code @AfterEach cleanUp()}이 테이블을 초기화하므로 테스트 간 격리가 보장된다.
 */
@TestPropertySource(properties = {
        "coupon.expiry.enabled=true",
        "inventory.snapshot.enabled=true",
        "order.stats.enabled=true"
})
@DisplayName("배치 스케줄러 통합 테스트")
class BatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CouponExpiryScheduler couponExpiryScheduler;
    @Autowired private InventorySnapshotScheduler inventorySnapshotScheduler;
    @Autowired private DailyOrderStatsScheduler dailyOrderStatsScheduler;

    @Autowired private CouponRepository couponRepository;
    @Autowired private DailyInventorySnapshotRepository snapshotRepository;
    @Autowired private DailyOrderStatsRepository statsRepository;

    // ===== 시나리오 1: 쿠폰 만료 비활성화 =====

    @Test
    @DisplayName("만료된 활성 쿠폰 → deactivateExpiredCoupons() → active=false 전환")
    void couponExpiry_deactivatesExpiredCoupons() {
        // given: validUntil이 과거인 쿠폰 저장
        Coupon expired = Coupon.builder()
                .code("EXPIRED_" + System.currentTimeMillis())
                .name("만료 쿠폰")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusMinutes(1))
                .maxUsageCount(100)
                .maxUsagePerUser(1)
                .build();
        couponRepository.save(expired);

        // when
        couponExpiryScheduler.deactivateExpiredCoupons();

        // then
        Coupon result = couponRepository.findById(expired.getId()).orElseThrow();
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("유효한 쿠폰 → deactivateExpiredCoupons() → active 유지")
    void couponExpiry_keepsValidCouponsActive() {
        // given: validUntil이 미래인 쿠폰
        Coupon valid = Coupon.builder()
                .code("VALID_" + System.currentTimeMillis())
                .name("유효 쿠폰")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxUsageCount(100)
                .maxUsagePerUser(1)
                .build();
        couponRepository.save(valid);

        // when
        couponExpiryScheduler.deactivateExpiredCoupons();

        // then
        Coupon result = couponRepository.findById(valid.getId()).orElseThrow();
        assertThat(result.isActive()).isTrue();
    }

    // ===== 시나리오 2: 재고 스냅샷 =====

    @Test
    @DisplayName("재고 존재 시 takeSnapshot() → daily_inventory_snapshots 레코드 저장")
    void inventorySnapshot_savesSnapshotForExistingInventory() throws Exception {
        // given: 상품 + 재고 입고
        String adminToken = createAdminAndLogin("snap_admin", "password!", "snapadmin@test.com");
        createProductAndReceive(adminToken, "SNAP_SKU_" + System.currentTimeMillis(), 10000, 50);

        long beforeCount = snapshotRepository.count();

        // when
        inventorySnapshotScheduler.takeSnapshot();

        // then: 스냅샷 1건 이상 저장됨
        assertThat(snapshotRepository.count()).isGreaterThan(beforeCount);
    }

    @Test
    @DisplayName("동일 날짜 재실행 → 스냅샷 중복 저장 없음 (멱등성)")
    void inventorySnapshot_idempotentOnRerun() throws Exception {
        // given
        String adminToken = createAdminAndLogin("snap2_admin", "password!", "snap2admin@test.com");
        createProductAndReceive(adminToken, "SNAP2_SKU_" + System.currentTimeMillis(), 5000, 20);

        // when: 두 번 실행
        inventorySnapshotScheduler.takeSnapshot();
        long countAfterFirst = snapshotRepository.count();
        inventorySnapshotScheduler.takeSnapshot();
        long countAfterSecond = snapshotRepository.count();

        // then: 두 번째 실행에서 추가 저장 없음
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    // ===== 시나리오 3: 일별 주문 통계 =====

    @Test
    @DisplayName("aggregateDailyStats() → daily_order_stats 레코드 저장")
    void dailyOrderStats_savesRecord() {
        // when: 전일 주문이 없어도 레코드는 생성됨
        dailyOrderStatsScheduler.aggregateDailyStats();

        // then
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(statsRepository.findByStatDate(yesterday)).isPresent();
    }

    @Test
    @DisplayName("aggregateDailyStats() 재실행 → 기존 레코드 update (멱등성)")
    void dailyOrderStats_idempotentOnRerun() {
        // when
        dailyOrderStatsScheduler.aggregateDailyStats();
        dailyOrderStatsScheduler.aggregateDailyStats();

        // then: 어제 날짜 레코드 1건만 존재
        LocalDate yesterday = LocalDate.now().minusDays(1);
        long count = statsRepository.findByStatDateBetweenOrderByStatDateDesc(yesterday, yesterday).size();
        assertThat(count).isEqualTo(1);
    }

    // ===== 헬퍼 =====

    private void createProductAndReceive(String adminToken, String sku, int price, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"배치_상품_%s\",\"sku\":\"%s\",\"price\":%d}",
                                sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
    }
}
