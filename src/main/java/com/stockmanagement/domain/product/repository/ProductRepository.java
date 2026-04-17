package com.stockmanagement.domain.product.repository;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

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

    /**
     * 특정 상태의 상품을 페이징 조회 — 목록 API에서 ACTIVE 상품만 반환할 때 사용.
     * category LEFT JOIN FETCH로 N+1 방지.
     */
    @EntityGraph(attributePaths = "category")
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /**
     * 전체 상품 목록 조회 (관리자용) — category LEFT JOIN FETCH로 N+1 방지.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);

    /**
     * ACTIVE 상품 중 상품명 또는 SKU로 검색 (대소문자 무시).
     * countQuery 분리로 페이징 카운트 쿼리의 정확성 보장.
     */
    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.status = :status AND " +
                   "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.status = :status AND " +
                        "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Product> searchByStatus(@Param("status") ProductStatus status, @Param("q") String query, Pageable pageable);

    /**
     * 전체 상태의 상품 중 상품명 또는 SKU로 검색 (대소문자 무시, 관리자 전용).
     * countQuery 분리로 페이징 카운트 쿼리의 정확성 보장.
     */
    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE " +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE " +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Product> searchAll(@Param("q") String query, Pageable pageable);

    /** 카테고리 ID 목록 필터 — ACTIVE 상품 중 해당 카테고리에 속하는 상품만 조회 (하위 카테고리 포함 가능). */
    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.status = :status AND p.category.id IN :categoryIds",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.category.id IN :categoryIds")
    Page<Product> findByStatusAndCategoryIdIn(@Param("status") ProductStatus status,
                                              @Param("categoryIds") Collection<Long> categoryIds,
                                              Pageable pageable);
}
