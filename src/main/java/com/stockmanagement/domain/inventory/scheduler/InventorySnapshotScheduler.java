package com.stockmanagement.domain.inventory.scheduler;

import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * 일별 재고 스냅샷 스케줄러.
 *
 * <p>매일 자정 5분에 전체 재고 현황을 {@code daily_inventory_snapshots}에 저장한다.
 * 동일 재고·날짜 조합이 이미 존재하면 스킵하여 멱등성을 보장한다.
 *
 * <p>페이지 단위({@value PAGE_SIZE}건)로 분할 처리하여 힙 사용량과 테이블 락 점유 시간을 줄인다.
 * 각 페이지는 {@link InventorySnapshotProcessor}의 독립 트랜잭션으로 커밋된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.snapshot.enabled", havingValue = "true", matchIfMissing = true)
public class InventorySnapshotScheduler {

    private static final int PAGE_SIZE = 1_000;

    private final InventoryRepository inventoryRepository;
    private final DailyInventorySnapshotRepository snapshotRepository;
    private final InventorySnapshotProcessor snapshotProcessor;

    @Scheduled(cron = "${inventory.snapshot.cron:0 5 0 * * *}")
    public void takeSnapshot() {
        LocalDate today = LocalDate.now();
        // 오늘 이미 스냅샷이 존재하는 inventoryId를 한 번에 조회하여 중복 방지
        Set<Long> alreadySnapped = snapshotRepository.findInventoryIdsBySnapshotDate(today);

        int pageNum = 0;
        int totalSaved = 0;
        long totalElements = 0;

        while (true) {
            Page<Inventory> inventoryPage = inventoryRepository.findAll(PageRequest.of(pageNum, PAGE_SIZE));
            if (inventoryPage.isEmpty()) break;

            if (pageNum == 0) {
                totalElements = inventoryPage.getTotalElements();
                log.info("[InventorySnapshotScheduler] 재고 스냅샷 시작 — 전체: {}건, 기준일: {}", totalElements, today);
            }

            try {
                totalSaved += snapshotProcessor.processBatch(inventoryPage.getContent(), alreadySnapped, today);
            } catch (DataIntegrityViolationException e) {
                // 레이스 컨디션: alreadySnapped 조회 후 INSERT 사이에 다른 인스턴스가 먼저 저장한 경우
                log.debug("[InventorySnapshotScheduler] 중복 스냅샷 감지 (동시 실행 경합) — date={}, page={}", today, pageNum);
            } catch (Exception e) {
                log.error("[InventorySnapshotScheduler] 스냅샷 배치 저장 실패 — 수동 확인 필요: date={}, page={}", today, pageNum, e);
                throw new RuntimeException("재고 스냅샷 저장 실패: " + today, e);
            }

            if (inventoryPage.isLast()) break;
            pageNum++;
        }

        log.info("[InventorySnapshotScheduler] 완료 — 저장: {}건 / 스킵: {}건",
                totalSaved, totalElements - totalSaved);
    }
}
