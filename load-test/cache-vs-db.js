/**
 * Cache vs DB 비교 테스트 — Redis 캐시 효과 정량화
 * 실행: k6 run load-test/cache-vs-db.js
 *
 * 시나리오:
 *   Phase 1 (캐시 워밍업): 상품 조회 → Redis에 캐시 적재
 *   Phase 2 (캐시 히트):   동일 엔드포인트 반복 → 캐시에서 응답
 *   Phase 3 (캐시 무효화): admin으로 상품 수정 → 캐시 evict
 *   Phase 4 (캐시 미스):   다시 조회 → DB에서 응답 후 재캐싱
 *
 * 관찰 포인트:
 *   - 캐시 히트: p(95) < 5ms 예상
 *   - 캐시 미스: p(95) > 캐시 히트 (DB 조회 포함)
 *   - 캐시 무효화 후 첫 요청에서 레이턴시 스파이크
 *
 * 참고:
 *   - /api/products/{id} → @Cacheable("products") — 상품 단건 캐시
 *   - PATCH /api/products/{id} → @CacheEvict("products") — 캐시 무효화
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL  = __ENV.BASE_URL  || 'http://localhost:8090';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';

export const options = {
  scenarios: {
    cache_warmup: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      startTime: '0s',
      tags: { phase: 'warmup' },
    },
    cache_hit: {
      executor: 'constant-vus',
      vus: 100,
      duration: '1m',
      startTime: '30s',
      tags: { phase: 'hit' },
    },
    cache_evict_and_miss: {
      executor: 'constant-vus',
      vus: 100,
      duration: '1m',
      startTime: '1m30s',  // cache_hit 끝난 직후
      tags: { phase: 'miss' },
    },
  },
  thresholds: {
    'http_req_duration{phase:hit}':    ['p(95)<30'],   // 캐시 히트: 30ms 이내
    'http_req_duration{phase:miss}':   ['p(95)<200'],  // 캐시 미스: 200ms 이내
    'http_req_duration{phase:warmup}': ['p(95)<500'],  // 워밍업: 500ms 이내
    http_req_failed:                   ['rate<0.01'],
  },
};

const cacheHitTrend  = new Trend('cache_hit_ms', true);
const cacheMissTrend = new Trend('cache_miss_ms', true);
const evictCount     = new Counter('cache_evict_count');

export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: 'testadmin', password: 'Test1234A' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const token = res.json('data.accessToken');
  if (!token) throw new Error('로그인 실패: ' + res.body);

  // 상품 현재 가격 조회
  const prod = http.get(`${BASE_URL}/api/products/${PRODUCT_ID}`);
  const currentPrice = prod.json('data.price') || 10000;
  console.log('상품 현재 가격: ' + currentPrice);

  return { token, originalPrice: currentPrice };
}

export default function (data) {
  const scenario = exec.scenario.name;
  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  };

  if (scenario === 'cache_evict_and_miss') {
    // Phase 3+4: 캐시 무효화 후 조회 (VU 1번만 evict, 나머지는 miss 측정)
    if (__VU === 1 && __ITER < 3) {
      // 상품 수정 → 캐시 evict
      const updateRes = http.put(`${BASE_URL}/api/products/${PRODUCT_ID}`,
        JSON.stringify({
          name: 'Test Product',
          price: data.originalPrice,
          sku: 'TEST-001',
          status: 'ACTIVE',
        }),
        { ...authHeaders, tags: { phase: 'miss' } },
      );
      check(updateRes, { '상품 수정(캐시 evict)': (r) => r.status === 200 });
      evictCount.add(1);
      sleep(0.1);
    }

    // 캐시 미스 후 DB 조회 측정
    const r = http.get(`${BASE_URL}/api/products/${PRODUCT_ID}`,
      { tags: { phase: 'miss' } },
    );
    cacheMissTrend.add(r.timings.duration);
    check(r, { '상품 조회(miss)': (res) => res.status === 200 });

  } else {
    // Phase 1+2: 캐시 워밍업 또는 캐시 히트 측정
    const phase = scenario === 'cache_warmup' ? 'warmup' : 'hit';
    const r = http.get(`${BASE_URL}/api/products/${PRODUCT_ID}`,
      { tags: { phase } },
    );

    if (phase === 'hit') {
      cacheHitTrend.add(r.timings.duration);
    }
    check(r, { '상품 조회': (res) => res.status === 200 });
  }

  sleep(Math.random() * 0.1);
}

export function handleSummary(data) {
  const hitP95  = data.metrics['cache_hit_ms']  ? data.metrics['cache_hit_ms'].values['p(95)']  : 'N/A';
  const missP95 = data.metrics['cache_miss_ms'] ? data.metrics['cache_miss_ms'].values['p(95)'] : 'N/A';

  console.log('\n=== Cache vs DB 비교 결과 ===');
  console.log('캐시 히트 p(95): ' + (typeof hitP95  === 'number' ? hitP95.toFixed(2)  + 'ms' : hitP95));
  console.log('캐시 미스 p(95): ' + (typeof missP95 === 'number' ? missP95.toFixed(2) + 'ms' : missP95));
  if (typeof hitP95 === 'number' && typeof missP95 === 'number') {
    console.log('속도 차이:       ' + (missP95 / hitP95).toFixed(1) + '배 (miss가 hit보다 느림)');
  }

  return {};
}
