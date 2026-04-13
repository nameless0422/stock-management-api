# DB 스키마

MySQL 8, Flyway 마이그레이션 (V1~V28), ENGINE=InnoDB, CHARSET=utf8mb4

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
| V16 | `users.deleted_at` 컬럼 추가 | 회원 Soft Delete |
| V17 | `product_images` + `products.thumbnail_url` 컬럼 추가 | 상품 이미지 (MinIO Presigned URL) |
| V18 | `outbox_events` | Transactional Outbox — 이벤트 유실 방지 |
| V19 | `reviews`, `wishlist_items` | 리뷰(실구매자 한정) + 위시리스트 |
| V20 | `user_points`, `point_transactions` + `orders.used_points` 컬럼 추가 | 포인트/적립금 시스템 |
| V21 | `refunds` | 환불 이력 |
| V22 | `user_coupons` | 사용자별 쿠폰 발급 이력 |
| V23 | `orders` 인덱스 추가 | `idx_orders_user_id`, `idx_orders_status`, `idx_orders_user_status` |
| V24 | `system_settings` | 런타임 변경 가능한 시스템 설정 (저재고 임계값 등) |
| V25 | `inventory_transactions` 복합 인덱스 추가 | `(inventory_id, created_at)` — filesort 제거 |
| V26 | `coupons.is_public` 컬럼 추가 | 공개 쿠폰 여부 (누구나 claim 가능) |
| V27 | `orders.created_at` 인덱스, `point_transactions` 복합 인덱스 추가 | 통계 집계 / 환불 조회 최적화 |
| V28 | `refunds.user_id` 컬럼 추가 | 소유권 검증 시 orders 추가 조회 제거 (비정규화) |

---

## ERD (관계 요약)

```
categories ──────────────┐
                         ▼
users ──────────── orders ──────── order_items ──── products
  │                  │   │                         │     │
  │                  │   └── coupon_id → coupons   │  inventory
  │                  │   └── delivery_address_id ─┐│       │
  │                  │                            ││  inventory_transactions
  │                  ├── order_status_history      │delivery_addresses
  │                  ├── payments ──── refunds     │
  │                  └── shipments                 │
  │                                               (ON DELETE SET NULL)
  ├── cart_items → products
  ├── delivery_addresses
  ├── coupon_usages → coupons
  ├── user_coupons → coupons
  ├── user_points
  ├── point_transactions
  ├── reviews → products
  └── wishlist_items → products

이벤트 / 설정:
  outbox_events      (Transactional Outbox 발행 큐)
  system_settings    (런타임 설정, PK: setting_key)
  product_images     (MinIO 오브젝트 메타데이터, product FK)

배치 집계:
  daily_order_stats         (일별 주문·매출)
  daily_inventory_snapshots (일별 재고 스냅샷, inventory FK)
```

---

## 테이블 상세

### products (V1, V14에서 category_id 추가, V17에서 thumbnail_url 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(255) | NOT NULL | 상품명 |
| description | TEXT | NULL | 상세 설명 |
| price | DECIMAL(12,2) | NOT NULL | 판매가 |
| sku | VARCHAR(100) | NOT NULL, **UNIQUE** | 재고 관리 코드 |
| category_id | BIGINT | NULL, FK→categories (ON DELETE SET NULL) | V14에서 추가 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | `ACTIVE` / `INACTIVE` / `DISCONTINUED` |
| thumbnail_url | VARCHAR(500) | NULL | V17에서 추가. 대표 이미지 URL |
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

### orders (V3, V5·V12·V13·V20에서 컬럼 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users, INDEX(V23) | V5에서 추가 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING', INDEX(V23) | `PENDING` / `CONFIRMED` / `CANCELLED` |
| total_amount | DECIMAL(12,2) | NOT NULL | 주문 항목 합계 |
| idempotency_key | VARCHAR(100) | NOT NULL, **UNIQUE** | 클라이언트 발급 UUID |
| delivery_address_id | BIGINT | NULL, FK→delivery_addresses (ON DELETE SET NULL) | V12에서 추가 |
| coupon_id | BIGINT | NULL, FK→coupons (ON DELETE SET NULL) | V13에서 추가 |
| discount_amount | DECIMAL(19,2) | NOT NULL, DEFAULT 0 | V13에서 추가. 쿠폰 할인 금액 |
| used_points | BIGINT | NOT NULL, DEFAULT 0 | V20에서 추가. 주문 시 사용 포인트 |
| created_at | DATETIME(6) | NOT NULL, INDEX(V27) | |
| updated_at | DATETIME(6) | NOT NULL | |

> 복합 인덱스: `(user_id, status)` — V23 추가

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

### users (V5, V16에서 deleted_at 추가)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| username | VARCHAR(50) | NOT NULL, **UNIQUE** | 로그인 ID |
| password | VARCHAR(255) | NOT NULL | BCrypt 해시 |
| email | VARCHAR(100) | NOT NULL, **UNIQUE** | |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | `USER` / `ADMIN` |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |
| deleted_at | DATETIME(6) | NULL | V16에서 추가. NOT NULL이면 탈퇴 계정 (`@SQLRestriction` 자동 필터링) |

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

### coupons (V13, V26에서 is_public 추가)

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
| is_public | TINYINT(1) | NOT NULL, DEFAULT 0 | V26에서 추가. 1이면 누구나 claim 가능한 공개 프로모 쿠폰 |
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

### product_images (V17)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| product_id | BIGINT | NOT NULL, FK→products (ON DELETE CASCADE) | |
| image_url | VARCHAR(500) | NOT NULL | 접근 URL |
| object_key | VARCHAR(500) | NOT NULL | MinIO(S3) 오브젝트 키 (삭제 시 사용) |
| image_type | VARCHAR(20) | NOT NULL | `THUMBNAIL` / `GALLERY` 등 |
| display_order | INT | NOT NULL, DEFAULT 0 | 이미지 노출 순서 |
| created_at | DATETIME(6) | NOT NULL | |

---

### outbox_events (V18)

> Transactional Outbox 패턴: 비즈니스 TX와 동일 TX에 저장 → `OutboxEventRelayScheduler`(5초 폴링)가 이벤트 발행

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| event_type | VARCHAR(50) | NOT NULL | 이벤트 종류 (`OutboxEventType` enum) |
| payload | TEXT | NOT NULL | 이벤트 데이터 (JSON) |
| created_at | DATETIME(6) | NOT NULL | |
| published_at | DATETIME(6) | NULL | NULL = 미발행 |
| retry_count | INT | NOT NULL, DEFAULT 0 | 재시도 횟수 |
| failed_at | DATETIME(6) | NULL | 최근 발행 실패 시각 |

> INDEX: `(published_at, retry_count, created_at)` — 미발행 이벤트 폴링

---

### reviews (V19)

> 실구매자(status=CONFIRMED 주문 보유)만 작성 가능. 상품당 1인 1리뷰

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| product_id | BIGINT | NOT NULL, FK→products (ON DELETE CASCADE) | |
| user_id | BIGINT | NOT NULL, FK→users | |
| rating | TINYINT | NOT NULL | 별점 1~5 |
| title | VARCHAR(100) | NOT NULL | 리뷰 제목 |
| content | TEXT | NOT NULL | 리뷰 본문 |
| created_at | DATETIME(6) | NOT NULL | |
| updated_at | DATETIME(6) | NOT NULL | |

> UK: `(product_id, user_id)` — 1인 1리뷰 제약

---

### wishlist_items (V19)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users | |
| product_id | BIGINT | NOT NULL, FK→products (ON DELETE CASCADE) | |
| created_at | DATETIME(6) | NOT NULL | |

> UK: `(user_id, product_id)` — 중복 저장 방지

---

### user_points (V20)

> 사용자별 포인트 잔액 단일 행. 동시성 제어: 비관적 락(SELECT FOR UPDATE)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, **UNIQUE**, FK→users | |
| balance | BIGINT | NOT NULL, DEFAULT 0 | 현재 포인트 잔액 |
| updated_at | DATETIME(6) | NOT NULL | |

---

### point_transactions (V20)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users | |
| amount | BIGINT | NOT NULL | 변동 금액 (양수=적립/환불, 음수=사용/소멸) |
| type | VARCHAR(20) | NOT NULL | `EARN` / `USE` / `REFUND` / `EXPIRE` |
| description | VARCHAR(200) | NOT NULL | 변동 사유 |
| order_id | BIGINT | NULL | 연관 주문 ID |
| created_at | DATETIME(6) | NOT NULL | |

> 복합 인덱스: `(user_id, order_id)` — V27 추가. 환불 시 주문별 포인트 내역 조회

---

### refunds (V21, V28에서 user_id 추가)

> 결제당 1건 환불. 실패(FAILED) 상태이면 `reset()` 후 재시도 가능

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| payment_id | BIGINT | NOT NULL, **UNIQUE**, FK→payments | 결제당 환불 1건 |
| order_id | BIGINT | NOT NULL, FK→orders | |
| user_id | BIGINT | NOT NULL, DEFAULT 0 | V28에서 추가. 소유권 검증용 비정규화 |
| amount | DECIMAL(19,2) | NOT NULL | 환불 금액 |
| reason | VARCHAR(300) | NOT NULL | 환불 사유 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `COMPLETED` / `FAILED` |
| created_at | DATETIME(6) | NOT NULL | |
| completed_at | DATETIME(6) | NULL | 처리 완료 시각 |

---

### user_coupons (V22)

> ADMIN이 특정 사용자에게 직접 발급한 쿠폰 매핑

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | NOT NULL, FK→users | |
| coupon_id | BIGINT | NOT NULL, FK→coupons | |
| issued_at | DATETIME(6) | NOT NULL | |

> UK: `(user_id, coupon_id)` — 중복 발급 방지

---

### system_settings (V24)

> 런타임 변경 가능한 시스템 설정. `setting_key`가 PK (키-값 구조)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| setting_key | VARCHAR(100) | **PK** | 설정 키 (예: `low_stock_threshold`) |
| setting_value | VARCHAR(500) | NOT NULL | 설정값 (문자열) |
| description | VARCHAR(255) | NULL | 설명 |
| updated_by | VARCHAR(100) | NULL | 마지막 변경자 |
| updated_at | DATETIME(6) | NOT NULL | |

> 초기값: `low_stock_threshold = '10'`

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
| `orders.user_id` | → users.id | V5 추가, INDEX(V23) |
| `orders.delivery_address_id` | → delivery_addresses.id | NULL, ON DELETE SET NULL, V12 추가 |
| `orders.coupon_id` | → coupons.id | NULL, ON DELETE SET NULL, V13 추가 |
| `payments.order_id` | → orders.id | UNIQUE (V8), 주문당 결제 1건 |
| `inventory_transactions.inventory_id` | → inventory.id | 복합 INDEX `(inventory_id, created_at)` V25 추가 |
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
| `product_images.product_id` | → products.id | ON DELETE CASCADE, V17 추가 |
| `reviews.product_id` | → products.id | ON DELETE CASCADE, V19 추가 |
| `reviews.user_id` | → users.id | UK: (product_id, user_id) |
| `wishlist_items.user_id` | → users.id | UK: (user_id, product_id), V19 추가 |
| `wishlist_items.product_id` | → products.id | ON DELETE CASCADE |
| `user_points.user_id` | → users.id | UNIQUE, V20 추가 |
| `point_transactions.user_id` | → users.id | 복합 INDEX `(user_id, order_id)` V27 추가 |
| `refunds.payment_id` | → payments.id | UNIQUE, V21 추가 |
| `refunds.order_id` | → orders.id | INDEX 포함 |
| `user_coupons.user_id` | → users.id | UK: (user_id, coupon_id), V22 추가 |
| `user_coupons.coupon_id` | → coupons.id | |

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
