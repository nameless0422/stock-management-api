/**
 * Concurrent Orders Test — 동시성 제어 검증 (핵심 테스트)
 * 실행: k6 run load-test/concurrent-orders.js
 *
 * 목적:
 *   - 재고 50개 상품에 100명이 동시에 주문 → available 음수 불가 검증
 *   - 분산 락(Redisson) + 비관적 락 동작 확인
 *   - 초과 주문(51번째~)은 400 에러로 정상 거부
 *
 * 사전 준비:
 *   - DB에 loaduser0~99 계정 필요 (password: Test1234A, role: USER)
 *   - 재고 설정: on_hand=50, reserved=0, allocated=0
 *   - 실행 전 Redis rate-limit 키 초기화 권장
 *
 * 성공 기준:
 *   - 성공 주문 수 ≤ STOCK_QTY (50)
 *   - 재고 available >= 0 (음수 절대 불가)
 *   - 500 서버 에러 0건
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const CONCURRENT_USERS = 100;
const STOCK_QTY = 50;
const USER_PASSWORD = 'Test1234A';
const UNIT_PRICE = 10000;
const PRODUCT_ID = 1;

export const options = {
  scenarios: {
    concurrent_burst: {
      executor: 'shared-iterations',
      vus: CONCURRENT_USERS,
      iterations: CONCURRENT_USERS,
      maxDuration: '3m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],  // 실 오류(500) 1% 미만
  },
};

const successCount = new Counter('order_success');
const rejectCount  = new Counter('order_rejected_stock');
const rateLimitCount = new Counter('order_rejected_ratelimit');
const errorCount   = new Counter('order_server_error');

/** setup: admin 토큰 + 각 VU용 loaduser 토큰 사전 발급 */
export function setup() {
  // admin 로그인
  const adminRes = http.post(BASE_URL + '/api/auth/login',
    JSON.stringify({ username: 'testadmin', password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const adminToken = adminRes.json('data.accessToken');
  if (!adminToken) throw new Error('Admin 로그인 실패: ' + adminRes.body);

  const adminHeaders = {
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + adminToken },
  };

  // 재고를 STOCK_QTY로 조정
  const invRes = http.get(BASE_URL + '/api/inventory?productId=' + PRODUCT_ID, adminHeaders);
  const currentAvailable = invRes.json('data.content.0.available');
  const delta = STOCK_QTY - currentAvailable;
  console.log('현재 available: ' + currentAvailable + ', 목표: ' + STOCK_QTY + ', delta: ' + delta);
  if (delta !== 0) {
    const adjustRes = http.post(BASE_URL + '/api/inventory/' + PRODUCT_ID + '/adjust',
      JSON.stringify({ quantity: delta, note: 'concurrent-test 재고 조정' }),
      adminHeaders,
    );
    check(adjustRes, { '재고 조정 성공': (r) => r.status === 200 });
    console.log('재고 조정 완료. available: ' + adjustRes.json('data.available'));
  }

  // loaduser0~99 로그인 (각 VU당 하나의 고유 토큰)
  const tokens = [];
  const userIds = [];
  for (let i = 0; i < CONCURRENT_USERS; i++) {
    const username = 'loaduser' + i;
    const res = http.post(BASE_URL + '/api/auth/login',
      JSON.stringify({ username, password: USER_PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    const token = res.json('data.accessToken');
    if (token) {
      tokens.push(token);
    } else {
      console.warn('로그인 실패: ' + username + ' (' + res.status + ')');
      tokens.push(adminToken); // fallback
    }
  }

  console.log('준비 완료: ' + tokens.length + '명 토큰 발급');
  return { tokens, adminToken };
}

/** 메인: 각 VU가 고유 사용자 토큰으로 주문 1건 */
export default function (data) {
  // VU 번호로 해당 사용자 토큰 선택 (1-based → 0-based)
  const vuIndex = (__VU - 1) % data.tokens.length;
  const token = data.tokens[vuIndex];

  const headers = {
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
  };

  const res = http.post(BASE_URL + '/api/orders',
    JSON.stringify({
      userId: vuIndex + 4,  // loaduser0=id4, loaduser1=id5 ... (admin=1, tester99=2, testadmin=3)
      items: [{ productId: PRODUCT_ID, quantity: 1, unitPrice: UNIT_PRICE }],
      idempotencyKey: 'concurrent-vu' + __VU + '-iter' + __ITER,
    }),
    {
      ...headers,
      responseCallback: http.expectedStatuses(201, 400, 409, 429),
    },
  );

  if (res.status === 201) {
    successCount.add(1);
    check(res, { '주문 성공 201': (r) => r.status === 201 });
  } else if (res.status === 400 || res.status === 409) {
    rejectCount.add(1);
    check(res, { '재고 부족 정상 거부': (r) => r.status === 400 || r.status === 409 });
  } else if (res.status === 429) {
    rateLimitCount.add(1);
    check(res, { 'Rate Limit (예상)': (r) => r.status === 429 });
  } else {
    errorCount.add(1);
    check(res, { '500 에러 없음': (r) => r.status !== 500 });
    console.error('예상치 못한 응답: ' + res.status + ' | ' + res.body);
  }
}

/** teardown: 최종 재고 확인 */
export function teardown(data) {
  const adminHeaders = {
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + data.adminToken },
  };

  const invRes = http.get(BASE_URL + '/api/inventory?productId=' + PRODUCT_ID, adminHeaders);
  const inv = invRes.json('data.content.0');
  console.log('=== 최종 재고 상태 ===');
  console.log('  onHand:    ' + inv.onHand);
  console.log('  reserved:  ' + inv.reserved);
  console.log('  allocated: ' + inv.allocated);
  console.log('  available: ' + inv.available);

  check(invRes, {
    '재고 available >= 0 (동시성 검증 핵심)': (r) => r.json('data.content.0.available') >= 0,
    '재고 onHand 변조 없음 (= 50)':           (r) => r.json('data.content.0.onHand') === STOCK_QTY,
  });
}
