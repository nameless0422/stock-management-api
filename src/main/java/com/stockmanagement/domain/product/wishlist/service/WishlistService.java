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

    /** 사용자의 위시리스트 목록을 조회한다. */
    public List<WishlistResponse> getList(String username) {
        User user = findUser(username);
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
                    return toResponse(item, product);
                })
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
