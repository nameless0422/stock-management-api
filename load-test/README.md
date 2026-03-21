# 부하 테스트 (k6)

## 사전 준비

```bash
# 앱 실행 (포트 8090 예시)
./gradlew bootRun --args='--server.port=8090'
```

## 테스트 실행

```bash
# 1. Smoke — 기본 동작 확인 (1 VU, 30초)
k6 run load-test/smoke.js

# 2. Load — 일반 부하 (최대 50 VU, ~3분)
k6 run load-test/load.js

# 3. Concurrent — 동시 주문 동시성 검증 (100 VU, 1회씩)
k6 run load-test/concurrent-orders.js

# BASE_URL 변경 시
k6 run -e BASE_URL=http://localhost:8080 load-test/smoke.js
```

## 시나리오별 목적

| 파일 | VU | 목적 |
|------|----|------|
| `smoke.js` | 1 | 기본 API 동작·응답 검증 |
| `load.js` | 0→50→0 | Redis 캐시 효과, 처리량, p95 레이턴시 측정 |
| `concurrent-orders.js` | 100 | 분산 락·비관적 락 동시성 제어 검증 |

## 합격 기준

| 지표 | 목표 |
|------|------|
| 오류율 | < 1% (Smoke), < 5% (Load) |
| p95 응답 | < 500ms (읽기), < 2000ms (쓰기) |
| 재고 available | ≥ 0 (음수 절대 불가) |
| 500 서버 에러 | 0건 |
