package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 재고 레포지토리.
 *
 * <p>동시성 제어를 위해 두 가지 조회 메서드를 제공한다:
 * <ul>
 *   <li>{@link #findByProductId} — 단순 조회 (읽기 전용)
 *   <li>{@link #findByProductIdWithLock} — 비관적 락 조회 (입고 등 쓰기 전용)
 * </ul>
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** 상품 ID로 재고를 조회한다 (읽기 전용) */
    Optional<Inventory> findByProductId(Long productId);

    /**
     * 상품 ID로 재고를 조회하되 비관적 쓰기 락을 건다.
     *
     * <p>입고(receive) 같이 onHand를 수정하는 고빈도 쓰기 작업에 사용한다.
     * 같은 재고를 동시에 수정하는 트랜잭션이 있으면 한 쪽이 락 해제를 기다린다.
     * {@code @Version} 낙관적 락과 병행 사용해 이중으로 보호한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdWithLock(@Param("productId") Long productId);
}
