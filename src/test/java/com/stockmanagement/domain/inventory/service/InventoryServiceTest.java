package com.stockmanagement.domain.inventory.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.exception.InsufficientStockException;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 단위 테스트")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("테스트 상품")
                .description("설명")
                .price(new BigDecimal("10000"))
                .sku("SKU-001")
                .category("전자기기")
                .build();
    }

    /** onHand=10, reserved=3, available=7 인 재고 픽스처 */
    private Inventory inventoryWithStock() {
        Inventory inv = Inventory.builder().product(product).build();
        inv.receive(10);
        inv.reserve(3);
        return inv;
    }

    // ===== getByProductId() =====

    @Nested
    @DisplayName("getByProductId()")
    class GetByProductId {

        @Test
        @DisplayName("재고가 존재하면 수량 정보가 담긴 InventoryResponse를 반환한다")
        void returnsInventoryResponse() {
            Inventory inventory = inventoryWithStock();
            given(inventoryRepository.findByProductId(1L)).willReturn(Optional.of(inventory));

            InventoryResponse response = inventoryService.getByProductId(1L);

            assertThat(response.getOnHand()).isEqualTo(10);
            assertThat(response.getReserved()).isEqualTo(3);
            assertThat(response.getAvailable()).isEqualTo(7);
        }

        @Test
        @DisplayName("재고 레코드가 없으면 INVENTORY_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(inventoryRepository.findByProductId(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.getByProductId(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
        }
    }

    // ===== receive() =====

    @Nested
    @DisplayName("receive()")
    class Receive {

        private InventoryReceiveRequest receiveRequest;

        @BeforeEach
        void setUp() {
            // InventoryReceiveRequest에 공개 생성자가 없으므로 Mock으로 생성
            // lenient: 상품 미존재 테스트에서는 getQuantity() 호출이 발생하지 않음
            receiveRequest = mock(InventoryReceiveRequest.class);
            lenient().when(receiveRequest.getQuantity()).thenReturn(5);
        }

        @Test
        @DisplayName("기존 재고 레코드가 있으면 onHand를 증가시킨다")
        void increasesOnHandForExistingInventory() {
            Inventory inventory = inventoryWithStock(); // onHand=10
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            InventoryResponse response = inventoryService.receive(1L, receiveRequest);

            assertThat(response.getOnHand()).isEqualTo(15); // 10 + 5
        }

        @Test
        @DisplayName("재고 레코드가 없으면 자동 생성 후 onHand를 설정한다")
        void createsInventoryWhenNotExists() {
            Inventory newInventory = Inventory.builder().product(product).build();
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.empty());
            given(inventoryRepository.save(any(Inventory.class))).willReturn(newInventory);

            InventoryResponse response = inventoryService.receive(1L, receiveRequest);

            verify(inventoryRepository).save(any(Inventory.class));
            assertThat(response.getOnHand()).isEqualTo(5); // 0 + 5
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenProductNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.receive(99L, receiveRequest))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));

            verifyNoInteractions(inventoryRepository);
        }
    }

    // ===== reserve() =====

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("가용 재고가 충분하면 reserved가 증가한다")
        void reservesSuccessfully() {
            Inventory inventory = inventoryWithStock(); // available=7
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            assertThatCode(() -> inventoryService.reserve(1L, 5)).doesNotThrowAnyException();

            assertThat(inventory.getReserved()).isEqualTo(8); // 3 + 5
        }

        @Test
        @DisplayName("가용 재고 부족 시 InsufficientStockException이 전파된다")
        void propagatesInsufficientStockException() {
            Inventory inventory = inventoryWithStock(); // available=7
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            assertThatThrownBy(() -> inventoryService.reserve(1L, 8))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("재고 레코드가 없으면 INVENTORY_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenInventoryNotFound() {
            given(inventoryRepository.findByProductIdWithLock(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.reserve(99L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
        }
    }

    // ===== releaseReservation() =====

    @Nested
    @DisplayName("releaseReservation()")
    class ReleaseReservation {

        @Test
        @DisplayName("예약 해제 시 reserved가 감소한다")
        void releasesReservation() {
            Inventory inventory = inventoryWithStock(); // reserved=3
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.releaseReservation(1L, 3);

            assertThat(inventory.getReserved()).isZero();
        }

        @Test
        @DisplayName("재고 레코드가 없으면 INVENTORY_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenInventoryNotFound() {
            given(inventoryRepository.findByProductIdWithLock(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.releaseReservation(99L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
        }
    }

    // ===== confirmAllocation() =====

    @Nested
    @DisplayName("confirmAllocation()")
    class ConfirmAllocation {

        @Test
        @DisplayName("결제 완료 시 reserved 감소, allocated 증가")
        void confirmsAllocation() {
            Inventory inventory = inventoryWithStock(); // reserved=3
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.confirmAllocation(1L, 3);

            assertThat(inventory.getReserved()).isZero();
            assertThat(inventory.getAllocated()).isEqualTo(3);
        }

        @Test
        @DisplayName("재고 레코드가 없으면 INVENTORY_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenInventoryNotFound() {
            given(inventoryRepository.findByProductIdWithLock(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.confirmAllocation(99L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
        }
    }

    // ===== releaseAllocation() =====

    @Nested
    @DisplayName("releaseAllocation()")
    class ReleaseAllocation {

        @Test
        @DisplayName("환불 시 allocated가 감소한다")
        void releasesAllocation() {
            Inventory inventory = inventoryWithStock(); // reserved=3
            inventory.confirmAllocation(3); // reserved=0, allocated=3
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.releaseAllocation(1L, 3);

            assertThat(inventory.getAllocated()).isZero();
        }

        @Test
        @DisplayName("환불 후 available이 복구된다")
        void availableIncreasesAfterRelease() {
            Inventory inventory = inventoryWithStock(); // onHand=10, reserved=3, available=7
            inventory.confirmAllocation(3); // reserved=0, allocated=3, available=7
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.releaseAllocation(1L, 3);

            // available = 10 - 0 - 0 = 10
            assertThat(inventory.getAvailable()).isEqualTo(10);
        }

        @Test
        @DisplayName("재고 레코드가 없으면 INVENTORY_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenInventoryNotFound() {
            given(inventoryRepository.findByProductIdWithLock(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.releaseAllocation(99L, 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
        }
    }
}
