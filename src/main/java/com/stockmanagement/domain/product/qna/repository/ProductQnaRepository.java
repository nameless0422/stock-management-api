package com.stockmanagement.domain.product.qna.repository;

import com.stockmanagement.domain.product.qna.entity.ProductQna;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductQnaRepository extends JpaRepository<ProductQna, Long> {

    Page<ProductQna> findByProductId(Long productId, Pageable pageable);
}
