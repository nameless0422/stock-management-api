package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 첫 페이지: 특정 상품 재고 이력을 ID 내림차순으로 최대 size+1건 조회. */
    List<InventoryTransaction> findByInventoryProductIdOrderByIdDesc(Long productId, Pageable pageable);

    /** 커서 이후: lastId보다 작은 재고 이력을 ID 내림차순으로 최대 size+1건 조회. */
    List<InventoryTransaction> findByInventoryProductIdAndIdLessThanOrderByIdDesc(
            Long productId, Long lastId, Pageable pageable);
}
