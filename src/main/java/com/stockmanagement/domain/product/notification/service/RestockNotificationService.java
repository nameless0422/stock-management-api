package com.stockmanagement.domain.product.notification.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.notification.dto.RestockNotificationResponse;
import com.stockmanagement.domain.product.notification.entity.RestockNotification;
import com.stockmanagement.domain.product.notification.repository.RestockNotificationRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestockNotificationService {

    private final RestockNotificationRepository restockNotificationRepository;
    private final ProductRepository productRepository;

    @Transactional
    public RestockNotificationResponse subscribe(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (restockNotificationRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new BusinessException(ErrorCode.RESTOCK_ALREADY_SUBSCRIBED);
        }

        RestockNotification notification = restockNotificationRepository.save(
                RestockNotification.builder()
                        .userId(userId)
                        .productId(productId)
                        .build()
        );

        return RestockNotificationResponse.of(notification, product);
    }

    @Transactional
    public void unsubscribe(Long userId, Long productId) {
        restockNotificationRepository.deleteByUserIdAndProductId(userId, productId);
    }

    /** 재입고 이벤트 처리 — 구독자 userId 목록 조회 후 해당 상품 구독 일괄 삭제. */
    @Transactional
    public List<Long> consumeSubscribers(Long productId) {
        List<Long> userIds = restockNotificationRepository.findUserIdsByProductId(productId);
        if (!userIds.isEmpty()) {
            restockNotificationRepository.deleteByProductId(productId);
        }
        return userIds;
    }

    public List<RestockNotificationResponse> getMyNotifications(Long userId) {
        List<RestockNotification> notifications =
                restockNotificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (notifications.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = notifications.stream()
                .map(RestockNotification::getProductId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return notifications.stream()
                .filter(n -> productMap.containsKey(n.getProductId()))
                .map(n -> RestockNotificationResponse.of(n, productMap.get(n.getProductId())))
                .toList();
    }
}
