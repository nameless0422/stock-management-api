package com.stockmanagement.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtAuthenticationFilter;
import com.stockmanagement.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklist jwtBlacklist;
    private final ObjectMapper objectMapper;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public 엔드포인트 — 로그아웃은 인증 필요 (permitAll 범위에서 제외)
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payments/webhook").permitAll()
                        // Actuator — health/info/prometheus는 공개 (내부망 전용), 나머지는 ADMIN 전용
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Swagger UI
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // 관리자 랜딩 페이지 (정적 파일 — API 인증은 JS에서 JWT로 처리)
                        .requestMatchers("/admin-page/**").permitAll()
                        // 쇼핑몰 페이지 (정적 파일 — 비로그인 접근 허용)
                        .requestMatchers("/shop/**").permitAll()
                        // 결제 테스트 페이지
                        .requestMatchers("/payment-test.html").permitAll()
                        // 상품 조회 — 비로그인 허용 (쇼핑몰 특성상 누구나 열람 가능)
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        // 상품 이미지 — Presigned URL 발급·저장·삭제는 ADMIN 전용
                        .requestMatchers(HttpMethod.POST, "/api/products/*/images/presigned").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/*/images").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/images/**").hasRole("ADMIN")
                        // 리뷰 작성/삭제 — 일반 사용자 인증 필요 (ADMIN 패턴보다 먼저 선언)
                        .requestMatchers(HttpMethod.POST, "/api/products/*/reviews").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/reviews/**").authenticated()
                        // ADMIN 전용
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/**").hasRole("ADMIN")
                        // 내 배송 목록 조회 — 인증된 사용자 전용 (ADMIN PATCH 패턴보다 앞에 선언)
                        .requestMatchers(HttpMethod.GET, "/api/shipments/my").authenticated()
                        // 배송 상태 변경 (출고/완료/반품)은 ADMIN 전용
                        .requestMatchers(HttpMethod.PATCH, "/api/shipments/**").hasRole("ADMIN")
                        // 카테고리 조회 공개, 관리 (생성/수정/삭제)는 ADMIN 전용
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        // 내 쿠폰 목록 — USER 인증 필요 (ADMIN 쿠폰 패턴보다 먼저 선언)
                        .requestMatchers(HttpMethod.GET, "/api/coupons/my").authenticated()
                        // 쿠폰 관리 (생성/발급/비활성화)는 ADMIN 전용
                        .requestMatchers(HttpMethod.POST, "/api/coupons").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/coupons/*/issue").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/coupons/**").hasRole("ADMIN")
                        // 쿠폰 목록/상세 — ADMIN 전용
                        .requestMatchers(HttpMethod.GET, "/api/coupons", "/api/coupons/**").hasRole("ADMIN")
                        // 리뷰 조회는 공개, 작성/삭제는 인증 필요 (authenticated() 로 처리)
                        .requestMatchers(HttpMethod.GET, "/api/products/*/reviews").permitAll()
                        // 포인트/위시리스트/환불은 인증 필요 (authenticated() 로 처리)
                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )
                .headers(headers -> {
                        // 클릭재킹 방지 — iframe 삽입 차단
                        headers.frameOptions(frame -> frame.deny());
                        // MIME 스니핑 방지 — Content-Type 무시하는 브라우저 동작 차단
                        headers.contentTypeOptions(cto -> {});
                        // HTTPS 강제 (1년) — HTTP로 접근해도 HTTPS로 리다이렉트
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000));
                        // Referrer 노출 최소화 — 외부 도메인 이동 시 origin만 전송
                        headers.referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                        // 브라우저 기능 제한 — 위치/마이크/카메라 API 차단
                        headers.addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()"));
                        // CSP — 자체 출처 리소스만 허용 (Swagger UI·어드민 SPA 인라인 스크립트 허용)
                        // 주의: Swagger UI 5.x 이상은 unsafe-eval 없이 동작. 구버전 사용 시 재확인 필요.
                        headers.contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'"));
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            ApiResponse.error(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")));
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            ApiResponse.error(ErrorCode.FORBIDDEN, "접근 권한이 없습니다.")));
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtBlacklist),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
