package com.stockmanagement.domain.product.repository;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품 레포지토리.
 *
 * <p>Spring Data JPA의 쿼리 메서드 네이밍 규칙을 사용해 쿼리를 자동 생성한다.
 * 복잡한 동적 쿼리가 필요해지면 Querydsl 또는 @Query로 확장한다.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** SKU 중복 여부를 빠르게 확인 — 상품 등록 시 중복 검사용 */
    boolean existsBySku(String sku);

    /** 특정 카테고리에 상품이 존재하는지 확인 — 카테고리 삭제 전 검사용 */
    boolean existsByCategory_Id(Long categoryId);

    /** 특정 상태의 상품을 페이징 조회 — 목록 API에서 ACTIVE 상품만 반환할 때 사용 */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /** ACTIVE 상품 중 상품명 또는 SKU로 검색 (대소문자 무시) */
    @Query("SELECT p FROM Product p WHERE p.status = :status AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Product> searchByStatus(@Param("status") ProductStatus status, @Param("q") String query, Pageable pageable);

    /** 전체 상태의 상품 중 상품명 또는 SKU로 검색 (대소문자 무시, 관리자 전용) */
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Product> searchAll(@Param("q") String query, Pageable pageable);
}
