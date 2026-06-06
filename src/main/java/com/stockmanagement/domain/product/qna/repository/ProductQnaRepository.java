package com.stockmanagement.domain.product.qna.repository;

import com.stockmanagement.domain.product.qna.entity.ProductQna;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductQnaRepository extends JpaRepository<ProductQna, Long> {

    Page<ProductQna> findByProductId(Long productId, Pageable pageable);

    /** 커서 기반 조회 — 첫 페이지. */
    List<ProductQna> findByProductIdOrderByIdDesc(Long productId, Pageable pageable);

    /** 커서 기반 조회 — 다음 페이지 (id < lastId). */
    List<ProductQna> findByProductIdAndIdLessThanOrderByIdDesc(Long productId, Long lastId, Pageable pageable);
}
