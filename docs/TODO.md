# TODO — 기술적 개선 항목

> 보안 취약점, 프로덕션 버그, 성능 결함, 코드 품질 문제를 우선순위별로 정리한다.

---

## 아키텍처 요약

```
Client
  │
  ├─ AuthController (JWT 발급)
  ├─ ProductController / CategoryController / ProductImageController
  ├─ InventoryController
  ├─ OrderController → OrderService(God Object) → InventoryService · CouponService · PointService
  ├─ PaymentController → PaymentService → PaymentTransactionHelper → OrderService (역방향 의존)
  │                                                                 └─ OutboxEventStore
  ├─ ShipmentController → ShipmentService
  ├─ RefundController → RefundService → PaymentService
  ├─ CartController → CartService → OrderService
  ├─ WishlistController / ReviewController / PointController
  └─ AdminController → AdminService

공통 인프라:
  Redis — JWT 블랙리스트 · Refresh Token · Cache · Rate Limit · 분산 락 · Outbox relay 락
  Elasticsearch — 상품 전문 검색 (MySQL fallback)
  MinIO(S3) — 상품 이미지 Presigned URL
  OutboxEventStore — ORDER_CREATED/CANCELLED · PAYMENT_CONFIRMED · SHIPMENT_CREATE · POINT_EARN
  Flyway V1~V34 — 스키마 버전 관리
```

**주요 데이터 흐름**: 주문 생성 → 재고 예약(분산 락) → PENDING → Toss 결제 승인 → Outbox 이벤트(배송·포인트) → CONFIRMED → 배송 상태 전이 → 완료

**동시성 전략**: 재고 변경 `@DistributedLock(Redisson)` + `SELECT FOR UPDATE`(이중), 결제 확정 `PaymentIdempotencyManager(Redis SETNX)`, 쿠폰 발급 `findByCodeWithLock(비관적 락)`

---

## 🔴 긴급 — 프로덕션 버그 (데이터 손실·사용자 피해)

> 운영 중 발생 시 사용자 과금 또는 데이터 손실로 직결되는 항목.

---

### 52. Toss 승인 완료 후 주문 만료 경합 — 결제됐는데 주문 취소

**위치**: `domain/payment/service/PaymentTransactionHelper.java` `applyConfirmResult()`

**문제**: Toss API 승인 요청 중(`callTossConfirmApi()` 대기, 최대 30초) 만료 스케줄러가 Order를 CANCELLED로 변경 가능. Toss는 결제를 완료했으나 주문은 취소된 상태 → 사용자는 청구됨, 서버는 `log.error("[Payment] CRITICAL: …수동 환불 필요…")` 로그만 출력하고 끝.

**현황**: 실제 발생 시 운영자가 로그를 확인하고 수동 환불해야 함. 자동 감지·알림 없음.

**개선 방향**:
- 결제 승인 직전 Order 상태를 `PAYMENT_IN_PROGRESS`로 선점 → 만료 스케줄러가 건드리지 못하게 차단 (근본 해결)
- 단기: `dead_letter_payments` 테이블에 레코드 삽입 → 대시보드에서 미처리 건 추적

---

## 🟠 중요 — 보안·운영 안정성 (운영 전 권장)

---

### 59. MySQL `useSSL=false` + `allowPublicKeyRetrieval=true` — 평문 DB 통신

**위치**: `src/main/resources/application.properties` datasource URL

**문제**: DB 연결이 평문 전송. 같은 네트워크에서 패킷 스니핑 시 쿼리·결과·자격증명 노출 가능. `allowPublicKeyRetrieval=true`는 MITM 공격자가 MySQL 서버로 위장해 비밀번호를 탈취할 수 있는 추가 벡터.

**개선**: 운영 환경에서 `useSSL=true&requireSSL=true`로 변경. `DB_SSL_ENABLED` 환경변수로 개발/운영 분리.

---

### 62. DB 자격증명 — `root/root` 하드코딩

**위치**: `src/main/resources/application.properties` `spring.datasource.username/password`

**문제**: 환경변수 없이 `root/root`가 소스코드에 그대로 존재. JAR 또는 설정 파일이 유출되면 DB 직접 접근 가능. 운영 배포 시 변경 누락 위험.

**개선**: `${DB_USERNAME}` / `${DB_PASSWORD}` (기본값 없이) → 미설정 시 Spring Boot 시작 실패. `application-integration.properties`에 테스트용 H2 계정 명시.

---

### 47. `X-Forwarded-For` IP 추출 오류 — Rate Limit 우회 가능

**위치**: `common/ratelimit/RateLimitAspect.java` `extractClientIp()`

**문제**: `X-Forwarded-For: client, proxy1, proxy2`에서 마지막 IP(`proxy2`)를 추출. 공격자가 헤더를 `X-Forwarded-For: real_ip, attacker_ip`로 조작하면 `attacker_ip`로 Rate Limit 버킷이 생성되어 IP 기반 제한 무력화. 현재 `trust-proxy=true/false` 설정만 있고 프록시 수 기반 추출은 미구현.

**개선**: `rate-limit.trusted-proxy-count` 설정 추가, 헤더 끝에서 N번째 IP 추출.

---

### 45. `refunds.payment_id UNIQUE` — 부분취소 두 번째 시도 시 DB 에러

**위치**: `V21__create_refunds_table.sql`, `RefundService`

**문제**: `payments.status`에 `PARTIAL_CANCELLED`(부분 취소) 상태가 있어 한 결제에 여러 번 부분 환불이 가능해야 하지만, `refunds.payment_id UNIQUE` 제약으로 결제당 1건만 허용. 부분 취소 두 번째 시도 시 `Duplicate entry` DB 에러 발생. 현재 `reset()`으로 FAILED 재시도만 지원, 부분 취소 누적 이력 불가.

**개선**: 부분 취소 지원 시 UNIQUE 제약 제거 + `(payment_id, created_at)` 복합 인덱스 전환.

---

## 🟡 중장기 개선

---

### 51. `OrderService` God Object — 단일 서비스에 책임 과다

**위치**: `domain/order/service/OrderService.java` (430줄)

**문제**: 주문 생성·조회·취소·상태 이력·페이지네이션·쿠폰·포인트·재고·배송지 검증을 단일 클래스가 담당. 테스트 시 Mock이 10개 이상 필요.

**개선 방향**: `OrderQueryService`(조회), `OrderCommandService`(생성·취소), `OrderValidationService`(검증) 분리.

---

### 13. 오프셋 페이지네이션 성능 한계

**위치**: 주문 목록, 재고 이력, 리뷰 등 `Pageable` 엔드포인트

**문제**: `LIMIT ? OFFSET ?`는 페이지 번호가 클수록 앞 레코드를 모두 스캔 후 버림. 대량 이력 보유 사용자의 마지막 페이지 조회 시 풀스캔.

**개선**: `WHERE id < :lastId LIMIT ?` 커서 기반 페이지네이션 전환.

---

### 41. `DECIMAL` precision 불일치 — `DECIMAL(19,2)` vs `DECIMAL(12,2)`

**위치**: `V13`, `V21`, `V15` 마이그레이션

**문제**: 같은 금액 컬럼이 두 precision으로 혼재. `orders.total_amount(12,2)`와 `coupons.discount_value(19,2)` 비교 시 묵시적 캐스팅 발생.

**개선**: `DECIMAL(15,2)`로 통일 (최대 999억원 지원).

---

### 42. `delivery_addresses` — 기본 배송지 복수 방지 DB 제약 없음

**위치**: `V12__create_delivery_address_tables.sql`

**문제**: `is_default = 1` 레코드가 사용자별 최대 1개임을 DB가 보장하지 않음. 직접 DB 수정 시 데이터 정합성 깨짐.

**개선**: `BEFORE INSERT/UPDATE` 트리거로 동일 user의 다른 레코드를 0으로 초기화.

---

### 25. API 버전 관리 부재

**위치**: 모든 컨트롤러 (`/api/products`, `/api/orders` 등)

**문제**: 버전 prefix 없이 `/api/` 직접 사용. 응답 스키마 변경 시 하위 호환성 관리 불가.

**개선**: `/api/v1/` prefix 도입.

---

## ⚪ 보류 — 판단 필요 또는 인프라 의존

---

### 43. 대시보드 GROUP BY — `daily_order_stats` 미활용

**위치**: `OrderRepository.findOrderStats()`, `AdminService.getDashboard()`

**문제**: `SELECT status, COUNT(*), SUM(totalAmount) FROM orders GROUP BY status` — 이미 `daily_order_stats` 집계 테이블이 있는데 미사용. 주문 누적 시 응답 지연.

**개선**: 대시보드 통계를 `daily_order_stats` + 당일 live 집계 조합으로 전환. 또는 Redis 캐시(5분 TTL).

> **보류 이유**: 어드민 전용 기능이고 현재 데이터 규모에서 성능 문제 없음. 운영 후 모니터링으로 판단.

---

### 53. `ProductService` ES Fallback — MySQL LIKE 검색으로 결과 불일치

**위치**: `domain/product/service/ProductService.java` `getList()`

**문제**: ES 장애 시 MySQL `LIKE '%keyword%'` fallback이 ES 형태소 분석 결과와 전혀 다른 결과를 반환. UX 불일치.

**보류 이유**: 빈 결과 반환 vs LIKE fallback 유지 중 어느 쪽이 나은지는 제품 결정 사항.

---

### 20. 리드 레플리카 라우팅 미적용

**위치**: 전체 `@Transactional(readOnly = true)` 쿼리

**보류 이유**: Replica 인프라 없이 코드만으로 완결 불가. Aurora Reader Endpoint 또는 `AbstractRoutingDataSource` 도입 시 재검토.

---

### 16. `OrderExpiryScheduler` 순차 취소 → 배치 처리

**위치**: `domain/order/scheduler/OrderExpiryScheduler.java`

**보류 이유**: 만료 주문 건수가 적은 초기 운영 단계에서는 현행 유지. 100건 초과 시 벌크 UPDATE 전환 검토.

---

### 15. `AdminService.getOrders()` 사용자명 조회

**위치**: `domain/admin/service/AdminService.java`

**보류 이유**: 이미 `findAllById()` 단일 IN 쿼리. 현재 규모에서 심각도 낮음.

---

## ~~조치 불필요~~

---

| # | 항목 | 판단 근거 |
|---|------|-----------|
| ~~60~~ | Toss Webhook Replay Attack | 일반 결제 웹훅에 타임스탬프 헤더 없음. `PaymentIdempotencyManager` + 상태 전이 검증으로 충분. [Toss 문서](https://docs.tosspayments.com/reference/using-api/webhook-events) |
| ~~61~~ | CSP `unsafe-inline` | REST API 서버 — HTML 직접 렌더링 없음. `/admin-ui`(SBA)가 인라인 스크립트 의존하여 제거 불가. 실질 XSS 위험 없음. |
| ~~65~~ | `GlobalExceptionHandler` 예외 메시지 노출 | 코드 확인 결과 폴백 핸들러가 `ErrorCode.INTERNAL_SERVER_ERROR.getMessage()` 고정 메시지만 반환. `DataIntegrityViolationException`도 고정 문자열 사용. 이미 올바르게 구현됨. |

---

## ✅ 완료된 항목

---

### ✅ 즉시 수정 완료 — 프로덕션 데이터 정합성 위험 (6/6)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 1 | 음수 재고 허용 버그 | `InventoryService` | `BusinessException(INVENTORY_STATE_INCONSISTENT)` throw |
| 2 | 포인트 롤백 불일치 | `OrderService.create()` | `validateBalance()` fail-fast 사전 검증 추가 |
| 3 | 배송·포인트 무음 삼킴 | `PaymentTransactionHelper` | Outbox 패턴(`SHIPMENT_CREATE`, `POINT_EARN`) 전환 |
| 4 | null userId NPE | `OrderExpiryScheduler` | `cancelBySystem()` 전용 메서드, Order에서 userId 직접 조회 |
| 5 | saveAll() 예외 무음 삼킴 | `InventorySnapshotScheduler` | `DataIntegrityViolationException` 분리, 장애 시 re-throw |
| 6 | SpEL null NPE | `DistributedLockAspect` | `resolveKey()` null/예외 시 `BusinessException` throw |

---

### ✅ 성능 병목 완료 (7/7)

| # | 항목 | 조치 |
|---|------|------|
| 7 | InventoryRepository N+1 | `@EntityGraph({"product"})` 추가 |
| 8 | CategoryService.getTree() 캐시 미적용 | `@Cacheable` + 30분 TTL |
| 9 | InventorySnapshotScheduler 전체 로드 | 1,000건 페이지 루프 + 배치 독립 트랜잭션 |
| 29 | ReviewService·WishlistService DB round-trip | `resolveUserId()` 헬퍼 패턴, `findByUsername()` 제거 |
| 30 | OutboxEventRelayScheduler 지수 백오프 없음 | `nextRetryAt` 컬럼(V34) + 최대 1시간 백오프 |
| ~~31~~ | `outbox_events` 인덱스 누락 | V18에 이미 `idx_outbox_unpublished` 포함 — 조치 불필요 |
| 32 | ReviewService.create() Product 전체 로드 | `findById()` → `existsById()` |

---

### ✅ 보안·버그 완료

| # | 항목 | 조치 |
|---|------|------|
| 46 | JWT Secret 기본값 → 시작 실패 | `JwtTokenProvider @PostConstruct` `IllegalStateException` |
| 48 | CartService N+1 | `CartRepository.findByUserId()` 이미 `@EntityGraph(product)` 확인 |
| 49 | ShipmentService userId 프로젝션 | `orderRepository.findUserIdById()` 스칼라 쿼리 |
| 50 | CouponService 검증 중복 | `applyCoupon()` → `validateConditions()` 통합 |
| 54 | InventoryService 이중 락 문서화 | `reserve()` Javadoc: Redis 장애 시 DB 락이 최후 방어선 |
| 55 | RefundService noRollbackFor 문서화 | 코드 확인 결과 주석 이미 존재 |
| 56 | PaymentController IDOR | 소유권 검증 + `orderRepository.findUserIdById()` |
| 57 | TossWebhookVerifier Mac 직렬화 | 싱글턴 + `synchronized` → 호출마다 `Mac.getInstance()` |
| 58 | Admin 기본 자격증명 → 시작 실패 | `AdminSecurityConfig @PostConstruct` `IllegalStateException` |
| 63 | Actuator 엔드포인트 과다 노출 | `env/heapdump/threaddump/beans/mappings/loggers` 비활성화 |
| 64 | CouponValidateRequest `@Size` 누락 | `@Size(min=8, max=50)` 추가 |

---

### ✅ 스프린트·기술부채·DBA 완료

| # | 항목 | 조치 |
|---|------|------|
| 10 | applyConfirmResult() 이중 DB 조회 | `confirm()` → `Order` 반환, 재사용 |
| 11 | Outbox 배치 크기 하드코딩 | `@Value("${outbox.relay.batch-size:100}")` + Prometheus counter |
| 12 | DB 인덱스 누락 | V23/V25/V27 마이그레이션 |
| 14 | RefundService 이중 쿼리 | `userId` 비정규화, `validateOwnership()` orders 조회 제거 |
| 17 | P6Spy 운영 오버헤드 | `implementation` → `runtimeOnly` |
| 18 | Zipkin 샘플링 100% | 1.0 → 0.1 |
| 19 | OrderSearchRequest mutable DTO | `of(request, forceUserId)` 오버로드 |
| 21 | 동시성 통합 테스트 부재 | 이미 구현 완료 (ExecutorService + CountDownLatch) |
| 22 | Pitest targetClasses 제한 | 6개 → 15개 확장 |
| 23 | Refresh Token 전체 무효화 불가 | 이미 구현 완료 (`revokeAll()`) |
| 24 | 쿠폰 코드 엔트로피 검증 없음 | 이미 구현 완료 (`@Pattern`) |
| 26 | Dockerfile HEALTHCHECK 미정의 | 이미 구현 완료 |
| 27 | `storage.enabled` 문서화 누락 | 이미 구현 완료 |
| 28 | Payment 도메인 주석 언어 불일치 | 한국어(키워드 영문) 통일 |
| 33 | `refunds.user_id DEFAULT 0` | V29 백필 + FK 제약 |
| 34 | `payments` CHARSET/COLLATE 미정의 | V29 utf8mb4 변환 |
| 35 | `orders` 복합 인덱스 누락 | V29 `idx_orders_status_created` |
| 36 | `coupons` 인덱스 누락 + N UPDATE | V29 인덱스 + `@Modifying` 벌크 UPDATE |
| 37 | `products.status` 인덱스 누락 | V29 `idx_products_status` |
| 38 | `cart_items.user_id` FK 누락 | V29 FK 제약 |
| 39 | `point_transactions.order_id` FK 누락 | V29 FK (ON DELETE SET NULL) |
| 40 | `created_at` DEFAULT 없는 테이블 6개 | V33 ALTER |
| 44 | 일부 테이블 COLLATE 미정의 | V30 charset/collation 통일 |

---

### ✅ 구현 완료 기능

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
