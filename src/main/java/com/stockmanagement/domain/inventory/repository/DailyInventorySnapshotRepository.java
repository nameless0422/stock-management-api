package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.DailyInventorySnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyInventorySnapshotRepository extends JpaRepository<DailyInventorySnapshot, Long> {

    /** 동일 재고·날짜 스냅샷 존재 여부 확인 (중복 저장 방지) */
    boolean existsByInventoryIdAndSnapshotDate(Long inventoryId, LocalDate date);

    /** 특정 날짜의 전체 스냅샷 조회 — inventory.product N+1 방지 */
    @EntityGraph(attributePaths = {"inventory.product"})
    List<DailyInventorySnapshot> findBySnapshotDate(LocalDate date);
}
