package com.stockmanagement.domain.inventory.repository;

import com.stockmanagement.domain.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 재고 레포지토리.
 *
 * <p>동시성 제어를 위해 두 가지 조회 메서드를 제공한다:
 * <ul>
 *   <li>{@link #findByVariantId} — 단순 조회 (읽기 전용)
 *   <li>{@link #findByVariantIdWithLock} — 비관적 락 조회 (입고 등 쓰기 전용)
 * </ul>
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {

    /** variant ID로 재고를 조회한다 (읽기 전용). variant+product를 JOIN FETCH하여 N+1 방지. */
    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Optional<Inventory> findByVariantId(Long variantId);

    /**
     * variant ID로 재고를 조회하되 비관적 쓰기 락을 건다.
     *
     * <p>입고(receive) 같이 onHand를 수정하는 고빈도 쓰기 작업에 사용한다.
     * 같은 재고를 동시에 수정하는 트랜잭션이 있으면 한 쪽이 락 해제를 기다린다.
     * {@code @Version} 낙관적 락과 병행 사용해 이중으로 보호한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId")
    Optional<Inventory> findByVariantIdWithLock(@Param("variantId") Long variantId);

    /** 여러 variant의 재고를 한 번에 조회한다. variant+product를 JOIN FETCH하여 N+1 방지. */
    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<Inventory> findAllByVariantIdIn(Collection<Long> variantIds);

    /** 상품의 모든 variant 재고를 조회한다 (상세 조회용). */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.variant v WHERE v.product.id = :productId")
    List<Inventory> findAllByProductId(@Param("productId") Long productId);

    /** 여러 상품의 variant 재고를 한 번에 조회한다 (목록 조회용, N+1 방지). */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.variant v WHERE v.product.id IN :productIds")
    List<Inventory> findAllByProductIdIn(@Param("productIds") Collection<Long> productIds);

    /** available(= onHand - reserved - allocated)이 threshold 미만인 저재고 목록 (대시보드용) */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.variant v JOIN FETCH v.product WHERE (i.onHand - i.reserved - i.allocated) < :threshold")
    List<Inventory> findLowStock(@Param("threshold") int threshold);

    /**
     * 동적 조건 검색 — variant+product를 EntityGraph로 즉시 로딩해 N+1을 방지한다.
     * {@link InventorySpecification}으로 생성한 Specification을 전달한다.
     */
    @EntityGraph(attributePaths = {"variant", "variant.product"})
    @Override
    Page<Inventory> findAll(Specification<Inventory> spec, Pageable pageable);

    /**
     * 스냅샷 배치용 전체 재고 Slice 조회 — COUNT 쿼리 없이 LIMIT만 실행한다.
     *
     * <p>{@code Page}의 매 페이지마다 발생하는 {@code SELECT COUNT(*)} 오버헤드를 제거한다.
     * 다음 페이지 존재 여부({@code hasNext})만 확인하면 되므로 Slice로 충분하다.
     */
    @Query("SELECT i FROM Inventory i")
    Slice<Inventory> findAllAsSlice(Pageable pageable);
}
