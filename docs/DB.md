# DB 스키마

MySQL 8, Flyway 마이그레이션 (V1~V15), ENGINE=InnoDB, CHARSET=utf8mb4

---

## 테이블 목록

| 마이그레이션 | 테이블 / 변경 | 설명 |
|---|---|---|
| V1 | `products` | 상품 마스터 |
| V2 | `inventory` | 재고 (상품당 1행) |
| V3 | `orders`, `order_items` | 주문 헤더 + 주문 항목 |
| V4 | `payments` | TossPayments 결제 |
| V5 | `users` + `orders.user_id` FK | 회원 + 주문 FK 연결 |
| V6 | `inventory_transactions` | 재고 변동 이력 |
| V7 | `inventory_transactions.note` 컬럼 추가 | 입고/조정 사유 |
| V8 | `payments.order_id` UNIQUE 제약 추가 | 결제 멱등성 |
| V9 | `order_status_history` | 주문 상태 변경 이력 |
| V10 | `cart_items` | 장바구니 |
| V11 | `shipments` | 배송 |
| V12 | `delivery_addresses` + `orders.delivery_address_id` FK | 배송지 |
| V13 | `coupons`, `coupon_usages` + `orders.coupon_id/discount_amount` | 쿠폰 |
| V14 | `categories` + `products.category_id` FK (category VARCHAR 제거) | 카테고리 분리 |
| V15 | `daily_order_stats`, `daily_inventory_snapshots` | 배치 집계 테이블 |

---

## ERD (관계 요약)

```
categories ──────────────┐
                         ▼
users ──────────── orders ──────── order_items ──── products
  │                  │   │                             │ 1
  │                  │   └── coupon_id → coupons       │
  │                  │   └── delivery_address_id ──┐   │
  │                  │                             │ inventory
  │                  ├── order_status_history       │       │
  │                  ├── payments                  delivery  inventory_transactions
  │                  └── shipments                 _addresses
  │
  ├── cart_items → products
  ├── delivery_addresses
  └── coupon_usages → coupons

배치 집계:
  daily_order_stats         (일별 주문·매출)
  daily_inventory_snapshots (일별 재고 스냅샷, inventory FK)
```

---

## 테이블 상세

### products (V1, V14에서 category 컬럼 제거 + category_id 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(255) | NOT NULL | 상품명 |
| description | TEXT | NULL | 상세 설명 |
| price | DECIMAL(12,2) | NOT NULL | 판매가 |
| sku | VARCHAR(100) | NOT NULL, **UNIQUE** | 재고 관리 코드 |
| category_id | BIGINT | NULL, FK→categories (ON DELETE SET NULL) | V14에서 추가 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | `ACTIVE` / `INACTIVE` / `DISCONTINUED` |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

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
| updated_at | DATETIME(6) | NOT NULL | |

> `available = on_hand - reserved - allocated` (DB 컬럼 없음, 애플리케이션에서 계산)

---

### orders (V3, V5·V12·V13에서 컬럼 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users | V5에서 추가 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `CONFIRMED` / `CANCELLED` |
| total_amount | DECIMAL(12,2) | NOT NULL | 주문 항목 합계 |
| idempotency_key | VARCHAR(100) | NOT NULL, **UNIQUE** | 클라이언트 발급 UUID |
| delivery_address_id | BIGINT | NULL, FK→delivery_addresses (ON DELETE SET NULL) | V12에서 추가 |
| coupon_id | BIGINT | NULL, FK→coupons (ON DELETE SET NULL) | V13에서 추가 |
| discount_amount | DECIMAL(19,2) | NOT NULL, DEFAULT 0 | V13에서 추가. 쿠폰 할인 금액 |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### order_items (V3)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL, FK→orders | |
| product_id | BIGINT | NOT NULL, FK→products | |
| quantity | INT | NOT NULL | |
| unit_price | DECIMAL(12,2) | NOT NULL | 주문 당시 단가 |
| subtotal | DECIMAL(12,2) | NOT NULL | unit_price × quantity |
| created_at | DATETIME(6) | NOT NULL | |

---

### payments (V4, V8에서 order_id UNIQUE 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL, **UNIQUE** (V8), FK→orders | 주문당 결제 1건 |
| payment_key | VARCHAR(200) | NULL | TossPayments 발급 키 (승인 후 채워짐) |
| toss_order_id | VARCHAR(64) | NOT NULL, **UNIQUE** | TossPayments에 전달한 주문ID |
| amount | DECIMAL(12,2) | NOT NULL | prepare 시 확정한 금액 |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `DONE` / `CANCELLED` / `FAILED` / `PARTIAL_CANCELLED` |
| method | VARCHAR(50) | NULL | 결제 수단 |
| requested_at | DATETIME(6) | NULL | |
| approved_at | DATETIME(6) | NULL | |
| cancel_reason | VARCHAR(200) | NULL | |
| failure_code | VARCHAR(50) | NULL | |
| failure_message | VARCHAR(200) | NULL | |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### users (V5)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| username | VARCHAR(50) | NOT NULL, **UNIQUE** | 로그인 ID |
| password | VARCHAR(255) | NOT NULL | BCrypt 해시 |
| email | VARCHAR(100) | NOT NULL, **UNIQUE** | |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | `USER` / `ADMIN` |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### inventory_transactions (V6, V7에서 note 컬럼 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| inventory_id | BIGINT | NOT NULL, FK→inventory, INDEX | |
| type | VARCHAR(30) | NOT NULL | `RECEIVE` / `ADJUST` / `RESERVE` / `RELEASE_RESERVATION` / `CONFIRM_ALLOCATION` / `RELEASE_ALLOCATION` |
| quantity | INT | NOT NULL | 변동 수량 (항상 양수) |
| snapshot_on_hand | INT | NOT NULL | 변동 직후 on_hand 값 |
| snapshot_reserved | INT | NOT NULL | 변동 직후 reserved 값 |
| snapshot_allocated | INT | NOT NULL | 변동 직후 allocated 값 |
| note | VARCHAR(255) | NULL | V7에서 추가. 입고/조정 사유 |
| created_at | DATETIME(6) | NOT NULL | |

---

### order_status_history (V9)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL, FK→orders (ON DELETE CASCADE), INDEX | |
| from_status | VARCHAR(20) | NULL | 이전 상태 (최초 생성 시 null) |
| to_status | VARCHAR(20) | NOT NULL | 변경된 상태 |
| changed_by | VARCHAR(100) | NULL | 변경 주체 (username 등) |
| note | VARCHAR(255) | NULL | 변경 사유 |
| created_at | DATETIME(6) | NOT NULL | |

---

### cart_items (V10)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL | |
| product_id | BIGINT | NOT NULL, FK→products | |
| quantity | INT | NOT NULL | |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

> UK: `(user_id, product_id)` — 사용자별 상품당 1행 유지

---

### shipments (V11)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | NOT NULL, **UNIQUE**, FK→orders | 주문당 배송 1건 |
| status | VARCHAR(20) | NOT NULL | `PREPARING` / `SHIPPED` / `DELIVERED` / `RETURNED` |
| carrier | VARCHAR(100) | NULL | 배송사 |
| tracking_number | VARCHAR(100) | NULL | 운송장 번호 |
| shipped_at | DATETIME(6) | NULL | |
| delivered_at | DATETIME(6) | NULL | |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### delivery_addresses (V12)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users, INDEX | |
| alias | VARCHAR(50) | NOT NULL | 별칭 (집, 회사 등) |
| recipient | VARCHAR(50) | NOT NULL | 수령인 이름 |
| phone | VARCHAR(20) | NOT NULL | |
| zip_code | VARCHAR(10) | NOT NULL | |
| address1 | VARCHAR(200) | NOT NULL | 도로명/지번 주소 |
| address2 | VARCHAR(100) | NULL | 상세주소 (동·호수 등) |
| is_default | TINYINT(1) | NOT NULL, DEFAULT 0 | 기본 배송지 여부 |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### coupons (V13)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| code | VARCHAR(50) | NOT NULL, **UNIQUE** | 쿠폰 코드 |
| name | VARCHAR(100) | NOT NULL | 쿠폰명 |
| description | VARCHAR(255) | NULL | |
| discount_type | VARCHAR(20) | NOT NULL | `FIXED_AMOUNT` / `PERCENTAGE` |
| discount_value | DECIMAL(19,2) | NOT NULL | 할인 금액 또는 퍼센트 (0~100) |
| minimum_order_amount | DECIMAL(19,2) | NULL | 최소 주문 금액 조건 |
| max_discount_amount | DECIMAL(19,2) | NULL | PERCENTAGE 타입 최대 할인 금액 캡 |
| max_usage_count | INT | NULL | 전체 사용 가능 횟수 (null=무제한) |
| usage_count | INT | NOT NULL, DEFAULT 0 | 현재 사용 횟수 |
| max_usage_per_user | INT | NOT NULL, DEFAULT 1 | 사용자별 사용 가능 횟수 |
| valid_from | DATETIME(6) | NOT NULL | 유효 시작일 |
| valid_until | DATETIME(6) | NOT NULL | 유효 종료일 |
| active | TINYINT(1) | NOT NULL, DEFAULT 1 | 활성화 여부 |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### coupon_usages (V13)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| coupon_id | BIGINT | NOT NULL, FK→coupons | |
| user_id | BIGINT | NOT NULL, FK→users | |
| order_id | BIGINT | NOT NULL, **UNIQUE**, FK→orders | 주문당 쿠폰 1개 |
| discount_amount | DECIMAL(19,2) | NOT NULL | 실제 할인된 금액 |
| used_at | DATETIME(6) | NOT NULL | |

> INDEX: `(coupon_id, user_id)`

---

### categories (V14)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL, **UNIQUE** | 카테고리명 |
| description | VARCHAR(255) | NULL | |
| parent_id | BIGINT | NULL, FK→categories (ON DELETE SET NULL) | 부모 카테고리 (null=최상위) |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

---

### daily_order_stats (V15)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| stat_date | DATE | NOT NULL, **UNIQUE** | 집계 기준일 |
| total_orders | INT | NOT NULL, DEFAULT 0 | 전일 전체 주문 수 |
| confirmed_orders | INT | NOT NULL, DEFAULT 0 | 전일 결제 완료 주문 수 |
| cancelled_orders | INT | NOT NULL, DEFAULT 0 | 전일 취소 주문 수 |
| total_revenue | DECIMAL(19,2) | NOT NULL, DEFAULT 0 | 전일 매출액 (쿠폰 할인 차감) |
| created_at | DATETIME(6) | NOT NULL | |

---

### daily_inventory_snapshots (V15)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| inventory_id | BIGINT | NOT NULL, FK→inventory (ON DELETE CASCADE) | |
| snapshot_date | DATE | NOT NULL | 스냅샷 기준일 |
| on_hand | INT | NOT NULL | 창고 실물 재고 |
| reserved | INT | NOT NULL | 주문 예약 재고 |
| allocated | INT | NOT NULL | 출고 확정 재고 |
| available | INT | NOT NULL | 주문 가능 재고 (스냅샷 시점 계산값) |
| created_at | DATETIME(6) | NOT NULL | |

> UK: `(inventory_id, snapshot_date)` — 날짜별 중복 스냅샷 방지

---

## FK 관계 요약

| FK | 방향 | 비고 |
|---|---|---|
| `inventory.product_id` | → products.id | UNIQUE (1:1) |
| `order_items.order_id` | → orders.id | |
| `order_items.product_id` | → products.id | |
| `orders.user_id` | → users.id | V5 추가 |
| `orders.delivery_address_id` | → delivery_addresses.id | NULL, ON DELETE SET NULL, V12 추가 |
| `orders.coupon_id` | → coupons.id | NULL, ON DELETE SET NULL, V13 추가 |
| `payments.order_id` | → orders.id | UNIQUE (V8), 주문당 결제 1건 |
| `inventory_transactions.inventory_id` | → inventory.id | INDEX 포함 |
| `order_status_history.order_id` | → orders.id | ON DELETE CASCADE |
| `cart_items.product_id` | → products.id | UK: (user_id, product_id) |
| `shipments.order_id` | → orders.id | UNIQUE, 주문당 배송 1건 |
| `delivery_addresses.user_id` | → users.id | INDEX 포함 |
| `coupon_usages.coupon_id` | → coupons.id | |
| `coupon_usages.user_id` | → users.id | |
| `coupon_usages.order_id` | → orders.id | UNIQUE |
| `products.category_id` | → categories.id | NULL, ON DELETE SET NULL, V14 추가 |
| `categories.parent_id` | → categories.id | Self-referential, ON DELETE SET NULL |
| `daily_inventory_snapshots.inventory_id` | → inventory.id | ON DELETE CASCADE |

---

## 재고 상태 전이

```
입고(RECEIVE)
  on_hand += quantity

재고 조정(ADJUST)
  on_hand += quantity (음수 가능)

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

---

## 배송 상태 전이

```
PREPARING ──▶ SHIPPED ──▶ DELIVERED
                     └──▶ RETURNED
```
