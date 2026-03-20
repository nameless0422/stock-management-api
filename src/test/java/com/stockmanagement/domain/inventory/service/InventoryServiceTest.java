package com.stockmanagement.domain.inventory.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.exception.InsufficientStockException;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.dto.InventorySearchRequest;
import com.stockmanagement.domain.inventory.dto.InventoryTransactionResponse;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.entity.InventoryStatus;
import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import com.stockmanagement.domain.inventory.entity.InventoryTransactionType;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.inventory.repository.InventoryTransactionRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 단위 테스트")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

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

    // ===== getTransactions() =====

    @Nested
    @DisplayName("getTransactions()")
    class GetTransactions {

        @Test
        @DisplayName("재고가 존재하면 이력 목록을 반환한다")
        void returnsTransactionList() {
            Inventory inventory = inventoryWithStock();
            given(inventoryRepository.findByProductId(1L)).willReturn(Optional.of(inventory));
            InventoryTransaction tx = mock(InventoryTransaction.class);
            given(tx.getId()).willReturn(1L);
            given(tx.getType()).willReturn(InventoryTransactionType.RECEIVE);
            given(tx.getQuantity()).willReturn(10);
            given(tx.getSnapshotOnHand()).willReturn(10);
            given(tx.getSnapshotReserved()).willReturn(0);
            given(tx.getSnapshotAllocated()).willReturn(0);
            given(tx.getCreatedAt()).willReturn(LocalDateTime.now());
            given(transactionRepository.findByInventoryProductIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(tx)));

            Page<InventoryTransactionResponse> result = inventoryService.getTransactions(1L, Pageable.unpaged());

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo("RECEIVE");
        }

        @Test
        @DisplayName("재고 레코드가 없으면 INVENTORY_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenInventoryNotFound() {
            given(inventoryRepository.findByProductId(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.getTransactions(99L, Pageable.unpaged()))
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
        @DisplayName("기존 재고 레코드가 있으면 onHand를 증가시키고 이력을 기록한다")
        void increasesOnHandForExistingInventory() {
            Inventory inventory = inventoryWithStock(); // onHand=10
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            InventoryResponse response = inventoryService.receive(1L, receiveRequest);

            assertThat(response.getOnHand()).isEqualTo(15); // 10 + 5
            verify(transactionRepository).save(any(InventoryTransaction.class));
        }

        @Test
        @DisplayName("재고 레코드가 없으면 자동 생성 후 onHand를 설정하고 이력을 기록한다")
        void createsInventoryWhenNotExists() {
            Inventory newInventory = Inventory.builder().product(product).build();
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.empty());
            given(inventoryRepository.save(any(Inventory.class))).willReturn(newInventory);

            InventoryResponse response = inventoryService.receive(1L, receiveRequest);

            verify(inventoryRepository).save(any(Inventory.class));
            verify(transactionRepository).save(any(InventoryTransaction.class));
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

            verifyNoInteractions(inventoryRepository, transactionRepository);
        }
    }

    // ===== reserve() =====

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("가용 재고가 충분하면 reserved가 증가하고 이력을 기록한다")
        void reservesSuccessfully() {
            Inventory inventory = inventoryWithStock(); // available=7
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            assertThatCode(() -> inventoryService.reserve(1L, 5)).doesNotThrowAnyException();

            assertThat(inventory.getReserved()).isEqualTo(8); // 3 + 5
            verify(transactionRepository).save(any(InventoryTransaction.class));
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
        @DisplayName("예약 해제 시 reserved가 감소하고 이력을 기록한다")
        void releasesReservation() {
            Inventory inventory = inventoryWithStock(); // reserved=3
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.releaseReservation(1L, 3);

            assertThat(inventory.getReserved()).isZero();
            verify(transactionRepository).save(any(InventoryTransaction.class));
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
        @DisplayName("결제 완료 시 reserved 감소, allocated 증가, 이력 기록")
        void confirmsAllocation() {
            Inventory inventory = inventoryWithStock(); // reserved=3
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.confirmAllocation(1L, 3);

            assertThat(inventory.getReserved()).isZero();
            assertThat(inventory.getAllocated()).isEqualTo(3);
            verify(transactionRepository).save(any(InventoryTransaction.class));
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
        @DisplayName("환불 시 allocated가 감소하고 이력을 기록한다")
        void releasesAllocation() {
            Inventory inventory = inventoryWithStock(); // reserved=3
            inventory.confirmAllocation(3); // reserved=0, allocated=3
            given(inventoryRepository.findByProductIdWithLock(1L)).willReturn(Optional.of(inventory));

            inventoryService.releaseAllocation(1L, 3);

            assertThat(inventory.getAllocated()).isZero();
            verify(transactionRepository).save(any(InventoryTransaction.class));
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

    // ===== search() =====

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("조건 없이 호출하면 레포지토리 결과를 Page<InventoryResponse>로 변환해 반환한다")
        void returnsPagedResults() {
            Inventory inventory = inventoryWithStock(); // onHand=10, reserved=3, available=7
            Page<Inventory> page = new PageImpl<>(List.of(inventory));
            given(inventoryRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

            Page<InventoryResponse> result = inventoryService.search(new InventorySearchRequest(), Pageable.unpaged());

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getOnHand()).isEqualTo(10);
            assertThat(result.getContent().get(0).getAvailable()).isEqualTo(7);
            verify(inventoryRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("결과가 없으면 빈 Page를 반환한다")
        void returnsEmptyPage() {
            given(inventoryRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(Page.empty());

            Page<InventoryResponse> result = inventoryService.search(new InventorySearchRequest(), Pageable.unpaged());

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("페이지네이션 정보가 그대로 반환된다")
        void preservesPaginationMeta() {
            Inventory inv1 = inventoryWithStock();
            Inventory inv2 = inventoryWithStock();
            Pageable pageable = PageRequest.of(0, 2);
            Page<Inventory> page = new PageImpl<>(List.of(inv1, inv2), pageable, 5);
            given(inventoryRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

            Page<InventoryResponse> result = inventoryService.search(new InventorySearchRequest(), pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("status 필터가 포함된 request로 호출해도 레포지토리에 Specification이 전달된다")
        void withStatusFilter_passesSpecificationToRepository() {
            given(inventoryRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(Page.empty());

            InventorySearchRequest request = new InventorySearchRequest();
            request.setStatus(InventoryStatus.LOW_STOCK);
            inventoryService.search(request, Pageable.unpaged());

            verify(inventoryRepository).findAll(any(Specification.class), any(Pageable.class));
        }
    }
}
