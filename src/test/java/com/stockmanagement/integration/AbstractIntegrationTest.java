package com.stockmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 기반 클래스.
 *
 * <p>H2 in-memory DB(MySQL 호환 모드)를 사용하며, @AfterEach에서
 * TRUNCATE TABLE로 테스트 간 데이터 격리를 보장한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
abstract class AbstractIntegrationTest {

    // RedisConfig의 실제 RedissonClient 대신 Mock으로 대체 — 통합 테스트에서 Redis 불필요
    @MockBean
    protected RedissonClient redissonClient;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    /**
     * DistributedLockAspect가 RedissonClient를 사용하므로 Mock 동작을 설정한다.
     * @MockBean은 테스트 메서드마다 초기화되므로 @BeforeEach에서 매번 stub을 설정한다.
     */
    @BeforeEach
    void setUpRedissonMock() throws InterruptedException {
        RLock mockLock = mock(RLock.class);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        lenient().when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(mockLock.isHeldByCurrentThread()).thenReturn(true);
    }

    /** 각 테스트 후 모든 테이블을 FK 순서대로 DELETE하여 테스트 간 격리를 보장한다. */
    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // FK 참조 방향: order_items → orders → users / order_items → products ← inventory_transactions ← inventory
            stmt.execute("DELETE FROM order_items");
            stmt.execute("DELETE FROM payments");
            stmt.execute("DELETE FROM orders");
            stmt.execute("DELETE FROM inventory_transactions");
            stmt.execute("DELETE FROM inventory");
            stmt.execute("DELETE FROM products");
            stmt.execute("DELETE FROM users");
        }
    }

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
