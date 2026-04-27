package com.stockmanagement.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 분산 락 클라이언트 설정.
 *
 * <p>단일 서버 모드로 Redis에 연결한다.
 * 멀티 인스턴스 환경에서는 Redis Cluster 또는 Sentinel 모드로 전환한다.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                // 연결 타임아웃: 2초 (기본값 10초는 너무 길어 장애 전파 위험)
                .setConnectTimeout(2000)
                // 응답 타임아웃: 3초
                .setTimeout(3000)
                // 재시도: 3회, 간격 1.5초
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                // 연결 풀: 최대 10개, 최소 idle 2개
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2);
        return Redisson.create(config);
    }
}
