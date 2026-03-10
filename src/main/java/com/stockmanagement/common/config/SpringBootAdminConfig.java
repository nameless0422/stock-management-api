package com.stockmanagement.common.config;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot Admin 서버 활성화.
 * 접속: /admin-ui (form login 필요 — AdminSecurityConfig 참고)
 */
@Configuration
@EnableAdminServer
public class SpringBootAdminConfig {
}
