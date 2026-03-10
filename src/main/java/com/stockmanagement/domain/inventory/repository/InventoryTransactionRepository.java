package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 특정 상품의 재고 이력을 최신순으로 조회한다. */
    List<InventoryTransaction> findByInventoryProductIdOrderByCreatedAtDesc(Long productId);
}
