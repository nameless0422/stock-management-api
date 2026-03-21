# Payment Domain Implementation

TossPayments Core API v1을 이용한 결제 도메인 구현 내용 정리.

---

## 결제 전체 흐름

```
[Client]                        [Our Server]                  [TossPayments]
   │                                │                               │
   │  POST /api/payments/prepare    │                               │
   │  { orderId, amount }           │                               │
   │──────────────────────────────>│                               │
   │                               │  1. Order 검증 (PENDING, 금액) │
   │                               │  2. Payment PENDING 생성       │
   │  ← { tossOrderId, amount }    │                               │
   │                               │                               │
   │  결제창 초기화(tossOrderId, amount)                              │
   │─────────────────────────────────────────────────────────────>│
   │  ← paymentKey (결제 완료 후 리다이렉트)                          │
   │                               │                               │
   │  POST /api/payments/confirm   │                               │
   │  { paymentKey, tossOrderId, amount }                          │
   │──────────────────────────────>│                               │
   │                               │  POST /v1/payments/confirm    │
   │                               │─────────────────────────────>│
   │                               │  ← { status: "DONE", ... }   │
   │                               │                               │
   │                               │  Payment: PENDING → DONE      │
   │                               │  Order:   PENDING → CONFIRMED │
   │                               │  Inventory: reserved → allocated
   │                               │  Shipment: PREPARING 자동 생성 │
   │  ← 200 { PaymentResponse }    │                               │
```

---

## API 엔드포인트

### POST `/api/payments/prepare`
결제창 초기화 전 서버 사전 검증. Payment PENDING 레코드 생성.

**Request:**
```json
{ "orderId": 42, "amount": 150000 }
```
**Response:**
```json
{
  "success": true,
  "data": {
    "tossOrderId": "order-42-a1b2c3d4",
    "amount": 150000,
    "orderName": "MacBook Pro 외 1건"
  }
}
```

---

### POST `/api/payments/confirm`
TossPayments 결제창 완료 후 서버 승인 처리. 성공 시 Shipment 자동 생성.

**Request:**
```json
{
  "paymentKey": "tviva20240101...",
  "tossOrderId": "order-42-a1b2c3d4",
  "amount": 150000
}
```

---

### POST `/api/payments/{paymentKey}/cancel`
전액 또는 부분 환불. 쿠폰이 적용된 주문이면 쿠폰도 자동 반환.

**Request:**
```json
{ "cancelReason": "고객 변심", "cancelAmount": null }
```
- `cancelAmount` 생략 시 전액 취소.

---

### GET `/api/payments/{paymentKey}`
결제 상세 조회.

---

### GET `/api/payments/order/{orderId}`
주문 ID로 결제 조회. 결제 전이면 `data: null` 반환.

---

### POST `/api/payments/webhook`
TossPayments 웹훅 수신. **인증 불필요** (Public 엔드포인트).
`Toss-Signature` 헤더 HMAC-SHA256 서명 검증 후 처리.
TossPayments는 10초 내 2xx 응답이 없으면 최대 7회 재전송.

---

## 핵심 설계 결정사항

### 1. 금액 변조 방지 (Double Verification)
- `prepare`: 클라이언트 `amount` ↔ DB `Order.totalAmount` 비교
- `confirm`: 클라이언트 `amount` ↔ DB `Payment.amount` 재검증
- 클라이언트가 임의로 금액을 낮춰도 서버에서 차단

### 2. 결제 멱등성 3중 전략

| 레이어 | 전략 |
|---|---|
| `prepare()` | `payments.order_id` DB UNIQUE 제약 (V8) |
| `confirm()` / `cancel()` | Redis SETNX (`PaymentIdempotencyManager`)로 PROCESSING 상태 원자적 선점, 결과 24h 캐싱 |
| Toss API 호출 | `Idempotency-Key: {tossOrderId}` 헤더 |

### 3. 취소 시 재고 처리 구분

| 취소 시점 | Order 상태 | 재고 처리 | 쿠폰 |
|---|---|---|---|
| 결제 전 (`OrderService.cancel`) | PENDING → CANCELLED | `reserved` 감소 | 반환 |
| 결제 후 (`PaymentService.cancel`) | CONFIRMED → CANCELLED | `allocated` 감소 | 반환 |

### 4. Circuit Breaker (Resilience4j)
`TossPaymentsClient`에 `@CircuitBreaker(name="tossPayments")` 적용.
- 10회 요청 중 50% 실패 시 회로 차단 → 빠른 실패 응답
- 차단 후 30초 대기 → HALF_OPEN 전환 → 3회 테스트 후 복구

### 5. 웹훅 서명 검증
`Toss-Signature` 헤더를 HMAC-SHA256으로 검증 (secret: `TOSS_SECRET_KEY`).
검증 실패 시 400 응답.

### 6. tossOrderId 생성 규칙
```
"order-" + {orderId} + "-" + {UUID 앞 8자리}
예: "order-42-a1b2c3d4"
```
TossPayments 규격: 영문/숫자/`-`/`_`, 6~64자.

### 7. HTTP 클라이언트
Spring 6.1+ `RestClient` 사용. `SimpleClientHttpRequestFactory`로 connect/read timeout 설정 (application.properties `toss.connect-timeout-ms`, `toss.read-timeout-ms`).
`TossPaymentsConfig`에서 Base64 인코딩된 `secretKey`를 `Authorization: Basic` 헤더에 주입.

### 8. Shipment 자동 생성
`confirm()` 성공 시 `ShipmentService.createForOrder()` 호출 → Shipment PREPARING 상태로 자동 생성.

---

## DB 스키마 (V4 + V8)

```sql
CREATE TABLE payments (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    order_id        BIGINT         NOT NULL UNIQUE,   -- V8에서 UNIQUE 추가
    payment_key     VARCHAR(200),
    toss_order_id   VARCHAR(64)    NOT NULL UNIQUE,
    amount          DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    method          VARCHAR(50),
    requested_at    DATETIME(6),
    approved_at     DATETIME(6),
    cancel_reason   VARCHAR(200),
    failure_code    VARCHAR(50),
    failure_message VARCHAR(200),
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
```

---

## 설정

### 환경 변수

| 변수 | 설명 |
|---|---|
| `TOSS_SECRET_KEY` | 서버 시크릿 키 (`test_sk_...` / `live_sk_...`) |
| `TOSS_CLIENT_KEY` | 클라이언트 키 (`test_ck_...` / `live_ck_...`) |

### 웹훅 URL 등록
개발자센터 → 웹훅 설정 → URL: `https://your-domain.com/api/payments/webhook`
> 로컬 개발 시 ngrok 등으로 외부 URL 노출 필요

### Circuit Breaker 설정 (`application.properties`)
```properties
resilience4j.circuitbreaker.instances.tossPayments.sliding-window-size=10
resilience4j.circuitbreaker.instances.tossPayments.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.tossPayments.wait-duration-in-open-state=30s
```
