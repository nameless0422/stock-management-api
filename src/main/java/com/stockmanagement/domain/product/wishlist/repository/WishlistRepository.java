package com.stockmanagement.domain.product.wishlist.repository;

import com.stockmanagement.domain.product.wishlist.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    /** 사용자의 위시리스트 목록을 최신순으로 조회한다 (전체). */
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 사용자의 위시리스트 목록을 최신순으로 페이징 조회한다. */
    Page<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 상품 목록 중 위시리스트에 등록된 상품 ID Set을 배치 조회한다 (목록 N+1 방지). */
    @Query("SELECT w.productId FROM WishlistItem w WHERE w.userId = :userId AND w.productId IN :productIds")
    Set<Long> findWishlistedProductIds(@Param("userId") Long userId,
                                       @Param("productIds") Collection<Long> productIds);

    /** 회원 탈퇴 시 사용자의 위시리스트 전체를 일괄 삭제한다. */
    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
