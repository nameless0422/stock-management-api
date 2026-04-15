package com.stockmanagement.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Boot Admin UI 전용 시큐리티 체인 (@Order(1)).
 *
 * <p>JWT 방식을 사용하는 API 체인과 분리하여 form login + 세션 기반으로 동작한다.
 * SBA 클라이언트의 /instances 등록 요청은 HTTP Basic Auth로 처리된다.
 *
 * <ul>
 *   <li>경로: /admin-ui/**, /instances/**
 *   <li>로그인: /admin-ui/login (form)
 *   <li>계정: application.properties의 admin.username / admin.password
 * </ul>
 */
@Configuration
@Order(1)
public class AdminSecurityConfig {

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:changeme}")
    private String adminPassword;

    /**
     * 운영 배포 시 기본 자격증명 사용 방지.
     *
     * <p>ADMIN_USERNAME / ADMIN_PASSWORD 환경변수가 모두 기본값인 경우 시작을 즉시 실패시킨다.
     *
     * @throws IllegalStateException 기본 자격증명이 그대로 사용되는 경우
     */
    @PostConstruct
    public void validateCredentials() {
        if ("admin".equals(adminUsername) && "changeme".equals(adminPassword)) {
            throw new IllegalStateException(
                    "[SECURITY] ADMIN_USERNAME / ADMIN_PASSWORD가 기본값(admin/changeme)입니다. " +
                    "운영 환경에서는 환경변수를 반드시 설정하세요.");
        }
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
        // SBA UI 로그인 성공 시 대시보드로 이동
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl("/admin-ui/");

        // 인메모리 ADMIN 계정 (SBA UI 전용)
        UserDetails adminUser = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        InMemoryUserDetailsManager detailsManager = new InMemoryUserDetailsManager(adminUser);

        http
                .securityMatcher("/admin-ui/**")
                .authorizeHttpRequests(auth -> auth
                        // SBA UI 정적 자원과 로그인 페이지는 공개
                        .requestMatchers("/admin-ui/assets/**", "/admin-ui/login").permitAll()
                        .anyRequest().hasRole("ADMIN")
                )
                // SBA UI: form login
                .formLogin(form -> form
                        .loginPage("/admin-ui/login")
                        .successHandler(successHandler))
                .logout(logout -> logout.logoutUrl("/admin-ui/logout"))
                // SBA 클라이언트 자기 등록(/instances): HTTP Basic Auth
                .httpBasic(Customizer.withDefaults())
                // CSRF: SBA UI에서 필요 (쿠키 기반), 클라이언트 등록(/instances)은 제외
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/admin-ui/instances/**"))
                .userDetailsService(detailsManager);

        return http.build();
    }
}
