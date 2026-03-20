package com.stockmanagement.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis Cache 설정.
 *
 * <p>캐시 전략:
 * <ul>
 *   <li>직렬화: JSON (타입 정보 포함 — 역직렬화 시 정확한 타입 복원)
 *   <li>null 캐싱: 비활성화 (NOT_FOUND 예외가 캐싱되지 않도록)
 *   <li>TTL: 캐시별 상이 — products(10분), inventory(5분), orders(5분)
 * </ul>
 */
@EnableCaching
@Configuration
public class CacheConfig {

    // spring.cache.type=none 프로필(통합 테스트)에서는 빈을 생성하지 않음
    // → Spring Boot auto-configuration이 NoOpCacheManager를 등록
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 타입 정보를 JSON에 포함시켜 역직렬화 시 정확한 클래스로 복원
        ObjectMapper cacheMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY
                );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(cacheMapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(Map.of(
                        // 상품: 변경 빈도 낮음 — 10분
                        "products", base.entryTtl(Duration.ofMinutes(10)),
                        // 재고: 주문마다 변경 → 5분 + 명시적 evict
                        "inventory", base.entryTtl(Duration.ofMinutes(5)),
                        // 주문: 상태 변경 가능 → 5분 + 명시적 evict
                        "orders", base.entryTtl(Duration.ofMinutes(5))
                ))
                .build();
    }
}
