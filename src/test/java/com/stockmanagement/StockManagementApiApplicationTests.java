package com.stockmanagement;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("integration")
class StockManagementApiApplicationTests {

    // 통합 프로파일에서 Redis 연결 없이 컨텍스트 로드 검증
    @MockBean
    RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

}
