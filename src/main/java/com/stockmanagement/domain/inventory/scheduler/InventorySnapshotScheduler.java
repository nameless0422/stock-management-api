package com.stockmanagement.domain.inventory.scheduler;

import com.stockmanagement.domain.inventory.entity.DailyInventorySnapshot;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 일별 재고 스냅샷 스케줄러.
 *
 * <p>매일 자정 5분에 전체 재고 현황을 {@code daily_inventory_snapshots}에 저장한다.
 * 동일 재고·날짜 조합이 이미 존재하면 스킵하여 멱등성을 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.snapshot.enabled", havingValue = "true", matchIfMissing = true)
public class InventorySnapshotScheduler {

    private final InventoryRepository inventoryRepository;
    private final DailyInventorySnapshotRepository snapshotRepository;

    @Scheduled(cron = "${inventory.snapshot.cron:0 5 0 * * *}")
    @Transactional
    public void takeSnapshot() {
        LocalDate today = LocalDate.now();
        List<Inventory> inventories = inventoryRepository.findAll();
        // 오늘 이미 스냅샷이 존재하는 inventoryId를 한 번에 조회하여 N+1 방지
        Set<Long> alreadySnapped = snapshotRepository.findInventoryIdsBySnapshotDate(today);
        log.info("[InventorySnapshotScheduler] 재고 스냅샷 시작 — 대상: {}건, 기준일: {}", inventories.size(), today);

        int saved = 0;
        int skipped = 0;
        for (Inventory inv : inventories) {
            try {
                if (alreadySnapped.contains(inv.getId())) {
                    skipped++;
                    continue;
                }
                DailyInventorySnapshot snapshot = DailyInventorySnapshot.builder()
                        .inventory(inv)
                        .snapshotDate(today)
                        .onHand(inv.getOnHand())
                        .reserved(inv.getReserved())
                        .allocated(inv.getAllocated())
                        .available(inv.getAvailable())
                        .build();
                snapshotRepository.save(snapshot);
                saved++;
            } catch (Exception e) {
                log.error("[InventorySnapshotScheduler] 스냅샷 저장 실패 — inventoryId={}, error={}",
                        inv.getId(), e.getMessage());
            }
        }
        log.info("[InventorySnapshotScheduler] 완료 — 저장: {}건 / 스킵: {}건", saved, skipped);
    }
}
