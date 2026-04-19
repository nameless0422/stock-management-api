package com.stockmanagement.domain.product.wishlist.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.entity.WishlistItem;
import com.stockmanagement.domain.product.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * 위시리스트에 상품을 추가한다.
     *
     * <p>userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    @Transactional
    public WishlistResponse add(Long productId, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }

        WishlistItem item = WishlistItem.builder()
                .userId(userId)
                .productId(productId)
                .build();

        Integer available = inventoryRepository.findByProductId(productId)
                .map(Inventory::getAvailable)
                .orElse(null);
        return WishlistResponse.of(wishlistRepository.save(item), product, available);
    }

    /**
     * 위시리스트에서 상품을 제거한다.
     *
     * <p>userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    @Transactional
    public void remove(Long productId, Long userId) {
        WishlistItem item = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISHLIST_ITEM_NOT_FOUND));
        wishlistRepository.delete(item);
    }

    /**
     * 특정 상품의 위시리스트 추가 여부를 반환한다 (상품 상세 페이지 하트 아이콘용).
     *
     * <p>userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    public boolean isWishlisted(Long productId, Long userId) {
        return wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    /**
     * 사용자의 위시리스트 목록을 페이징 조회한다.
     *
     * <p>userId는 JWT claim에서 추출한 값을 컨트롤러에서 전달받아 DB users 조회를 생략한다.
     */
    public Page<WishlistResponse> getList(Long userId, Pageable pageable) {
        Page<WishlistItem> page = wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        if (page.isEmpty()) {
            return page.map(item -> null); // 빈 페이지 반환
        }

        // N+1 방지: 상품 + 재고를 한 번에 배치 조회
        List<Long> productIds = page.map(WishlistItem::getProductId).toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Integer> availableMap = inventoryRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Inventory::getAvailable));

        // 상품이 삭제(soft delete)된 경우 null로 매핑 후 필터링 — Spring Data Page는 filter 미지원
        // → toList()로 materialized 후 PageImpl로 래핑
        List<WishlistResponse> content = page.stream()
                .filter(item -> productMap.containsKey(item.getProductId()))
                .map(item -> WishlistResponse.of(
                        item,
                        productMap.get(item.getProductId()),
                        availableMap.get(item.getProductId())))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, page.getTotalElements());
    }
}
