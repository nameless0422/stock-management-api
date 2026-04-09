package com.stockmanagement.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 비동기/스케줄러 스레드 풀 설정.
 *
 * <p>이벤트 리스너(@TransactionalEventListener + @Async)와 스케줄러(@Scheduled)가
 * 각자 전용 스레드 풀에서 실행되도록 분리한다.
 *
 * <p>두 빈 모두 Micrometer가 자동 계측하여 executor.* 메트릭으로 노출된다.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * 도메인 이벤트 처리 전용 스레드 풀.
     * 반환 타입을 ThreadPoolTaskExecutor로 선언해야 Micrometer 자동 계측이 적용된다.
     */
    @Bean(name = "eventTaskExecutor")
    public ThreadPoolTaskExecutor eventTaskExecutor() {
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

    /**
     * 스케줄러 전용 스레드 풀.
     * "taskScheduler" 이름으로 등록하면 Spring이 @Scheduled 메서드에 자동 적용한다.
     * 단일 스레드(scheduler-1) 대신 여러 스케줄러가 병렬 실행되어 하나가 지연돼도 다른 스케줄러에 영향이 없다.
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
