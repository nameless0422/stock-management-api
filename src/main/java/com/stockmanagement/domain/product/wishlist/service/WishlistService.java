package com.stockmanagement.domain.product.wishlist.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.entity.WishlistItem;
import com.stockmanagement.domain.product.wishlist.repository.WishlistRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
    private final UserRepository userRepository;

    /** 위시리스트에 상품을 추가한다. */
    @Transactional
    public WishlistResponse add(Long productId, String username) {
        User user = findUser(username);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (wishlistRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }

        WishlistItem item = WishlistItem.builder()
                .userId(user.getId())
                .productId(productId)
                .build();

        return toResponse(wishlistRepository.save(item), product);
    }

    /** 위시리스트에서 상품을 제거한다. */
    @Transactional
    public void remove(Long productId, String username) {
        User user = findUser(username);
        WishlistItem item = wishlistRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISHLIST_ITEM_NOT_FOUND));
        wishlistRepository.delete(item);
    }

    /** 특정 상품의 위시리스트 추가 여부를 반환한다 (상품 상세 페이지 하트 아이콘용). */
    public boolean isWishlisted(Long productId, String username) {
        User user = findUser(username);
        return wishlistRepository.existsByUserIdAndProductId(user.getId(), productId);
    }

    /** 사용자의 위시리스트 목록을 조회한다. */
    public List<WishlistResponse> getList(String username) {
        User user = findUser(username);
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (items.isEmpty()) {
            return List.of();
        }

        // N+1 방지: 상품 ID 목록을 한 번에 조회
        List<Long> productIds = items.stream().map(WishlistItem::getProductId).toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 상품이 삭제(soft delete)된 경우 위시리스트 항목을 건너뛴다 — 전체 조회 실패 방지
        return items.stream()
                .filter(item -> productMap.containsKey(item.getProductId()))
                .map(item -> toResponse(item, productMap.get(item.getProductId())))
                .toList();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private WishlistResponse toResponse(WishlistItem item, Product product) {
        return WishlistResponse.of(item, product.getName(), product.getPrice(), product.getThumbnailUrl());
    }
}
