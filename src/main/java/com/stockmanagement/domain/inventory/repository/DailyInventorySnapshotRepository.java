package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.DailyInventorySnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface DailyInventorySnapshotRepository extends JpaRepository<DailyInventorySnapshot, Long> {

    /** 동일 재고·날짜 스냅샷 존재 여부 확인 (중복 저장 방지) */
    boolean existsByInventoryIdAndSnapshotDate(Long inventoryId, LocalDate date);

    /**
     * 특정 날짜에 이미 스냅샷이 존재하는 inventoryId 집합을 한 번에 조회한다.
     * InventorySnapshotScheduler에서 N+1 방지용으로 사용한다.
     */
    @Query("SELECT s.inventory.id FROM DailyInventorySnapshot s WHERE s.snapshotDate = :date")
    Set<Long> findInventoryIdsBySnapshotDate(@Param("date") LocalDate date);

    /** 특정 날짜의 전체 스냅샷 조회 — inventory.product N+1 방지 */
    @EntityGraph(attributePaths = {"inventory.product"})
    List<DailyInventorySnapshot> findBySnapshotDate(LocalDate date);
}
