package com.stockmanagement.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 설정.
 *
 * <p>{@code @EnableJpaAuditing}을 별도 클래스에 분리하여 {@code @WebMvcTest} 슬라이스 테스트 시
 * JPA 컨텍스트 없이도 로딩 오류가 발생하지 않도록 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
