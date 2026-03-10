package com.stockmanagement.domain.inventory.dto;

import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class InventoryTransactionResponse {

    private final Long id;
    private final String type;
    private final int quantity;
    private final int snapshotOnHand;
    private final int snapshotReserved;
    private final int snapshotAllocated;
    private final LocalDateTime createdAt;

    public static InventoryTransactionResponse from(InventoryTransaction tx) {
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getType().name(),
                tx.getQuantity(),
                tx.getSnapshotOnHand(),
                tx.getSnapshotReserved(),
                tx.getSnapshotAllocated(),
                tx.getCreatedAt()
        );
    }
}
