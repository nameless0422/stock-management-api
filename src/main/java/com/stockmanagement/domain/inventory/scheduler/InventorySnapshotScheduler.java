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

import org.springframework.dao.DataIntegrityViolationException;

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

        // 신규 항목만 필터링하여 엔티티 리스트 구성 → saveAll() 단일 배치 INSERT
        List<DailyInventorySnapshot> snapshots = inventories.stream()
                .filter(inv -> !alreadySnapped.contains(inv.getId()))
                .map(inv -> DailyInventorySnapshot.builder()
                        .inventory(inv)
                        .snapshotDate(today)
                        .onHand(inv.getOnHand())
                        .reserved(inv.getReserved())
                        .allocated(inv.getAllocated())
                        .available(inv.getAvailable())
                        .build())
                .toList();

        int skipped = inventories.size() - snapshots.size();
        try {
            snapshotRepository.saveAll(snapshots);
            log.info("[InventorySnapshotScheduler] 완료 — 저장: {}건 / 스킵: {}건", snapshots.size(), skipped);
        } catch (DataIntegrityViolationException e) {
            // 레이스 컨디션으로 인한 중복 스냅샷 — alreadySnapped 집합 조회 후 INSERT 사이에
            // 다른 인스턴스가 먼저 저장한 경우. 안전하게 스킵한다.
            log.debug("[InventorySnapshotScheduler] 중복 스냅샷 감지 (동시 실행 경합) — date={}", today);
        } catch (Exception e) {
            // 예상치 못한 오류 — 스케줄러가 error를 삼키면 다음 주기까지 알 수 없으므로 re-throw하여
            // Spring 스케줄러가 로그를 남기고 Prometheus/알림이 감지할 수 있도록 한다.
            log.error("[InventorySnapshotScheduler] 스냅샷 배치 저장 실패 — 수동 확인 필요: date={}", today, e);
            throw new RuntimeException("재고 스냅샷 저장 실패: " + today, e);
        }
    }
}
