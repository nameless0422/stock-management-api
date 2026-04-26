package com.stockmanagement.domain.inventory.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.exception.InsufficientStockException;
import com.stockmanagement.domain.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Inventory 엔티티 단위 테스트")
class InventoryTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("테스트 상품")
                .description("설명")
                .price(new BigDecimal("10000"))
                .sku("SKU-001")
                .build();
    }

    private Inventory createInventory() {
        return Inventory.builder().product(product).build();
    }

    // ===== getAvailable() =====

    @Nested
    @DisplayName("getAvailable()")
    class GetAvailable {

        @Test
        @DisplayName("초기 상태에서 available은 0이다")
        void initialAvailableIsZero() {
            Inventory inventory = createInventory();
            assertThat(inventory.getAvailable()).isZero();
        }

        @Test
        @DisplayName("onHand - reserved - allocated 계산값을 반환한다")
        void calculatesCorrectly() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(3);
            inventory.confirmAllocation(2); // reserved 3→1, allocated 0→2

            // available = 10 - 1 - 2 = 7
            assertThat(inventory.getAvailable()).isEqualTo(7);
        }
    }

    // ===== receive() =====

    @Nested
    @DisplayName("receive()")
    class Receive {

        @Test
        @DisplayName("양수 수량 입고 시 onHand가 증가한다")
        void increasesOnHand() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            assertThat(inventory.getOnHand()).isEqualTo(10);
        }

        @Test
        @DisplayName("복수 입고 시 onHand가 누적된다")
        void accumulatesOnHand() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.receive(5);
            assertThat(inventory.getOnHand()).isEqualTo(15);
        }

        @Test
        @DisplayName("0 수량 입고 시 IllegalArgumentException 발생")
        void throwsExceptionForZeroQuantity() {
            Inventory inventory = createInventory();
            assertThatThrownBy(() -> inventory.receive(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("음수 수량 입고 시 IllegalArgumentException 발생")
        void throwsExceptionForNegativeQuantity() {
            Inventory inventory = createInventory();
            assertThatThrownBy(() -> inventory.receive(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ===== reserve() =====

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("가용 재고 이하 예약 시 reserved가 증가한다")
        void increasesReserved() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(3);

            assertThat(inventory.getReserved()).isEqualTo(3);
            assertThat(inventory.getAvailable()).isEqualTo(7);
        }

        @Test
        @DisplayName("가용 재고 전량 예약이 가능하다")
        void canReserveAllAvailable() {
            Inventory inventory = createInventory();
            inventory.receive(5);
            inventory.reserve(5);

            assertThat(inventory.getReserved()).isEqualTo(5);
            assertThat(inventory.getAvailable()).isZero();
        }

        @Test
        @DisplayName("가용 재고 초과 예약 시 InsufficientStockException 발생")
        void throwsExceptionWhenExceedsAvailable() {
            Inventory inventory = createInventory();
            inventory.receive(5);

            assertThatThrownBy(() -> inventory.reserve(6))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("재고가 0인 상태에서 예약 시 InsufficientStockException 발생")
        void throwsExceptionWhenNoStock() {
            Inventory inventory = createInventory();

            assertThatThrownBy(() -> inventory.reserve(1))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("allocated로 인해 available이 줄어든 상태에서 초과 예약 시 예외 발생")
        void throwsExceptionWhenReducedByAllocated() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5); // allocated=5, available=5

            assertThatThrownBy(() -> inventory.reserve(6))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    // ===== releaseReservation() =====

    @Nested
    @DisplayName("releaseReservation()")
    class ReleaseReservation {

        @Test
        @DisplayName("예약 해제 시 reserved가 감소한다")
        void decreasesReserved() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.releaseReservation(3);

            assertThat(inventory.getReserved()).isEqualTo(2);
        }

        @Test
        @DisplayName("전량 해제 시 reserved가 0이 된다")
        void fullRelease() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.releaseReservation(5);

            assertThat(inventory.getReserved()).isZero();
        }

        @Test
        @DisplayName("reserved보다 큰 수량 해제 시 0으로 보호된다")
        void protectsAgainstNegative() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(3);
            inventory.releaseReservation(10);

            assertThat(inventory.getReserved()).isZero();
        }

        @Test
        @DisplayName("예약 해제 후 available이 복구된다")
        void availableIncreasesAfterRelease() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.releaseReservation(5);

            assertThat(inventory.getAvailable()).isEqualTo(10);
        }
    }

    // ===== confirmAllocation() =====

    @Nested
    @DisplayName("confirmAllocation()")
    class ConfirmAllocation {

        @Test
        @DisplayName("결제 완료 시 reserved 감소, allocated 증가")
        void movesFromReservedToAllocated() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5);

            assertThat(inventory.getReserved()).isZero();
            assertThat(inventory.getAllocated()).isEqualTo(5);
        }

        @Test
        @DisplayName("부분 확정 시 나머지 reserved는 유지된다")
        void partialConfirmation() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(3);

            assertThat(inventory.getReserved()).isEqualTo(2);
            assertThat(inventory.getAllocated()).isEqualTo(3);
        }

        @Test
        @DisplayName("confirmAllocation은 onHand를 변경하지 않는다")
        void doesNotChangeOnHand() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5);

            assertThat(inventory.getOnHand()).isEqualTo(10);
        }

        @Test
        @DisplayName("quantity > reserved이면 INVENTORY_STATE_INCONSISTENT 예외 발생")
        void throwsWhenQuantityExceedsReserved() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(3);

            assertThatThrownBy(() -> inventory.confirmAllocation(5))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVENTORY_STATE_INCONSISTENT));
        }

        @Test
        @DisplayName("confirmAllocation 후 available은 onHand - allocated로 계산된다")
        void availableAfterConfirmation() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5); // reserved=0, allocated=5

            // available = 10 - 0 - 5 = 5
            assertThat(inventory.getAvailable()).isEqualTo(5);
        }
    }

    // ===== releaseAllocation() =====

    @Nested
    @DisplayName("releaseAllocation()")
    class ReleaseAllocation {

        @Test
        @DisplayName("환불 시 allocated가 감소한다")
        void decreasesAllocated() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5);
            inventory.releaseAllocation(5);

            assertThat(inventory.getAllocated()).isZero();
        }

        @Test
        @DisplayName("allocated보다 큰 수량 해제 시 0으로 보호된다")
        void protectsAgainstNegative() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5);
            inventory.releaseAllocation(100);

            assertThat(inventory.getAllocated()).isZero();
        }

        @Test
        @DisplayName("환불 후 available이 복구된다")
        void availableIncreasesAfterRelease() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5); // available=5, allocated=5
            inventory.releaseAllocation(5); // available=10, allocated=0

            assertThat(inventory.getAvailable()).isEqualTo(10);
        }

        @Test
        @DisplayName("부분 환불 시 나머지 allocated는 유지된다")
        void partialRelease() {
            Inventory inventory = createInventory();
            inventory.receive(10);
            inventory.reserve(5);
            inventory.confirmAllocation(5);
            inventory.releaseAllocation(3);

            assertThat(inventory.getAllocated()).isEqualTo(2);
        }
    }
}
