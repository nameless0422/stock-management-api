# DB 스키마

MySQL 8, Flyway 마이그레이션 (V1~V6), ENGINE=InnoDB, CHARSET=utf8mb4

---

## 테이블 목록

| 마이그레이션 | 테이블 | 설명 |
|---|---|---|
| V1 | `products` | 상품 마스터 |
| V2 | `inventory` | 재고 (상품당 1행) |
| V3 | `orders`, `order_items` | 주문 헤더 + 주문 항목 |
| V4 | `payments` | TossPayments 결제 |
| V5 | `users` | 회원 + `orders.user_id` FK 추가 |
| V6 | `inventory_transactions` | 재고 변동 이력 |

---

## ERD (관계 요약)

```
users ──────────────────────────── orders
                                      │ 1
                                      │
                                    order_items ─── products
                                                        │ 1
                                                        │
                                                    inventory
                                                        │ 1
                                                        │
                                              inventory_transactions

orders ─── payments
```

---

## 테이블 상세

### products (V1)

상품 마스터. 재고·주문·결제가 FK로 참조하는 핵심 테이블.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(255) | NOT NULL | 상품명 |
| description | TEXT | NULL 허용 | 상세 설명 |
| price | DECIMAL(12,2) | NOT NULL | 판매가 |
| sku | VARCHAR(100) | NOT NULL, **UNIQUE** | 재고 관리 코드 |
| category | VARCHAR(100) | NULL 허용 | 카테고리 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | `ACTIVE` / `INACTIVE` / `DISCONTINUED` |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL, ON UPDATE | |

---

### inventory (V2)

상품 1개당 레코드 1개 (1:1). 재고 4-state 모델.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| product_id | BIGINT | NOT NULL, **UNIQUE**, FK→products | |
| on_hand | INT | NOT NULL, DEFAULT 0 | 창고 실물 재고 |
| reserved | INT | NOT NULL, DEFAULT 0 | 주문 생성 후 예약 (미결제) |
| allocated | INT | NOT NULL, DEFAULT 0 | 결제 완료 후 확정 |
| version | INT | NOT NULL, DEFAULT 0 | 낙관적 락 버전 |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL, ON UPDATE | |

> `available = on_hand - reserved - allocated` (DB 컬럼 없음, 애플리케이션에서 계산)

---

### orders (V3)

주문 헤더. 멱등성 키로 중복 주문 방지.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users (V5에서 추가) | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `CONFIRMED` / `CANCELLED` |
| total_amount | DECIMAL(12,2) | NOT NULL | 주문 항목 합계 |
| idempotency_key | VARCHAR(100) | NOT NULL, **UNIQUE** | 클라이언트 발급 UUID |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL, ON UPDATE | |

---

### order_items (V3)

주문 항목. 주문 당시 단가를 별도 저장해 가격 변경 이력 보존.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL, FK→orders | |
| product_id | BIGINT | NOT NULL, FK→products | |
| quantity | INT | NOT NULL | 주문 수량 |
| unit_price | DECIMAL(12,2) | NOT NULL | 주문 당시 단가 |
| subtotal | DECIMAL(12,2) | NOT NULL | unit_price × quantity |
| created_at | DATETIME(6) | NOT NULL | |

---

### payments (V4)

TossPayments 결제 라이프사이클 관리.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL, FK→orders | |
| payment_key | VARCHAR(200) | NULL 허용 | TossPayments 발급 키 (승인 후 채워짐) |
| toss_order_id | VARCHAR(64) | NOT NULL, **UNIQUE** | TossPayments에 전달한 주문ID |
| amount | DECIMAL(12,2) | NOT NULL | prepare 시 확정한 금액 |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `DONE` / `CANCELLED` / `FAILED` / `PARTIAL_CANCELLED` |
| method | VARCHAR(50) | NULL 허용 | 결제 수단 (카드, 가상계좌 등) |
| requested_at | DATETIME(6) | NULL 허용 | TossPayments 요청 시각 |
| approved_at | DATETIME(6) | NULL 허용 | TossPayments 승인 시각 |
| cancel_reason | VARCHAR(200) | NULL 허용 | 취소 사유 |
| failure_code | VARCHAR(50) | NULL 허용 | TossPayments 오류 코드 |
| failure_message | VARCHAR(200) | NULL 허용 | TossPayments 오류 메시지 |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### users (V5)

회원 정보. V5에서 `orders.user_id → users.id` FK도 함께 추가.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| username | VARCHAR(50) | NOT NULL, **UNIQUE** | 로그인 ID |
| password | VARCHAR(255) | NOT NULL | BCrypt 해시 |
| email | VARCHAR(100) | NOT NULL, **UNIQUE** | |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | `USER` / `ADMIN` |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL, ON UPDATE | |

---

### inventory_transactions (V6)

재고 변동 이력. 모든 재고 조작 후 스냅샷을 기록.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| inventory_id | BIGINT | NOT NULL, FK→inventory, INDEX | |
| type | VARCHAR(30) | NOT NULL | `RECEIVE` / `RESERVE` / `RELEASE_RESERVATION` / `CONFIRM_ALLOCATION` / `RELEASE_ALLOCATION` |
| quantity | INT | NOT NULL | 변동 수량 (항상 양수) |
| snapshot_on_hand | INT | NOT NULL | 변동 직후 on_hand 값 |
| snapshot_reserved | INT | NOT NULL | 변동 직후 reserved 값 |
| snapshot_allocated | INT | NOT NULL | 변동 직후 allocated 값 |
| created_at | DATETIME(6) | NOT NULL | |

---

## FK 관계 요약

| FK | 방향 | 비고 |
|---|---|---|
| `inventory.product_id` | → products.id | UNIQUE (1:1) |
| `order_items.order_id` | → orders.id | |
| `order_items.product_id` | → products.id | |
| `orders.user_id` | → users.id | V5에서 ALTER TABLE로 추가 |
| `payments.order_id` | → orders.id | |
| `inventory_transactions.inventory_id` | → inventory.id | INDEX 포함 |

---

## 재고 상태 전이

```
입고(RECEIVE)
  on_hand += quantity

주문 생성(RESERVE)
  reserved += quantity
  available = on_hand - reserved - allocated 체크 → 부족 시 예외

결제 완료(CONFIRM_ALLOCATION)
  reserved -= quantity
  allocated += quantity

주문 취소 / 결제 실패(RELEASE_RESERVATION)
  reserved -= quantity

결제 취소 / 환불(RELEASE_ALLOCATION)
  allocated -= quantity
```

---

## 결제 상태 전이

```
PENDING ──▶ DONE ──▶ CANCELLED
        └──▶ FAILED
```
