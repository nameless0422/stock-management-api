package com.stockmanagement.domain.admin.dto;

import com.stockmanagement.domain.inventory.entity.Inventory;

public record LowStockItem(
        Long productId,
        String productName,
        String sku,
        int onHand,
        int reserved,
        int allocated,
        int available
) {
    public static LowStockItem from(Inventory inventory) {
        return new LowStockItem(
                inventory.getProduct().getId(),
                inventory.getProduct().getName(),
                inventory.getProduct().getSku(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAllocated(),
                inventory.getAvailable()
        );
    }
}
