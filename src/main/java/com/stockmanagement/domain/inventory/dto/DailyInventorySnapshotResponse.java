package com.stockmanagement.domain.inventory.dto;

import com.stockmanagement.domain.inventory.entity.DailyInventorySnapshot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** 일별 재고 스냅샷 응답 DTO. */
@Getter
@Builder
public class DailyInventorySnapshotResponse {

    private Long id;
    private Long inventoryId;
    private Long productId;
    private String productName;
    private LocalDate snapshotDate;
    private int onHand;
    private int reserved;
    private int allocated;
    private int available;

    public static DailyInventorySnapshotResponse from(DailyInventorySnapshot snapshot) {
        return DailyInventorySnapshotResponse.builder()
                .id(snapshot.getId())
                .inventoryId(snapshot.getInventory().getId())
                .productId(snapshot.getInventory().getProduct().getId())
                .productName(snapshot.getInventory().getProduct().getName())
                .snapshotDate(snapshot.getSnapshotDate())
                .onHand(snapshot.getOnHand())
                .reserved(snapshot.getReserved())
                .allocated(snapshot.getAllocated())
                .available(snapshot.getAvailable())
                .build();
    }
}
