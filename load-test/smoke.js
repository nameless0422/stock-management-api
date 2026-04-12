/**
 * Smoke Test — 기본 동작 및 응답 검증 (1 VU, 30초)
 * 실행: k6 run load-test/smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],      // 오류율 1% 미만
    http_req_duration: ['p(95)<500'],    // 95% 응답 500ms 이내
  },
};

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

  // 1. 상품 목록 조회 (공개 API)
  const products = http.get(`${BASE_URL}/api/products`);
  check(products, {
    'products 200': (r) => r.status === 200,
    'products success': (r) => r.json('success') === true,
  });

  // 2. 카테고리 목록 조회
  const categories = http.get(`${BASE_URL}/api/categories`);
  check(categories, {
    'categories 200': (r) => r.status === 200,
  });

  // 3. 재고 조회 (인증 필요)
  const inventory = http.get(`${BASE_URL}/api/inventory`, authHeaders);
  check(inventory, {
    'inventory 200': (r) => r.status === 200,
  });

  // 4. 주문 목록 조회
  const orders = http.get(`${BASE_URL}/api/orders`, authHeaders);
  check(orders, {
    'orders 200': (r) => r.status === 200,
  });

  sleep(1);
}
