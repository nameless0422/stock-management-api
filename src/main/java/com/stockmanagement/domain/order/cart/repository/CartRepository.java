package com.stockmanagement.domain.order.cart.repository;

import com.stockmanagement.domain.order.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 아이템 레포지토리.
 */
public interface CartRepository extends JpaRepository<CartItem, Long> {

    /** 사용자의 장바구니 전체 조회 — product, variant fetch join으로 N+1 방지 */
    @EntityGraph(attributePaths = {"product", "variant"})
    List<CartItem> findByUserId(Long userId);

    /** 사용자 + variant 조합으로 단건 조회 */
    @EntityGraph(attributePaths = {"product", "variant"})
    Optional<CartItem> findByUserIdAndVariantId(Long userId, Long variantId);

    /** 사용자의 장바구니 전체 삭제 — 벌크 DELETE (파생 삭제 메서드의 N+1 방지) */
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.userId = :userId")
    void deleteByUserId(Long userId);

    /** 사용자의 장바구니에서 특정 variant들만 삭제 (선택 결제 후 나머지 유지) */
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.userId = :userId AND c.variant.id IN :variantIds")
    void deleteByUserIdAndVariantIdIn(Long userId, Collection<Long> variantIds);
}
