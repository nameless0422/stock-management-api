package com.stockmanagement.domain.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 재고 변동 이력 엔티티.
 *
 * <p>재고 뮤테이션(입고/예약/해제/확정/환불)이 발생할 때마다 한 건이 기록된다.
 * {@code snapshot*} 필드는 변동 <strong>이후</strong>의 수량 상태를 보존한다.
 */
@Entity
@Table(name = "inventory_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryTransactionType type;

    /** 해당 이벤트에서 변동된 수량 (항상 양수) */
    @Column(nullable = false)
    private int quantity;

    /** 입고·조정 시 관리자가 입력한 사유 (선택) */
    @Column(length = 255)
    private String note;

    /** 변동 이후 onHand 스냅샷 */
    @Column(nullable = false)
    private int snapshotOnHand;

    /** 변동 이후 reserved 스냅샷 */
    @Column(nullable = false)
    private int snapshotReserved;

    /** 변동 이후 allocated 스냅샷 */
    @Column(nullable = false)
    private int snapshotAllocated;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private InventoryTransaction(Inventory inventory, InventoryTransactionType type, int quantity, String note) {
        this.inventory = inventory;
        this.type = type;
        this.quantity = quantity;
        this.note = note;
        // 뮤테이션 이후 Inventory 상태를 스냅샷으로 기록
        this.snapshotOnHand = inventory.getOnHand();
        this.snapshotReserved = inventory.getReserved();
        this.snapshotAllocated = inventory.getAllocated();
    }
}
