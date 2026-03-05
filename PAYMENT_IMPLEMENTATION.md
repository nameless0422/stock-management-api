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
   │  ← 200 { PaymentResponse }    │                               │
```

---

## 생성/수정 파일 목록

### 신규 생성 (17개)

| 파일 경로 | 설명 |
|---|---|
| `src/main/resources/db/migration/V4__create_payment_tables.sql` | payments 테이블 DDL |
| `domain/payment/entity/PaymentStatus.java` | 결제 상태 enum |
| `domain/payment/entity/Payment.java` | 결제 엔티티 |
| `domain/payment/repository/PaymentRepository.java` | 결제 레포지토리 |
| `domain/payment/service/PaymentService.java` | 결제 비즈니스 로직 |
| `domain/payment/controller/PaymentController.java` | REST 컨트롤러 |
| `domain/payment/dto/PaymentPrepareRequest.java` | 결제 준비 요청 DTO |
| `domain/payment/dto/PaymentPrepareResponse.java` | 결제 준비 응답 DTO |
| `domain/payment/dto/PaymentConfirmRequest.java` | 결제 승인 요청 DTO |
| `domain/payment/dto/PaymentCancelRequest.java` | 결제 취소 요청 DTO |
| `domain/payment/dto/PaymentResponse.java` | 결제 응답 DTO |
| `domain/payment/infrastructure/TossPaymentsClient.java` | TossPayments HTTP 클라이언트 |
| `domain/payment/infrastructure/dto/TossConfirmRequest.java` | Toss 승인 API 요청 DTO |
| `domain/payment/infrastructure/dto/TossConfirmResponse.java` | Toss 승인 API 응답 DTO |
| `domain/payment/infrastructure/dto/TossCancelRequest.java` | Toss 취소 API 요청 DTO |
| `domain/payment/infrastructure/dto/TossWebhookEvent.java` | 웹훅 이벤트 DTO |
| `common/config/TossPaymentsConfig.java` | API 키 설정 및 RestClient Bean |

### 수정 (5개)

| 파일 | 변경 내용 |
|---|---|
| `common/exception/ErrorCode.java` | Payment 관련 에러 코드 5개 추가 |
| `domain/inventory/entity/Inventory.java` | `releaseAllocation()` 메서드 추가 |
| `domain/inventory/service/InventoryService.java` | `releaseAllocation()` 메서드 추가 |
| `domain/order/entity/Order.java` | `refund()` 메서드 추가 (CONFIRMED → CANCELLED) |
| `domain/order/service/OrderService.java` | `refund()` 메서드 추가 |
| `src/main/resources/application.properties` | `toss.*` 설정 추가 |

---

## API 엔드포인트

### POST `/api/payments/prepare`
결제창 초기화 전 서버 사전 검증. Payment PENDING 레코드 생성.

**Request:**
```json
{
  "orderId": 42,
  "amount": 150000
}
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
TossPayments 결제창 완료 후 서버 승인 처리.

**Request:**
```json
{
  "paymentKey": "tviva20240101...",
  "tossOrderId": "order-42-a1b2c3d4",
  "amount": 150000
}
```
**Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "orderId": 42,
    "paymentKey": "tviva20240101...",
    "tossOrderId": "order-42-a1b2c3d4",
    "amount": 150000,
    "status": "DONE",
    "method": "카드",
    "requestedAt": "2024-01-01T00:00:00",
    "approvedAt": "2024-01-01T00:00:01"
  }
}
```

---

### POST `/api/payments/{paymentKey}/cancel`
전액 또는 부분 환불.

**Request:**
```json
{
  "cancelReason": "고객 변심",
  "cancelAmount": null
}
```
- `cancelAmount` 생략 시 전액 취소.

---

### POST `/api/payments/webhook`
TossPayments 웹훅 수신. **인증 불필요** (Security 설정에서 public으로 열어야 함).

TossPayments는 10초 내 2xx 응답이 없으면 최대 7회 재전송.

---

### GET `/api/payments/{paymentKey}`
결제 상세 조회.

---

## 핵심 설계 결정사항

### 1. 금액 변조 방지 (Double Verification)
- `prepare` 단계: 클라이언트가 보낸 `amount`를 DB의 `Order.totalAmount`와 비교
- `confirm` 단계: 클라이언트가 보낸 `amount`를 DB의 `Payment.amount`와 재검증
- 클라이언트가 임의로 금액을 낮춰도 서버에서 차단

### 2. 취소 시 재고 처리 구분
| 취소 시점 | Order 상태 | 재고 처리 |
|---|---|---|
| 결제 전 (`OrderService.cancel`) | PENDING → CANCELLED | `reserved` 감소 |
| 결제 후 (`OrderService.refund`) | CONFIRMED → CANCELLED | `allocated` 감소 |

### 3. 멱등성 처리
| 상황 | 처리 |
|---|---|
| 동일 `orderId`로 prepare 재요청 | 기존 PENDING Payment 반환 |
| 이미 DONE인 결제에 confirm 재요청 | 기존 결과 그대로 반환 |
| 이미 CANCELLED인 결제에 cancel 재요청 | 기존 결과 그대로 반환 |

### 4. tossOrderId 생성 규칙
```
"order-" + {orderId} + "-" + {UUID 앞 8자리}
예: "order-42-a1b2c3d4"
```
TossPayments 규격: 영문/숫자/`-`/`_`, 6~64자

### 5. HTTP 클라이언트
Spring Boot 3.5 (Spring 6.1+)의 `RestClient` 사용.
`TossPaymentsConfig`에서 Bean 생성 시 Base64 인코딩된 `secretKey`를 `Authorization: Basic` 헤더에 주입.

---

## DB 스키마 (V4)

```sql
CREATE TABLE payments (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    order_id        BIGINT         NOT NULL,
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
    UNIQUE KEY uk_payments_toss_order_id (toss_order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
```

---

## 설정 방법

### 1. TossPayments 키 발급
[TossPayments 개발자센터](https://developers.tosspayments.com)에서 상점 등록 후 발급.
- 테스트 키: `test_sk_...` / `test_ck_...`
- 실서비스 키: `live_sk_...` / `live_ck_...`

### 2. 환경 변수 설정
```bash
export TOSS_SECRET_KEY=test_sk_your_actual_key
export TOSS_CLIENT_KEY=test_ck_your_actual_key
```

### 3. 웹훅 URL 등록
개발자센터 → 웹훅 설정 → URL: `https://your-domain.com/api/payments/webhook`
> 로컬 개발 시 ngrok 등으로 외부 URL 노출 필요

---

## 추후 구현 예정

- [ ] 가상계좌 웹훅 기반 결제 완료 처리 (`handleWebhook` DONE 분기)
- [ ] 결제 내역 목록 API (`GET /api/payments?orderId=42`)
- [ ] Spring Security 연동 (webhook endpoint 제외 인증 적용)
- [ ] 부분 취소 후 `PARTIAL_CANCELLED` 상태 처리
