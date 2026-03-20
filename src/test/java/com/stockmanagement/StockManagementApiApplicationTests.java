package com.stockmanagement;

import com.stockmanagement.domain.product.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration"
})
@ActiveProfiles("integration")
class StockManagementApiApplicationTests {

    // 통합 프로파일에서 Redis/Elasticsearch 연결 없이 컨텍스트 로드 검증
    @MockBean
    RedissonClient redissonClient;

    @MockBean
    ProductSearchService productSearchService;

    @Test
    void contextLoads() {
    }

}
