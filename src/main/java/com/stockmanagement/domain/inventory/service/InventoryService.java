package com.stockmanagement.domain.inventory.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 로 개별 오버라이드
 * </ul>
 *
 * <p>동시성: 입고 처리는 비관적 락({@code findByProductIdWithLock})을 사용해
 * 동시 요청으로 인한 수량 오염을 방지한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    /**
     * 상품의 재고 현황을 조회한다.
     * 재고 레코드가 없으면 404 예외를 발생시킨다.
     */
    public InventoryResponse getByProductId(Long productId) {
        return InventoryResponse.from(findByProductId(productId));
    }

    /**
     * 상품에 재고를 입고한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>상품 존재 여부 확인
     *   <li>재고 레코드가 없으면 자동 생성 (상품 등록 후 첫 입고)
     *   <li>비관적 락으로 재고 행을 잠근 뒤 onHand 증가
     * </ol>
     *
     * @param productId 입고 대상 상품 ID
     * @param request   입고 수량 및 메모
     */
    @Transactional
    public InventoryResponse receive(Long productId, InventoryReceiveRequest request) {
        // 상품 존재 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 재고 레코드가 없으면 첫 입고 시 자동 생성
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder().product(product).build()
                ));

        inventory.receive(request.getQuantity());
        return InventoryResponse.from(inventory);
    }

    // ===== 내부 전용 메서드 (Order/Payment 도메인에서 호출) =====

    /**
     * 주문 생성 시 재고를 예약한다.
     * 가용 재고가 부족하면 {@link com.stockmanagement.common.exception.InsufficientStockException}이 발생한다.
     */
    @Transactional
    public void reserve(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.reserve(quantity);
    }

    /** 주문 취소 또는 결제 실패 시 예약을 해제한다. */
    @Transactional
    public void releaseReservation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.releaseReservation(quantity);
    }

    /** 결제 완료 시 예약을 확정(allocated)으로 전환한다. */
    @Transactional
    public void confirmAllocation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.confirmAllocation(quantity);
    }

    /**
     * Releases allocated stock after payment cancellation / refund.
     * Called when a CONFIRMED order is refunded (payment DONE → CANCELLED).
     * Decreases {@code allocated} without touching {@code onHand}.
     */
    @Transactional
    public void releaseAllocation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.releaseAllocation(quantity);
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
}
