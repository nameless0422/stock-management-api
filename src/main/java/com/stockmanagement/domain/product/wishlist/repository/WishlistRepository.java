package com.stockmanagement.domain.product.wishlist.repository;

import com.stockmanagement.domain.product.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    /** 사용자의 위시리스트 목록을 최신순으로 조회한다. */
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);
}
