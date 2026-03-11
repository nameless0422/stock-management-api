package com.stockmanagement.domain.inventory.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.lock.DistributedLock;
import com.stockmanagement.domain.inventory.dto.InventoryAdjustRequest;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.dto.InventoryTransactionResponse;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import com.stockmanagement.domain.inventory.entity.InventoryTransactionType;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.inventory.repository.InventoryTransactionRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 재고 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 로 개별 오버라이드
 * </ul>
 *
 * <p>동시성 2중 제어:
 * <ul>
 *   <li>분산 락({@link DistributedLock}): 멀티 인스턴스 환경에서 Redis를 통한 외부 락
 *   <li>비관적 락({@code findByProductIdWithLock}): 단일 인스턴스 DB 레벨 보조 락
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;

    /**
     * 상품의 재고 현황을 조회한다.
     * 재고 레코드가 없으면 404 예외를 발생시킨다.
     */
    public InventoryResponse getByProductId(Long productId) {
        return InventoryResponse.from(findByProductId(productId));
    }

    /**
     * 상품의 재고 변동 이력을 최신순으로 조회한다.
     * 재고 레코드가 없으면 404 예외를 발생시킨다.
     */
    public List<InventoryTransactionResponse> getTransactions(Long productId) {
        findByProductId(productId); // 재고 존재 여부 확인
        return transactionRepository.findByInventoryProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(InventoryTransactionResponse::from)
                .toList();
    }

    /**
     * 상품에 재고를 입고한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>분산 락 획득
     *   <li>상품 존재 여부 확인
     *   <li>재고 레코드가 없으면 자동 생성 (상품 등록 후 첫 입고)
     *   <li>비관적 락으로 재고 행을 잠근 뒤 onHand 증가
     *   <li>이력 기록
     * </ol>
     *
     * @param productId 입고 대상 상품 ID
     * @param request   입고 수량 및 메모
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    public InventoryResponse receive(Long productId, InventoryReceiveRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder().product(product).build()
                ));

        inventory.receive(request.getQuantity());
        recordTransaction(inventory, InventoryTransactionType.RECEIVE, request.getQuantity(), request.getNote());
        return InventoryResponse.from(inventory);
    }

    /**
     * 관리자 수동 재고 조정.
     *
     * @param productId 조정 대상 상품 ID
     * @param request   조정 수량(양수/음수) 및 사유
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    public InventoryResponse adjust(Long productId, InventoryAdjustRequest request) {
        if (request.getQuantity() == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.adjust(request.getQuantity());
        recordTransaction(inventory, InventoryTransactionType.ADJUSTMENT, request.getQuantity(), request.getNote());
        return InventoryResponse.from(inventory);
    }

    // ===== 내부 전용 메서드 (Order/Payment 도메인에서 호출) =====

    /**
     * 주문 생성 시 재고를 예약한다.
     * 가용 재고가 부족하면 {@link com.stockmanagement.common.exception.InsufficientStockException}이 발생한다.
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    public void reserve(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.reserve(quantity);
        recordTransaction(inventory, InventoryTransactionType.RESERVE, quantity);
    }

    /** 주문 취소 또는 결제 실패 시 예약을 해제한다. */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    public void releaseReservation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.releaseReservation(quantity);
        recordTransaction(inventory, InventoryTransactionType.RELEASE_RESERVATION, quantity);
    }

    /** 결제 완료 시 예약을 확정(allocated)으로 전환한다. */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    public void confirmAllocation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.confirmAllocation(quantity);
        recordTransaction(inventory, InventoryTransactionType.CONFIRM_ALLOCATION, quantity);
    }

    /**
     * 결제 취소(환불) 시 확정 재고를 해제한다.
     * allocated를 감소시켜 available을 복구한다.
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    public void releaseAllocation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.releaseAllocation(quantity);
        recordTransaction(inventory, InventoryTransactionType.RELEASE_ALLOCATION, quantity);
    }

    // ===== private 헬퍼 =====

    private Inventory findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    /** 비관적 락으로 재고를 조회한다 — 쓰기 작업 전용 */
    private Inventory findByProductIdWithLock(Long productId) {
        return inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    /** 재고 뮤테이션 이후 이력을 기록한다 (note 없음). */
    private void recordTransaction(Inventory inventory, InventoryTransactionType type, int quantity) {
        recordTransaction(inventory, type, quantity, null);
    }

    /** 재고 뮤테이션 이후 이력을 기록한다 (note 포함). */
    private void recordTransaction(Inventory inventory, InventoryTransactionType type, int quantity, String note) {
        transactionRepository.save(
                InventoryTransaction.builder()
                        .inventory(inventory)
                        .type(type)
                        .quantity(quantity)
                        .note(note)
                        .build()
        );
    }
}
