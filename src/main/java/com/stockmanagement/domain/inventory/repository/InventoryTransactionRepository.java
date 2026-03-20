package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 특정 상품의 재고 이력을 최신순 페이징 조회한다. */
    Page<InventoryTransaction> findByInventoryProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
}
