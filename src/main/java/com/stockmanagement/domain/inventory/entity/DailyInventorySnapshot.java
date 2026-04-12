package com.stockmanagement.domain.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일별 재고 스냅샷 엔티티.
 *
 * <p>{@code InventorySnapshotScheduler}가 매일 자정 5분에 전체 재고 상태를 캡처한다.
 * (inventoryId, snapshotDate) UNIQUE 제약으로 중복 저장을 방지한다.
 */
@Entity
@Table(name = "daily_inventory_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_snapshot_inv_date",
                columnNames = {"inventory_id", "snapshot_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyInventorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 스냅샷 대상 재고 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    /** 스냅샷 기준일 */
    @Column(nullable = false)
    private LocalDate snapshotDate;

    /** 창고 실물 재고 */
    @Column(nullable = false)
    private int onHand;

    /** 주문 예약 재고 (미결제) */
    @Column(nullable = false)
    private int reserved;

    /** 출고 확정 재고 (결제 완료) */
    @Column(nullable = false)
    private int allocated;

    /** 주문 가능 재고 = onHand - reserved - allocated */
    @Column(nullable = false)
    private int available;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DailyInventorySnapshot(Inventory inventory, LocalDate snapshotDate,
                                   int onHand, int reserved, int allocated, int available) {
        this.inventory    = inventory;
        this.snapshotDate = snapshotDate;
        this.onHand       = onHand;
        this.reserved     = reserved;
        this.allocated    = allocated;
        this.available    = available;
    }
}
