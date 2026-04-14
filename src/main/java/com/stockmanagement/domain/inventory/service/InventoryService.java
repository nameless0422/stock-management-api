package com.stockmanagement.domain.inventory.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.lock.DistributedLock;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.inventory.dto.InventoryAdjustRequest;
import com.stockmanagement.domain.inventory.dto.InventoryReceiveRequest;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.dto.InventorySearchRequest;
import com.stockmanagement.domain.inventory.dto.InventoryTransactionResponse;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.entity.InventoryTransaction;
import com.stockmanagement.domain.inventory.entity.InventoryTransactionType;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.inventory.repository.InventorySpecification;
import com.stockmanagement.domain.inventory.repository.InventoryTransactionRepository;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.event.LowStockEvent;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 재고 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 로 개별 오버라이드
 * </ul>
 *
 * <p>동시성 2중 제어:
 * <ul>
 *   <li>분산 락({@link DistributedLock}): 멀티 인스턴스 환경에서 Redis를 통한 외부 락
 *   <li>비관적 락({@code findByProductIdWithLock}): 단일 인스턴스 DB 레벨 보조 락
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemSettingService systemSettingService;

    /**
     * 재고 목록을 조건 필터링 + 페이지네이션으로 조회한다.
     * 모든 필터 조건은 선택적이며, AND로 결합된다.
     *
     * @param request  status / minAvailable / maxAvailable / productName / category 필터
     * @param pageable page / size / sort
     */
    public Page<InventoryResponse> search(InventorySearchRequest request, Pageable pageable) {
        return inventoryRepository.findAll(InventorySpecification.of(request), pageable)
                .map(InventoryResponse::from);
    }

    /**
     * 상품의 재고 현황을 조회한다.
     * 재고 레코드가 없으면 404 예외를 발생시킨다.
     * 결과를 Redis에 캐싱한다 (TTL: 5분, write 시 명시적 evict).
     */
    @Cacheable(cacheNames = "inventory", key = "#productId")
    public InventoryResponse getByProductId(Long productId) {
        return InventoryResponse.from(findByProductId(productId));
    }

    /**
     * 상품의 재고 변동 이력을 커서 기반으로 최신순 조회한다.
     *
     * <p>오프셋 페이지네이션 대신 {@code WHERE id < :lastId} 커서 방식을 사용하여
     * 페이지 번호가 늘어나도 앞 레코드를 스캔하지 않는다.
     *
     * @param productId 조회 대상 상품 ID
     * @param lastId    이전 페이지의 마지막 항목 ID (첫 조회 시 null)
     * @param size      한 페이지 항목 수
     */
    public CursorPage<InventoryTransactionResponse> getTransactions(Long productId, Long lastId, int size) {
        findByProductId(productId); // 재고 존재 여부 확인
        PageRequest limit = PageRequest.of(0, size + 1);
        var items = lastId == null
                ? transactionRepository.findByInventoryProductIdOrderByIdDesc(productId, limit)
                : transactionRepository.findByInventoryProductIdAndIdLessThanOrderByIdDesc(productId, lastId, limit);
        return CursorPage.of(
                items.stream().map(InventoryTransactionResponse::from).toList(),
                size,
                InventoryTransactionResponse::getId);
    }

    /**
     * 상품에 재고를 입고한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>분산 락 획득
     *   <li>상품 존재 여부 확인
     *   <li>재고 레코드가 없으면 자동 생성 (상품 등록 후 첫 입고)
     *   <li>비관적 락으로 재고 행을 잠근 뒤 onHand 증가
     *   <li>이력 기록
     * </ol>
     *
     * @param productId 입고 대상 상품 ID
     * @param request   입고 수량 및 메모
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "inventory", key = "#productId"),
            @CacheEvict(cacheNames = "products",  key = "#productId")
    })
    public InventoryResponse receive(Long productId, InventoryReceiveRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder().product(product).build()
                ));

        inventory.receive(request.getQuantity());
        recordTransaction(inventory, InventoryTransactionType.RECEIVE, request.getQuantity(), request.getNote());
        return InventoryResponse.from(inventory);
    }

    /**
     * 관리자 수동 재고 조정.
     *
     * @param productId 조정 대상 상품 ID
     * @param request   조정 수량(양수/음수) 및 사유
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "inventory", key = "#productId"),
            @CacheEvict(cacheNames = "products",  key = "#productId")
    })
    public InventoryResponse adjust(Long productId, InventoryAdjustRequest request) {
        if (request.getQuantity() == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.adjust(request.getQuantity());
        recordTransaction(inventory, InventoryTransactionType.ADJUSTMENT, request.getQuantity(), request.getNote());
        return InventoryResponse.from(inventory);
    }

    // ===== 내부 전용 메서드 (Order/Payment 도메인에서 호출) =====

    /**
     * 주문 생성 시 재고를 예약한다.
     * 가용 재고가 부족하면 {@link com.stockmanagement.common.exception.InsufficientStockException}이 발생한다.
     *
     * <p>동시성 이중 방어:
     * <ul>
     *   <li>@DistributedLock(Redisson) — 멀티 인스턴스 간 상호 배제 (외부 락)
     *   <li>findByProductIdWithLock(SELECT FOR UPDATE) — 단일 DB 트랜잭션 내 비관적 락 (내부 락)
     * </ul>
     * Redis 장애 시 Redisson 락이 누락되더라도 DB 비관적 락이 최후 방어선으로 동작한다.
     * releaseReservation(), allocate(), releaseAllocation()도 동일한 이중 락 구조를 따른다.
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "inventory", key = "#productId"),
            @CacheEvict(cacheNames = "products",  key = "#productId")
    })
    public void reserve(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        int availableBefore = inventory.getAvailable() + quantity; // reserve 전 가용 재고
        inventory.reserve(quantity);
        recordTransaction(inventory, InventoryTransactionType.RESERVE, quantity);

        // 저재고 경보 — 임계값을 처음 하향 돌파하는 순간에만 발행 (중복 이메일 방지)
        // systemSettingService 캐시(1시간 TTL)로 DB 조회 부담 없음
        int threshold = systemSettingService.getLowStockThreshold();
        if (availableBefore >= threshold && inventory.getAvailable() < threshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    productId,
                    inventory.getProduct().getName(),
                    inventory.getAvailable()));
        }
    }

    /** 주문 취소 또는 결제 실패 시 예약을 해제한다. */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "inventory", key = "#productId"),
            @CacheEvict(cacheNames = "products",  key = "#productId")
    })
    public void releaseReservation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        if (inventory.getReserved() < quantity) {
            // 음수 reserved는 available 계산을 오염시켜 품절 상품이 주문 가능 상태로 노출됨
            // 경고 후 진행하지 않고 즉시 예외를 던져 데이터 정합성 위반을 빠르게 감지한다
            throw new BusinessException(ErrorCode.INVENTORY_STATE_INCONSISTENT,
                    String.format("[Inventory] reserved 불일치: productId=%d, reserved=%d, 해제요청=%d",
                            productId, inventory.getReserved(), quantity));
        }
        inventory.releaseReservation(quantity);
        recordTransaction(inventory, InventoryTransactionType.RELEASE_RESERVATION, quantity);
    }

    /** 결제 완료 시 예약을 확정(allocated)으로 전환한다. */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "inventory", key = "#productId"),
            @CacheEvict(cacheNames = "products",  key = "#productId")
    })
    public void confirmAllocation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        inventory.confirmAllocation(quantity);
        recordTransaction(inventory, InventoryTransactionType.CONFIRM_ALLOCATION, quantity);
    }

    /**
     * 결제 취소(환불) 시 확정 재고를 해제한다.
     * allocated를 감소시켜 available을 복구한다.
     */
    @DistributedLock(key = "'inventory:' + #productId")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "inventory", key = "#productId"),
            @CacheEvict(cacheNames = "products",  key = "#productId")
    })
    public void releaseAllocation(Long productId, int quantity) {
        Inventory inventory = findByProductIdWithLock(productId);
        if (inventory.getAllocated() < quantity) {
            throw new BusinessException(ErrorCode.INVENTORY_STATE_INCONSISTENT,
                    String.format("[Inventory] allocated 불일치: productId=%d, allocated=%d, 해제요청=%d",
                            productId, inventory.getAllocated(), quantity));
        }
        inventory.releaseAllocation(quantity);
        recordTransaction(inventory, InventoryTransactionType.RELEASE_ALLOCATION, quantity);
    }

    // ===== private 헬퍼 =====

    private Inventory findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    /** 비관적 락으로 재고를 조회한다 — 쓰기 작업 전용 */
    private Inventory findByProductIdWithLock(Long productId) {
        return inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    /** 재고 뮤테이션 이후 이력을 기록한다 (note 없음). */
    private void recordTransaction(Inventory inventory, InventoryTransactionType type, int quantity) {
        recordTransaction(inventory, type, quantity, null);
    }

    /** 재고 뮤테이션 이후 이력을 기록한다 (note 포함). */
    private void recordTransaction(Inventory inventory, InventoryTransactionType type, int quantity, String note) {
        transactionRepository.save(
                InventoryTransaction.builder()
                        .inventory(inventory)
                        .type(type)
                        .quantity(quantity)
                        .note(note)
                        .build()
        );
    }
}
