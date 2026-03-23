package com.stockmanagement.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정.
 *
 * <p>도메인 이벤트 리스너(@TransactionalEventListener + @Async)에서 사용하는
 * 전용 스레드 풀을 정의한다. 메인 트랜잭션과 별도 스레드에서 실행되므로
 * 이벤트 처리 실패가 원본 트랜잭션에 영향을 주지 않는다.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "eventTaskExecutor")
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
