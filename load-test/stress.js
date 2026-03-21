/**
 * Stress Test — 점진적 부하 증가로 MySQL/Redis 한계 파악
 * 실행: k6 run load-test/stress.js
 *
 * 시나리오:
 *   - 0 → 100 VU → 200 VU → 300 VU → 0 (단계적 증가)
 *   - 70% 상품/재고 조회 (Redis 캐시)
 *   - 20% 주문 생성 (MySQL + Redisson 분산락)
 *   - 10% 주문 목록 조회
 *
 * 관찰 포인트:
 *   - p(95) 레이턴시가 어느 VU 구간에서 튀는지
 *   - 오류율이 올라가기 시작하는 VU 수 (=임계점)
 *   - DB 락 경합으로 인한 쓰기 레이턴시 급증 구간
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';

export const options = {
  stages: [
    { duration: '30s', target: 50  },  // 워밍업
    { duration: '1m',  target: 100 },  // 1단계
    { duration: '1m',  target: 150 },  // 2단계
    { duration: '1m',  target: 200 },  // 3단계
    { duration: '1m',  target: 250 },  // 4단계 (한계 탐색)
    { duration: '1m',  target: 300 },  // 5단계 (한계 탐색)
    { duration: '30s', target: 0   },  // 쿨다운
  ],
  thresholds: {
    http_req_duration:               ['p(95)<3000'],  // 임계 3초 (초과시 FAIL)
    'http_req_duration{type:read}':  ['p(95)<1000'],  // 읽기 1초
    'http_req_duration{type:write}': ['p(95)<5000'],  // 쓰기 5초
    http_req_failed:                 ['rate<0.05'],   // 실 오류 5% 미만
  },
};

const writeTrend  = new Trend('write_duration', true);
const readTrend   = new Trend('read_duration', true);
const orderErrors = new Counter('order_errors');
const orderOk     = new Counter('order_success');

export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: 'testadmin', password: 'Test1234A' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const token = res.json('data.accessToken');
  if (!token) throw new Error('로그인 실패: ' + res.body);
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

  if (roll < 0.40) {
    // 40%: 상품 목록 (Redis 캐시)
    const r = http.get(`${BASE_URL}/api/products`, { tags: { type: 'read' } });
    readTrend.add(r.timings.duration);
    check(r, { 'products 200': (res) => res.status === 200 });

  } else if (roll < 0.70) {
    // 30%: 재고 조회 (Redis 캐시 + DB JPA Criteria)
    const r = http.get(`${BASE_URL}/api/inventory`,
      { ...authHeaders, tags: { type: 'read' } },
    );
    readTrend.add(r.timings.duration);
    check(r, { 'inventory 200': (res) => res.status === 200 });

  } else if (roll < 0.90) {
    // 20%: 주문 생성 (MySQL write + Redisson 분산락)
    const r = http.post(`${BASE_URL}/api/orders`,
      JSON.stringify({
        userId: 3,
        items: [{ productId: 1, quantity: 1, unitPrice: 10000 }],
        idempotencyKey: `stress-${__VU}-${__ITER}-${Date.now()}`,
      }),
      {
        ...authHeaders,
        tags: { type: 'write' },
        responseCallback: http.expectedStatuses(201, 400, 409, 429),
      },
    );
    writeTrend.add(r.timings.duration);
    if (r.status === 201) {
      orderOk.add(1);
      check(r, { '주문 성공': (res) => res.status === 201 });
    } else if (r.status === 400 || r.status === 409) {
      check(r, { '재고 부족 거부': (res) => res.status === 400 || res.status === 409 });
    } else if (r.status === 429) {
      check(r, { 'Rate Limit': (res) => res.status === 429 });
    } else {
      orderErrors.add(1);
      check(r, { '서버 오류 없음': (res) => res.status < 500 });
    }

  } else {
    // 10%: 주문 목록 조회
    const r = http.get(`${BASE_URL}/api/orders`,
      { ...authHeaders, tags: { type: 'read' } },
    );
    readTrend.add(r.timings.duration);
    check(r, { 'orders 200': (res) => res.status === 200 });
  }

  sleep(Math.random() * 0.3);  // 0~0.3초 랜덤 대기 (부하 분산)
}
