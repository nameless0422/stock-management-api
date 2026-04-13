# TODO — 기술적 개선 항목

> 보안 취약점, 프로덕션 버그, 성능 결함, 코드 품질 문제를 우선순위별로 정리한다.

---

## ✅ 즉시 수정 완료 — 프로덕션 데이터 정합성 위험 (6/6)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 1 | 음수 재고 허용 버그 | `InventoryService` | `BusinessException(INVENTORY_STATE_INCONSISTENT)` throw |
| 2 | 포인트 롤백 불일치 | `OrderService.create()` | `validateBalance()` fail-fast 사전 검증 추가 |
| 3 | 배송·포인트 무음 삼킴 | `PaymentTransactionHelper` | Outbox 패턴(`SHIPMENT_CREATE`, `POINT_EARN`) 전환 |
| 4 | null userId NPE | `OrderExpiryScheduler` | `cancelBySystem()` 전용 메서드, Order에서 userId 직접 조회 |
| 5 | saveAll() 예외 무음 삼킴 | `InventorySnapshotScheduler` | `DataIntegrityViolationException` 분리, 장애 시 re-throw |
| 6 | SpEL null NPE | `DistributedLockAspect` | `resolveKey()` null/예외 시 `BusinessException` throw |

---

## ✅ 즉시 처리 — 성능 병목 완료 (3/3)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 7 | InventoryRepository fetch join 누락 (N+1) | `InventoryRepository` | `findByProductId`, `findAllByProductIdIn`에 `@EntityGraph({"product"})` 추가 |
| 8 | CategoryService.getTree() 캐시 미적용 | `CategoryService`, `CacheConfig`, `CategoryResponse` | `getList/getTree` `@Cacheable`, `create/update/delete` `@CacheEvict(allEntries=true)`, categories 30분 TTL, `@Jacksonized` + `ArrayList` 적용 |
| 9 | InventorySnapshotScheduler 전체 로드 | `InventorySnapshotScheduler`, `InventorySnapshotProcessor` | 1,000건 페이지 루프 + 배치마다 독립 트랜잭션 커밋 (`@Component` 분리) |

---

## ✅ 스프린트 내 처리 완료 (8/11)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 10 | applyConfirmResult() 이중 DB 조회 | `PaymentTransactionHelper` | `confirm()` → `Order` 반환, 재사용 |
| 11 | Outbox 배치 크기 하드코딩 | `OutboxEventRelayScheduler` | `@Value("${outbox.relay.batch-size:100}")` + Prometheus counter (이미 완료) |
| 12 | DB 인덱스 누락 | V23/V25/V27 마이그레이션 | 모든 항목 기존 마이그레이션으로 커버 |
| 14 | RefundService 이중 쿼리 | `Refund` 엔티티 + `RefundService` | `userId` 비정규화 저장 → `validateOwnership()` orders 조회 제거, V28 마이그레이션 |
| 17 | P6Spy 운영 오버헤드 | `build.gradle` | `implementation` → `runtimeOnly` |
| 18 | Zipkin 샘플링 100% | `application.properties` | 1.0 → 0.1 (운영: 0.01~0.1) |
| 19 | OrderSearchRequest mutable DTO | `OrderSpecification` + `OrderService` | `of(request, forceUserId)` 오버로드, DTO 변경 제거 |

## 🟠 미처리 (3/11) — 아키텍처/인프라 판단 필요

### 13. 오프셋 페이지네이션 성능 한계

**위치**: 주문 목록, 재고 이력, 리뷰 등 `Pageable` 엔드포인트

**문제**: `LIMIT ? OFFSET ?`는 페이지 번호가 클수록 앞 레코드를 모두 스캔 후 버린다. 대량 이력 보유 사용자의 마지막 페이지 조회 시 풀스캔.

**개선**: 스크롤 조회(주문 이력, 재고 이력)는 `WHERE id < :lastId LIMIT ?` 커서 기반 페이지네이션으로 전환.

---

### 15. AdminService.getOrders() 사용자명 조회 비효율

**위치**: `domain/admin/service/AdminService.java` L108–125

**문제**: 주문 페이지 조회 후 userId 목록으로 `userRepository.findAllById()` 별도 호출. 현재는 단일 `SELECT ... IN (...)` 1쿼리이므로 심각한 수준은 아님.

**개선**: `"usernames"` Redis 캐시(5분 TTL) 추가 또는 Order 쿼리에 username 프로젝션 포함.

---

### 16. OrderExpiryScheduler 순차 취소 → 배치 처리

**위치**: `domain/order/scheduler/OrderExpiryScheduler.java` L38–60

**문제**: 만료 주문마다 개별 트랜잭션으로 `cancelBySystem()` 호출. 100건 발생 시 100회 DB 왕복.

**개선**: 건수 ≤10이면 현행 유지, 초과 시 `UPDATE orders SET status='CANCELLED' WHERE id IN (...)` 벌크 + 재고 일괄 해제.

---

### 20. 리드 레플리카 라우팅 미적용

**위치**: 전체 `@Transactional(readOnly = true)` 쿼리

**문제**: 읽기 전용 트랜잭션이 모두 MySQL Primary로 연결됨. Replica가 있어도 읽기 부하 분산 없음.

**개선**: `AbstractRoutingDataSource` 또는 AWS Aurora Reader Endpoint 활용.

---

## ✅ 기술 부채 완료 (7/8)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 21 | 동시성 통합 테스트 부재 | `ConcurrencyIntegrationTest`, `InventoryConcurrencyTest` | 이미 구현 완료 (ExecutorService + CountDownLatch, reserve() 재고 불변 조건 검증) |
| 22 | Pitest targetClasses 제한적 | `build.gradle` | 6개 → 15개로 확장 (shipment·product·review·wishlist·category·user·address·cart 추가) |
| 23 | 전체 Refresh Token 무효화 불가 | `RefreshTokenStore`, `UserService` | 이미 구현 완료 (`revokeAll()` + 비밀번호 변경 시 자동 호출) |
| 24 | 쿠폰 코드 엔트로피 검증 없음 | `CouponCreateRequest` | 이미 구현 완료 (`@Pattern` 영문+숫자 혼합 8자 이상 강제) |
| 26 | Dockerfile HEALTHCHECK 미정의 | `Dockerfile` | 이미 구현 완료 (`--interval=30s --retries=3`) |
| 27 | `storage.enabled` 문서화 누락 | `application.properties` | 이미 구현 완료 (`storage.enabled=false` + 주석) |
| 28 | Payment 도메인 주석 언어 불일치 | `PaymentService`, `PaymentTransactionHelper` | 영어 inline 주석·Javadoc·log 메시지 → 한국어(키워드 영문) 통일 |

## 🟡 미처리 기술 부채 (1/8)

### 25. API 버전 관리 부재

**위치**: 모든 컨트롤러 (`/api/products`, `/api/orders` 등)

**문제**: 버전 prefix 없이 `/api/` 직접 사용. 응답 스키마 변경 시 하위 호환성 관리 불가.

**개선**: `/api/v1/` prefix 도입.

---

## ✅ DBA 진단 — 스키마 결함 및 누락 제약 (9/13)

### ✅ 33. `refunds.user_id DEFAULT 0` — 기존 환불 소유권 검증 영구 실패 (V28 결함)

**위치**: `V29__backfill_refunds_user_id.sql`

**조치**: V29 마이그레이션 추가 — `orders.user_id` JOIN 백필 + `fk_refunds_user` FK 제약 추가.

---

### ✅ 34. `payments` 테이블 — `ENGINE` · `CHARSET` · `COLLATE` 미정의 (V4 결함)

**위치**: `V4__create_payment_tables.sql`

**문제**: V4의 `CREATE TABLE payments` 구문에 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`가 없음. 서버 기본 collation(`utf8mb4_0900_ai_ci`)이 적용되어 다른 테이블(`utf8mb4_unicode_ci`)과 collation이 달라짐. `payments JOIN orders`, `payments JOIN users` 조인 시 `Illegal mix of collations` 에러 또는 인덱스 미사용 가능.

**개선**: V29 마이그레이션:
```sql
ALTER TABLE payments CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

### ✅ 35. `orders` — `(status, created_at)` 복합 인덱스 누락

**위치**: `V23__add_orders_indexes.sql`, `OrderRepository.countByStatusAndCreatedAtBetween()`

**문제**: `DailyOrderStatsScheduler`가 매일 자정 `countByStatusAndCreatedAtBetween(CONFIRMED, start, end)`과 `countByStatusAndCreatedAtBetween(CANCELLED, start, end)`을 2회 실행. 쿼리: `WHERE status = ? AND created_at >= ? AND created_at < ?`. 현재 `idx_orders_status(status)` + `idx_orders_created_at(created_at)` 각각 존재하나 복합 조건에는 단일 인덱스 선택 후 나머지 필터링(filesort). 운영 주문이 누적될수록 성능 저하.

**개선**: V29 마이그레이션:
```sql
ALTER TABLE orders ADD INDEX idx_orders_status_created (status, created_at);
```

---

### ✅ 36. `coupons` — `(valid_until, active)` 복합 인덱스 누락 + 만료 처리 벌크 UPDATE 미적용

**위치**: `CouponRepository.findExpiredActiveCoupons()`, `CouponExpiryScheduler`

**문제**:
- 쿼리 `WHERE valid_until < :now AND active = true` — 인덱스 없으면 전체 coupons 테이블 풀스캔 (매일 새벽 1시 실행)
- `deactivate()` Dirty Checking으로 쿠폰 수만큼 개별 `UPDATE` 발생 — 만료 쿠폰 100건 = 100 UPDATE

**개선**:
- 인덱스: `ADD INDEX idx_coupons_expiry (valid_until, active)` (V29 마이그레이션)
- 벌크 UPDATE: `UPDATE coupons SET active = false WHERE valid_until < :now AND active = true` 단일 쿼리로 대체

---

### ✅ 37. `products.status` 인덱스 누락

**위치**: `V1__init_schema.sql`, `ProductRepository`

**문제**: `ProductRepository`의 `findAllActiveBySpec()`, `findWithSpec()` 등이 `WHERE status = 'ACTIVE'` 조건을 항상 포함. status 인덱스가 V1~V28 어디에도 없어 전체 products 스캔 후 필터링. ES fallback 쿼리에서도 동일하게 사용.

**개선**: V29 마이그레이션:
```sql
ALTER TABLE products ADD INDEX idx_products_status (status);
```

---

### ✅ 38. `cart_items.user_id` — FK 누락, 사용자 탈퇴 시 고아 레코드

**위치**: `V10__create_cart_tables.sql`

**문제**: `cart_items`에 `product_id → products FK`는 있지만 `user_id → users FK`가 없음. `users.deleted_at`(소프트 삭제, V16)에도 불구하고, 사용자 삭제 시 `cart_items` 레코드는 고아(orphan)로 남아 user_id가 무효한 상태로 유지됨.

**개선**: V29 마이그레이션:
```sql
ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id);
```

---

### ✅ 39. `point_transactions.order_id` — FK 누락, 고아 참조 가능

**위치**: `V20__create_point_tables.sql`

**문제**: `point_transactions.order_id BIGINT NULL`이지만 `orders(id)` FK가 없음. 주문 삭제 시 포인트 이력의 `order_id`는 유효하지 않은 ID를 참조하게 되어 데이터 조회 정합성 훼손 가능. `user_id`에는 FK가 있지만 `order_id`에는 없어 일관성 없음.

**개선**: V29 마이그레이션 (ON DELETE SET NULL — 주문 삭제 시 이력 보존):
```sql
ALTER TABLE point_transactions
    ADD CONSTRAINT fk_pt_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL;
```

---

### ✅ 40. `created_at` / `updated_at` — `DEFAULT CURRENT_TIMESTAMP` 없는 테이블 6개

**위치**: V4(`payments`), V10(`cart_items`), V11(`shipments`), V12(`delivery_addresses`), V17(`product_images`), V22(`user_coupons`)

**문제**: 6개 테이블의 `created_at DATETIME(6) NOT NULL`에 `DEFAULT CURRENT_TIMESTAMP(6)`이 없음. Hibernate `@CreationTimestamp`에만 의존 — bulk INSERT 또는 DB 직접 조작 시 `NULL` 삽입으로 `NOT NULL` 제약 위반 또는 묵시적 `'0000-00-00'` 저장 가능. 나머지 테이블들(V1~V3, V5, V6 등)은 모두 DEFAULT가 있어 불일치.

**개선**: V29 마이그레이션으로 각 컬럼 ALTER (6개 테이블):
```sql
ALTER TABLE payments   MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE shipments  MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
-- ... (4개 더)
```

---

### 41. `DECIMAL` precision 불일치 — `DECIMAL(19,2)` vs `DECIMAL(12,2)`

**위치**: `V13__create_coupon_tables.sql`, `V21__create_refunds_table.sql`, `V15__create_batch_tables.sql`

**문제**: 같은 비즈니스 도메인(금액) 컬럼이 두 precision으로 혼재:
- `DECIMAL(12,2)`: `orders.total_amount`, `payments.amount`, `inventory_transactions` 스냅샷값
- `DECIMAL(19,2)`: `coupons.discount_value`, `coupon_usages.discount_amount`, `refunds.amount`, `daily_order_stats.total_revenue`

`orders.total_amount(12,2)`와 `coupons.discount_value(19,2)` 비교 시 묵시적 캐스팅 발생. 최대 12자리 금액(9,999,999.99원)으로 충분한 도메인에서 DECIMAL(19,2)는 불필요하게 큰 저장 공간 사용.

**개선**: 금액 컬럼 precision을 `DECIMAL(15,2)`로 통일 (최대 999억원 지원, 일별 매출·누적 집계에 충분).

---

### 42. `delivery_addresses` — 기본 배송지 복수 방지 DB 제약 없음

**위치**: `V12__create_delivery_address_tables.sql`, `DeliveryAddressService`

**문제**: `is_default TINYINT(1)` 컬럼만 있고 "사용자별 `is_default = 1` 레코드는 최대 1개" 제약이 DB에 없음. 애플리케이션 버그 또는 직접 DB 수정 시 기본 배송지가 여러 개 생성될 수 있음. MySQL은 partial unique index를 지원하지 않으므로 트리거 또는 CHECK 제약으로 보완 필요.

**개선 옵션**:
1. `BEFORE INSERT/UPDATE` 트리거 — `is_default = 1` 설정 시 동일 user의 다른 레코드를 0으로 초기화
2. 애플리케이션 레벨 검증 강화 + 정기 정합성 점검 쿼리 추가

---

### 43. `orders.findOrderStats()` — 전체 테이블 GROUP BY (대시보드 성능)

**위치**: `OrderRepository.findOrderStats()`, `AdminService.getDashboard()`

**문제**: `SELECT status, COUNT(*), SUM(totalAmount) FROM orders GROUP BY status` — 주문 데이터가 누적될수록 대시보드 API 응답 지연 증가. 현재 `idx_orders_status` 인덱스로 인덱스 스캔은 가능하지만 전체 행을 읽음. `daily_order_stats` 테이블이 이미 존재하지만 실시간 대시보드는 live orders 테이블 집계 사용.

**개선**: 대시보드 통계를 `daily_order_stats` 캐시 + 당일 분만 live 집계로 전환. 또는 `AdminService` 응답에 Redis 캐시(5분 TTL) 적용.

---

### ✅ 44. 일부 테이블 `COLLATE` 미정의 — collation 불일치

**위치**: `V15`(`daily_order_stats`, `daily_inventory_snapshots`), `V18`(`outbox_events`)

**문제**: 3개 테이블이 `DEFAULT CHARSET=utf8mb4` 없이 생성되거나 COLLATE 없이 생성됨. 서버 기본 collation(`utf8mb4_0900_ai_ci`)이 적용되어 나머지 테이블(`utf8mb4_unicode_ci`)과 불일치. 직접 JOIN은 없지만 `SHOW CREATE TABLE`에서 혼재 확인 → 운영 이관 시 혼란.

**개선**: V29 마이그레이션:
```sql
ALTER TABLE daily_order_stats        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE daily_inventory_snapshots CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE outbox_events             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

### 45. `refunds.payment_id UNIQUE` — `PARTIAL_CANCELLED`(부분 취소) 여러 건 이력 불가

**위치**: `V21__create_refunds_table.sql`, `payments.status = PARTIAL_CANCELLED`

**문제**: `payments.status`에 `PARTIAL_CANCELLED`(부분 취소) 상태가 정의되어 있어 한 결제에 대해 여러 차례 부분 환불이 가능해야 하지만, `refunds.payment_id UNIQUE` 제약이 결제당 단 1건의 환불 레코드만 허용. 부분 취소 두 번째 시도 시 DB에서 `Duplicate entry` 에러 발생.

**개선**: 부분 취소 이력이 필요하면 UNIQUE 제약 제거 + `(payment_id, created_at)` 복합 인덱스 전환. 현재는 `reset()` 재사용으로 FAILED 재시도만 지원하나, 부분 취소 누적 이력은 불가.

---

> **요약**: #33~#40 · #44 완료(V29~V34) → 아키텍처 판단(#41~#43 · #45) 보류

---

## ✅ 신규 발굴 성능 병목 (4/4)

### ✅ 29. ReviewService · WishlistService `findByUsername()` DB round-trip

**위치**: `ReviewService.create/update/delete`, `WishlistService.add/remove/isWishlisted/getList`

**문제**: 7개 메서드 전부 `userRepository.findByUsername(username)` 쿼리로 시작. JWT claim에 이미 `userId`가 포함되어 있음에도 매 요청마다 불필요한 DB round-trip 발생. `OrderService`, `ShipmentService`는 이미 `resolveUserId()` 패턴으로 전환됨.

**개선**: 컨트롤러에서 `@AuthenticationPrincipal String username` → `Long userId` (`resolveUserId()` 헬퍼 패턴) 전환. 7개 write 메서드에서 `userRepository.findByUsername()` 제거.

---

### ✅ 30. OutboxEventRelayScheduler 지수 백오프 없음

**위치**: `OutboxEventRepository.findPendingEvents()`, `OutboxEventRelayScheduler`

**문제**: 실패한 이벤트(`failedAt != null`)를 5초 후 무조건 재시도. 다운스트림(ShipmentService · PointService · EmailService) 장애 시 100건 × 5초 간격으로 연속 폭격 발생. `failedAt` 컬럼이 존재하지만 relay 쿼리에서 전혀 활용되지 않음.

**개선**: `findPendingEvents()` 쿼리에 `AND (e.failedAt IS NULL OR e.failedAt <= :cutoff)` 조건 추가. cutoff = `now - min(retryCount² × 5초, 300초)`. 재시도 간격: 1회→5s, 2회→20s, 3회→45s, 4회→80s, 5회→125s.

---

### ~~31. `outbox_events` 쿼리 인덱스 누락~~ ✅ 이미 구현됨

`V18__create_outbox_events.sql`에 `INDEX idx_outbox_unpublished (published_at, retry_count, created_at)` 이미 포함. 조치 불필요.

---

### ✅ 32. ReviewService.create() 불필요한 `Product` 전체 엔티티 로드

**위치**: `ReviewService.create()` L40-41, `ReviewService.getList()` L65-66

**문제**: `productRepository.findById(productId)`로 Product + category(Lazy) 전체를 로드하지만 실제 사용 필드는 `product.getId()` 하나뿐. 1인 1리뷰 검증(`existsByProductIdAndUserId`)과 구매 이력 확인(`existsPurchaseByUserIdAndProductId`)에 productId를 직접 사용 가능.

**개선**: `productRepository.findById()` → `productRepository.existsById()` 전환. 불필요한 Product 객체 생성·category JOIN 제거.

---

## ✅ 완료

### 보안·버그 수정

| 항목 | 비고 |
|------|------|
| PaymentService JWT userId 적용 | DB 라운드트립 제거, `resolveUserId()` 패턴 |
| ES 상품 동기화 | `@TransactionalEventListener(AFTER_COMMIT)` + `ProductEventListener` |
| CSP `unsafe-eval` 제거 | `SecurityConfig` script-src 정책 강화 |
| 가상계좌 Webhook 처리 | `applyWebhookConfirmResult()` confirm 흐름 재사용 |
| JWT 시크릿 기본값 경고 | `@PostConstruct` ERROR 로그 |
| RefundService 영구 재시도 불가 버그 | FAILED 상태 환불 `reset()` 후 재사용 |
| LoginRateLimiter Lua 원자화 | `RAtomicLong` → Lua INCR+EXPIRE 원자적 처리 |
| ShipmentService / PointService `REQUIRES_NEW` | `UnexpectedRollbackException` 방지 |
| DeliveryAddressRepository `findFirstBy...` | 기본 배송지 삭제 후 1건만 조회 |

### 기능 구현 완료

| 기능 | 기능 |
|------|------|
| 인증 (로그인/로그아웃/토큰 재발급) | 상품 목록/검색 (Elasticsearch) |
| 상품 상세 (재고 상태·별점 통합) | 카테고리 트리 |
| 장바구니 (품절 상태 포함) | 주문 생성/취소/목록/이력 |
| TossPayments 결제/취소 | 배송 조회 |
| 환불 | 리뷰 작성/수정/조회/삭제 |
| 위시리스트 | 포인트 잔액/이력 |
| 배송지 관리 | 내 쿠폰 목록 / 공개 쿠폰 claim |
| 프로필 수정 / 비밀번호 변경 | 상품 이미지 업로드 (MinIO Presigned URL) |
| 주문 아이템 리뷰 작성 여부 | 주문 상세 통합 응답 |
