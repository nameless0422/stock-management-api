package com.stockmanagement.domain.product.repository;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 레포지토리.
 *
 * <p>Spring Data JPA의 쿼리 메서드 네이밍 규칙을 사용해 쿼리를 자동 생성한다.
 * 복잡한 동적 쿼리가 필요해지면 Querydsl 또는 @Query로 확장한다.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** SKU 중복 여부를 빠르게 확인 — 상품 등록 시 중복 검사용 */
    boolean existsBySku(String sku);

    /** 특정 상태의 상품을 페이징 조회 — 목록 API에서 ACTIVE 상품만 반환할 때 사용 */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
}
