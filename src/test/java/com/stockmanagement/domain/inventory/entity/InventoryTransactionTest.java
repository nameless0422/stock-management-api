package com.stockmanagement.domain.inventory.entity;

import com.stockmanagement.domain.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InventoryTransaction 엔티티 단위 테스트")
class InventoryTransactionTest {

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        Product product = Product.builder()
                .name("테스트 상품")
                .description("설명")
                .price(new BigDecimal("10000"))
                .sku("SKU-001")
                .category("전자기기")
                .build();
        inventory = Inventory.builder().product(product).build();
        inventory.receive(10);
        inventory.reserve(3);
    }

    @Test
    @DisplayName("입고 이후 스냅샷은 변동 후 onHand를 반영한다")
    void snapshotReflectsStateAfterReceive() {
        InventoryTransaction tx = InventoryTransaction.builder()
                .inventory(inventory)
                .type(InventoryTransactionType.RECEIVE)
                .quantity(5)
                .build();

        // inventory 현재 상태: onHand=10, reserved=3, allocated=0
        assertThat(tx.getSnapshotOnHand()).isEqualTo(10);
        assertThat(tx.getSnapshotReserved()).isEqualTo(3);
        assertThat(tx.getSnapshotAllocated()).isZero();
    }

    @Test
    @DisplayName("예약 이후 스냅샷은 변동 후 reserved를 반영한다")
    void snapshotReflectsStateAfterReserve() {
        inventory.reserve(2); // reserved=5

        InventoryTransaction tx = InventoryTransaction.builder()
                .inventory(inventory)
                .type(InventoryTransactionType.RESERVE)
                .quantity(2)
                .build();

        assertThat(tx.getSnapshotReserved()).isEqualTo(5);
    }

    @Test
    @DisplayName("결제 확정 이후 스냅샷은 변동 후 reserved/allocated를 반영한다")
    void snapshotReflectsStateAfterConfirmAllocation() {
        inventory.confirmAllocation(3); // reserved=0, allocated=3

        InventoryTransaction tx = InventoryTransaction.builder()
                .inventory(inventory)
                .type(InventoryTransactionType.CONFIRM_ALLOCATION)
                .quantity(3)
                .build();

        assertThat(tx.getSnapshotReserved()).isZero();
        assertThat(tx.getSnapshotAllocated()).isEqualTo(3);
    }

    @Test
    @DisplayName("type과 quantity 필드가 올바르게 저장된다")
    void typeAndQuantityAreStored() {
        InventoryTransaction tx = InventoryTransaction.builder()
                .inventory(inventory)
                .type(InventoryTransactionType.RELEASE_RESERVATION)
                .quantity(3)
                .build();

        assertThat(tx.getType()).isEqualTo(InventoryTransactionType.RELEASE_RESERVATION);
        assertThat(tx.getQuantity()).isEqualTo(3);
    }
}
