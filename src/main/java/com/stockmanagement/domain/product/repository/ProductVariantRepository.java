package com.stockmanagement.domain.product.repository;

import com.stockmanagement.domain.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 상품 변형 레포지토리.
 */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /** 상품의 모든 variant를 조회한다. */
    List<ProductVariant> findByProductId(Long productId);

    /** 여러 variant를 product와 함께 일괄 조회한다 (주문 생성 시). */
    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.id IN :ids")
    List<ProductVariant> findAllByIdInWithProduct(@Param("ids") Collection<Long> ids);

    /** variant를 product와 함께 조회한다. */
    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.id = :id")
    Optional<ProductVariant> findByIdWithProduct(@Param("id") Long id);

    /** SKU 중복 확인 */
    boolean existsBySku(String sku);
}
