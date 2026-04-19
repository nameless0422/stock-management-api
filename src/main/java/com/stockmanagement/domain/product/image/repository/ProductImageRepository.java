package com.stockmanagement.domain.product.image.repository;

import com.stockmanagement.domain.product.image.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    /** 이미지 ID 목록 + productId로 배치 조회 (순서 변경 시 소유권 검증) */
    List<ProductImage> findByProductIdAndIdIn(Long productId, Collection<Long> ids);
}
