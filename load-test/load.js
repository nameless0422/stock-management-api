/**
 * Load Test — 일반 부하 테스트 (점진적 증가 → 유지 → 감소)
 * 실행: k6 run load-test/load.js
 *
 * 시나리오:
 *   - 70% 상품 목록 조회 (Redis 캐시 효과 측정)
 *   - 20% 재고/주문 조회
 *   - 10% 주문 생성
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // 워밍업
    { duration: '2m',  target: 50 },   // 목표 부하 유지
    { duration: '30s', target: 0 },    // 쿨다운
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],    // 실제 오류율 1% 미만 (400/409는 expectedStatuses 처리)
    http_req_duration: ['p(95)<1000'],   // 95% 응답 1초 이내
    'http_req_duration{type:read}':  ['p(95)<500'],   // 읽기 500ms
    'http_req_duration{type:write}': ['p(95)<2000'],  // 쓰기 2초
  },
};

const orderErrors = new Counter('order_errors');
const orderSuccess = new Counter('order_success');
const cacheHitRate = new Rate('cache_hit');

export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: 'testadmin', password: 'Test1234A' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const token = res.json('data.accessToken');
  if (!token) throw new Error(`로그인 실패: ${res.body}`);
  return { token };
}

export default function (data) {
  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  };

  const roll = Math.random();

  if (roll < 0.70) {
    // 70%: 상품 목록 조회 — 캐시 효과 측정
    const res = http.get(`${BASE_URL}/api/products`, {
      tags: { type: 'read' },
    });
    const ok = check(res, { 'products ok': (r) => r.status === 200 });
    // 응답 시간이 50ms 미만이면 캐시 히트로 간주
    cacheHitRate.add(res.timings.duration < 50);

  } else if (roll < 0.90) {
    // 20%: 재고 및 주문 조회
    const inv = http.get(`${BASE_URL}/api/inventory`, {
      ...authHeaders,
      tags: { type: 'read' },
    });
    check(inv, { 'inventory ok': (r) => r.status === 200 });

    const orders = http.get(`${BASE_URL}/api/orders`, {
      ...authHeaders,
      tags: { type: 'read' },
    });
    check(orders, { 'orders ok': (r) => r.status === 200 });

  } else {
    // 10%: 주문 생성 (쓰기)
    const res = http.post(`${BASE_URL}/api/orders`,
      JSON.stringify({
        userId: 3,  // testadmin id
        items: [{ productId: 1, quantity: 1, unitPrice: 10000 }],
        idempotencyKey: `load-test-${__VU}-${__ITER}-${Date.now()}`,
      }),
      {
        ...authHeaders,
        tags: { type: 'write' },
        // 400(재고 부족)은 예상된 응답 — http_req_failed 집계에서 제외
        responseCallback: http.expectedStatuses(201, 400, 409),
      },
    );
    const ok = check(res, {
      'order created or stock error': (r) => r.status === 201 || r.status === 400,
    });
    if (res.status === 201) {
      orderSuccess.add(1);
    } else {
      orderErrors.add(1);
    }
  }

  sleep(Math.random() * 0.5 + 0.1); // 0.1 ~ 0.6초 랜덤 대기
}

export function teardown(data) {
  console.log('Load test 완료');
}
