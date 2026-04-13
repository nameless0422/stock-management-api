package com.stockmanagement.domain.inventory.scheduler;

import com.stockmanagement.domain.inventory.entity.DailyInventorySnapshot;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 재고 스냅샷 배치 처리기.
 *
 * <p>{@link InventorySnapshotScheduler}에서 페이지 단위로 호출한다.
 * 각 호출이 독립 트랜잭션으로 커밋되므로 힙 사용량과 테이블 락 점유 시간을 모두 줄인다.
 * (별도 빈으로 분리하지 않으면 같은 클래스 내 self-call이 되어 Spring AOP 프록시를 우회함)
 */
@Component
@RequiredArgsConstructor
class InventorySnapshotProcessor {

    private final DailyInventorySnapshotRepository snapshotRepository;

    /**
     * 재고 페이지 한 건을 스냅샷으로 저장한다.
     *
     * @param inventories  이번 페이지의 재고 목록
     * @param alreadySnapped 오늘 이미 저장된 inventoryId 집합 (중복 방지)
     * @param today        스냅샷 기준일
     * @return 신규 저장된 스냅샷 건수
     */
    @Transactional
    int processBatch(List<Inventory> inventories, Set<Long> alreadySnapped, LocalDate today) {
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

        snapshotRepository.saveAll(snapshots);
        return snapshots.size();
    }
}
