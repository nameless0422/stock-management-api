package com.stockmanagement.domain.user.service;

import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 최근 본 상품 서비스 — Redis Sorted Set 기반.
 *
 * <p>key: "recently-viewed:{userId}", score: timestamp(ms), member: productId.
 * 최대 50개 유지, 초과 시 가장 오래된 항목 자동 제거.
 */
@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

    private static final String KEY_PREFIX = "recently-viewed:";
    private static final int MAX_SIZE = 50;

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;

    /**
     * 상품 조회 기록을 저장한다.
     *
     * @param userId    사용자 ID
     * @param productId 조회한 상품 ID
     */
    public void record(Long userId, Long productId) {
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(KEY_PREFIX + userId, LongCodec.INSTANCE);
        set.add(System.currentTimeMillis(), productId);

        // 최대 크기 초과 시 가장 오래된 항목 제거
        int size = set.size();
        if (size > MAX_SIZE) {
            set.removeRangeByRank(0, size - MAX_SIZE - 1);
        }
    }

    /**
     * 최근 본 상품 목록을 조회한다 (최신순).
     *
     * @param userId 사용자 ID
     * @param size   조회할 개수 (최대 50)
     * @return 최근 본 상품 목록 (삭제된 상품 제외)
     */
    public List<ProductResponse> getRecentlyViewed(Long userId, int size) {
        int limit = Math.min(size, MAX_SIZE);
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(KEY_PREFIX + userId, LongCodec.INSTANCE);

        // 최신순으로 limit개 조회 (높은 score = 최신)
        Collection<Long> productIds = set.valueRangeReversed(0, limit - 1);
        if (productIds.isEmpty()) {
            return List.of();
        }

        // 배치 조회 후 순서 유지
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return productIds.stream()
                .filter(productMap::containsKey)
                .map(id -> ProductResponse.from(productMap.get(id)))
                .toList();
    }
}
