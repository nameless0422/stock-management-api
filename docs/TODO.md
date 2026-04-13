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
