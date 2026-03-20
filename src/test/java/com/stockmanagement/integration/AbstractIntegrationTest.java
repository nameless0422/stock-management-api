package com.stockmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.domain.product.document.ProductDocument;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 기반 클래스.
 *
 * <p>Singleton Container 패턴으로 MySQL·Redis 컨테이너를 JVM 당 1회만 시작하고
 * 모든 통합 테스트 클래스가 공유한다. {@code @DynamicPropertySource}가 컨테이너의
 * 동적 포트를 Spring 환경에 주입해 실제 MySQL·Redis 연결을 사용한다.
 *
 * <p>분산 락(Redisson)·캐시(Redis)가 실제 컨테이너에 연결되므로
 * H2 Mock 기반보다 운영 환경에 가까운 통합 테스트를 제공한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
abstract class AbstractIntegrationTest {

    // ===== Singleton Testcontainers =====

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8").withDatabaseName("stock_management");

    @SuppressWarnings({"resource", "rawtypes"})
    static final GenericContainer REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @SuppressWarnings("resource")
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.0")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");

    static {
        MYSQL.start();
        REDIS.start();
        ELASTICSEARCH.start();
    }

    /**
     * 컨테이너의 동적 포트를 Spring 환경 프로퍼티로 주입한다.
     * application-integration.properties의 H2/mock 설정을 오버라이드한다.
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url",                MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",           MYSQL::getUsername);
        registry.add("spring.datasource.password",           MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                     () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto",        () -> "validate");
        registry.add("spring.flyway.enabled",                () -> "true");

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.cache.type",      () -> "redis");

        // Elasticsearch
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    // ===== Spring Beans =====

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired private DataSource dataSource;
    @Autowired private RedissonClient redissonClient;
    @Autowired private ElasticsearchOperations elasticsearchOperations;

    /** 각 테스트 후 모든 테이블을 FK 순서대로 DELETE하여 테스트 간 격리를 보장한다. */
    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // FK 참조 방향: order_items → orders → users / order_items → products ← inventory_transactions ← inventory
            // order_status_history → orders (ON DELETE CASCADE로 자동 삭제되지만 명시적으로 먼저 삭제)
            stmt.execute("DELETE FROM cart_items");
            stmt.execute("DELETE FROM shipments");
            stmt.execute("DELETE FROM order_items");
            stmt.execute("DELETE FROM payments");
            stmt.execute("DELETE FROM order_status_history");
            stmt.execute("DELETE FROM orders");          // delivery_address_id FK → delivery_addresses
            stmt.execute("DELETE FROM delivery_addresses");
            stmt.execute("DELETE FROM inventory_transactions");
            stmt.execute("DELETE FROM inventory");
            stmt.execute("DELETE FROM products");
            stmt.execute("DELETE FROM users");
        }
        // Redis 전체 초기화 — 캐시·rate limit 카운터 테스트 간 격리
        redissonClient.getKeys().flushall();

        // Elasticsearch products 인덱스 초기화 — 테스트 간 격리
        try {
            elasticsearchOperations.indexOps(ProductDocument.class).delete();
            elasticsearchOperations.indexOps(ProductDocument.class).createWithMapping();
        } catch (Exception ignored) { }
    }

    // ===== 공통 헬퍼 =====

    /** 회원가입 후 JWT 토큰을 반환한다. */
    protected String signupAndLogin(String username, String password, String email) throws Exception {
        String signupJson = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\"}",
                username, password, email);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson))
                .andExpect(status().isCreated());
        return login(username, password);
    }

    /** 로그인 후 JWT 토큰을 반환한다. */
    protected String login(String username, String password) throws Exception {
        String loginJson = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    /** ADMIN 사용자를 DB에 직접 저장 후 JWT 토큰을 반환한다. */
    protected String createAdminAndLogin(String username, String password, String email) throws Exception {
        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);
        return login(username, password);
    }
}
