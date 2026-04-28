# TODO — 기술적 개선 항목

> 보안 취약점, 프로덕션 버그, 성능 결함, 코드 품질 문제를 우선순위별로 정리한다.
> 완료된 항목은 `docs/IMPROVEMENTS.md` 참조.

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
  Flyway V1~V42 — 스키마 버전 관리
```

**주요 데이터 흐름**: 주문 생성 → 재고 예약(분산 락) → PENDING → Toss 결제 승인 → Outbox 이벤트(배송·포인트) → CONFIRMED → 배송 상태 전이 → 완료

**동시성 전략**: 재고 변경 `@DistributedLock(Redisson)` + `SELECT FOR UPDATE`(이중), 결제 확정 `PaymentIdempotencyManager(Redis SETNX)`, 쿠폰 발급 `findByCodeWithLock(비관적 락)`

---

## 🔴 긴급 — 금전 손실·데이터 정합성 (즉시 수정)

---

## 🟠 중요 — 보안·운영 안정성 (운영 전 권장)

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


### 67. `DailyOrderStatsScheduler` — 4개 개별 쿼리 → 단일 GROUP BY 통합 가능

**위치**: `domain/order/scheduler/DailyOrderStatsScheduler.java:44-49`

**문제**: 전일 통계를 4개 별도 쿼리로 집계한다.

```java
countByCreatedAtBetween(start, end)                        // SELECT COUNT(*)
countByStatusAndCreatedAtBetween(CONFIRMED, start, end)    // SELECT COUNT(*)
countByStatusAndCreatedAtBetween(CANCELLED, start, end)    // SELECT COUNT(*)
sumRevenueByCreatedAtBetween(start, end)                   // SELECT SUM(total_amount)
```

`OrderRepository`에 이미 `findOrderStats()` GROUP BY 메서드가 있지만 날짜 범위 파라미터가 없어 미사용.

**개선**: `findOrderStats(start, end)` — `WHERE created_at BETWEEN :start AND :end GROUP BY status` 단일 쿼리로 count·revenue를 한 번에 조회.

---

### 68. `InventorySnapshotScheduler` — `Page<T>` COUNT 중복 쿼리 → `Slice<T>` 전환

**위치**: `domain/inventory/scheduler/InventorySnapshotScheduler.java:50`

**문제**: `inventoryRepository.findAll(PageRequest.of(pageNum, PAGE_SIZE))`는 매 페이지마다 `SELECT COUNT(*) FROM inventory` 쿼리를 추가로 실행한다. `totalElements`는 첫 번째 페이지에서만 사용하므로 나머지 페이지의 COUNT 쿼리는 불필요한 오버헤드다.

**개선**: `Page<T>` → `Slice<T>` 전환. 또는 `id > :lastId LIMIT n` 커서 쿼리로 교체.

---

### 69. `PaymentService.getByOrderId()` — 소유권 체크에 전체 Order 엔티티 로드

**위치**: `domain/payment/service/PaymentService.java:308`

**문제**: USER 소유권 검증 시 `orderRepository.findById(orderId)`로 Order 전체 엔티티를 로드한다. 목적은 `order.getUserId()` 스칼라 값 추출뿐. `ShipmentService.getByOrderId()`는 이미 `findUserIdById()` 프로젝션으로 해결했으나 미적용.

**개선**: `orderRepository.findUserIdById(orderId)` 스칼라 쿼리로 교체.

---

### 70. `CartService.addOrUpdate()` — 응답 구성용 전체 장바구니 재조회

**위치**: `domain/order/cart/service/CartService.java:92`

**문제**: 항목 추가·수량 변경 후 `return getCart(userId)`를 호출한다. `getCart()`는 내부에서 `findByUserId()` + `findAllByProductIdIn()` 2개 쿼리를 추가로 실행한다.

**개선**: 변경된 CartItem 상태를 in-memory로 응답에 반영하거나, 클라이언트가 별도 GET으로 최신 장바구니를 조회하게 분리.

---

### 74. `PointService.refundByOrder()` — 포인트 미사용 주문도 항상 `SELECT FOR UPDATE`

**위치**: `domain/point/service/PointService.java:147-148`

**문제**: `getOrCreate(userId)`가 메서드 진입 직후 호출되어, 주문에 `USE`/`EARN` 트랜잭션이 없어도 항상 `SELECT ... FOR UPDATE`를 실행한다. 포인트 계정이 없으면 빈 `UserPoint` 레코드(ghost record)를 새로 INSERT한다.

**개선**: `pointTransactionRepository.findByOrderId(orderId)`를 먼저 실행하고, 결과가 비어 있으면 early return.

---

### 75. `RefundService` — `userRepository.findByUsername()` DB round-trip

**위치**: `domain/refund/service/RefundService.java:60`, `:114`, `:124`

**문제**: `requestRefund()`, `getById()`, `getByPaymentId()` 세 메서드 모두 `userRepository.findByUsername(username)`으로 User 엔티티 전체를 로드한다. 목적은 `user.getId()`(Long) 하나뿐이며, JWT 클레임에서 추출한 userId를 파라미터로 받으면 DB 조회 없이 처리 가능하다.

**개선**: 시그니처를 `(String username)` → `(Long userId)`로 변경. 컨트롤러에서 `resolveUserId()` 헬퍼로 JWT claim 추출.

---


### 76. `PaymentTransactionHelper.loadAndValidateForCancel()` — 소유권 체크에 전체 Order 엔티티 로드

**위치**: `domain/payment/service/PaymentTransactionHelper.java:273`

**문제**: USER 소유권 검증 시 `orderRepository.findById(payment.getOrderId())`로 Order 엔티티 전체를 로드한다. `order.getUserId()` 스칼라 값만 필요하며, `findUserIdById()` 스칼라 프로젝션이 이미 존재한다.

**개선**: `orderRepository.findUserIdById(payment.getOrderId())` 스칼라 쿼리로 교체.

---

### 71. `OrderService.getHistory()` — 소유권 체크에 전체 Order 엔티티 로드

**위치**: `domain/order/service/OrderService.java:291`

**문제**: `historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)` 호출 전 소유권 검증을 위해 `orderRepository.findById(orderId)`로 전체 Order 엔티티를 로드한다. items 컬렉션은 이 흐름에서 사용하지 않는다.

**개선**: `findUserIdById()` 스칼라 프로젝션으로 교체 (#49, #69와 동일 패턴).

---

### 25. API 버전 관리 부재

**위치**: 모든 컨트롤러 (`/api/products`, `/api/orders` 등)

**문제**: 버전 prefix 없이 `/api/` 직접 사용. 응답 스키마 변경 시 하위 호환성 관리 불가.

**개선**: `/api/v1/` prefix 도입.

---


### 101. `OutboxEventPurgeScheduler` — 대량 삭제 시 단일 트랜잭션 테이블 잠금

**위치**: `common/outbox/OutboxEventPurgeScheduler.java:33-36`

**문제**: `@Transactional` + `DELETE WHERE publishedAt < :before`가 단일 트랜잭션. 7일간 수만~수십만 건이면 장시간 잠금으로 relay/INSERT 블로킹.

**개선**: 1,000건 단위 배치 삭제 루프 전환.

---

### 103. 커서 스크롤 `size` 파라미터 상한 미검증

**위치**: `domain/order/controller/OrderController.java:133`, `domain/inventory/controller/InventoryController.java:82`

**문제**: `@RequestParam(defaultValue = "20") int size`에 `@Max` 제약 없음. `size=1000000` 전달 시 메모리 과다 사용 및 DB 부하.

**개선**: `@Min(1) @Max(100)` 범위 제약 추가.

---

### 104. `resolveUserId()` 헬퍼 6개 컨트롤러에 동일 코드 복붙

**위치**: `ProductController`, `ReviewController`, `WishlistController`, `ShipmentController`, `RefundController`, `CartController`

**문제**: JWT details에서 userId 추출하는 동일한 4줄 메서드가 6곳에 copy-paste.

**개선**: `SecurityUtils`에 유틸리티 메서드 추출. 또는 `HandlerMethodArgumentResolver`로 `@CurrentUserId Long userId` 어노테이션 자동 주입.

---


### 106. `CartRepository.deleteByUserId()` — N+1 DELETE 쿼리

**위치**: `domain/order/cart/repository/CartRepository.java:25,28`

**문제**: Spring Data JPA 파생 삭제 메서드는 SELECT 후 건별 DELETE. 장바구니 10개 상품 → 11개 쿼리.

**개선**: `@Modifying @Query("DELETE FROM CartItem c WHERE c.userId = :userId")` 벌크 DELETE.

---

### 107. `AdminController.updateRole()` — 마지막 ADMIN 자기 해제 시 ADMIN 0명

**위치**: `domain/admin/controller/AdminController.java:68-72`

**문제**: 유일한 ADMIN이 자기를 USER로 변경하면 시스템에 ADMIN이 0명. DB 직접 조작 외 복구 불가.

**개선**: 현재 ADMIN 수 체크하여 마지막 ADMIN 권한 해제 차단.

---

### 108. `ChangePasswordRequest` — 현재/신규 비밀번호 동일 허용

**위치**: `domain/user/service/UserService.java:167-176`

**문제**: 같은 비밀번호로 변경 시 Refresh Token 전체 폐기(`revokeAll`) 부작용만 발생하고 보안 개선 없음.

**개선**: `currentPassword.equals(newPassword)` 동일 비밀번호 변경 차단.

---

### 109. `Inventory` 엔티티/서비스 간 방어 로직 불일치

**위치**: `domain/inventory/entity/Inventory.java:133-135` vs `domain/inventory/service/InventoryService.java:209-215`

**문제**: 서비스에서 `reserved < quantity` 시 예외를 던지지만, 엔티티의 `releaseReservation()`은 `Math.max(0, reserved - quantity)`로 음수를 조용히 0으로 클램핑. 다른 호출 경로 추가 시 데이터 불일치를 조용히 삼킬 수 있다. `confirmAllocation()`도 동일 (#117 참조).

**개선**: 엔티티 메서드가 자체적으로 `quantity > reserved` 시 예외를 던지도록 통일. `Math.max(0, ...)` 클램핑 제거.

---

### 126. `Inventory` 엔티티 — `reserve/release/confirm`에 `quantity <= 0` 검증 없음

**위치**: `domain/inventory/entity/Inventory.java:121-156`

**문제**: `receive()`는 `quantity <= 0` 검증이 있지만, `reserve()`, `releaseReservation()`, `confirmAllocation()`, `releaseAllocation()`에는 없다. `reserve(productId, -5)` 호출 시 reserved가 감소하여 가용 재고가 허위로 증가. 현재는 DTO `@Min(1)` 검증이 간접 방어하지만, Entity 자체 방어 없음.

**개선**: 모든 뮤테이션 메서드에 `if (quantity <= 0) throw IllegalArgumentException` 가드 추가.

---

### 127. `InventoryReceiveRequest` / `InventoryAdjustRequest` — `note` `@Size` 누락

**위치**: `domain/inventory/dto/InventoryReceiveRequest.java:22`, `InventoryAdjustRequest.java:20`

**문제**: `note` 필드에 `@Size(max=255)` 없음. `InventoryTransaction.note`는 `@Column(length=255)`. 초과 시 DB 에러.

**개선**: `@Size(max = 255)` 추가.

---

### 128. `InventorySpecification` — LIKE 와일드카드 미이스케이프

**위치**: `domain/inventory/repository/InventorySpecification.java:58-60,68-70`

**문제**: 사용자 입력을 `"%" + input + "%"` 로 직접 LIKE 패턴에 결합. `%`, `_` 특수문자를 이스케이프하지 않아 `productName=%%%` 입력 시 전체 테이블 매칭. SQL injection은 아니지만 DoS 벡터.

**개선**: `%`와 `_`를 `\\%`, `\\_`로 이스케이프하는 유틸리티 적용.

---


### 130. 주문 생성 시 포인트+쿠폰 초과 할인 미검증

**위치**: `domain/order/service/OrderService.java:172-212`

**문제**: 쿠폰 할인 + 포인트가 totalAmount를 초과해도 `getPayableAmount()`가 `max(0, ...)`으로 0 클램핑만 한다. 10,000원 상품에 5,000원 쿠폰 + 8,000포인트 적용 시 실제 필요한 포인트는 5,000인데 8,000이 차감된다. 취소 시 8,000 포인트가 환불되므로 결과적으로는 정상이지만, "결제 금액 0원" 주문이 생성되어 Toss 결제 없이 자동 확정되는 로직이 없으면 만료 취소된다.

**개선**: `usePoints`가 `totalAmount - couponDiscount`를 초과하면 에러 반환 또는 초과분 자동 잘라내기.

---

### 131. `CartService.addOrUpdate()` — 동시 호출 시 중복 CartItem 생성

**위치**: `domain/order/cart/service/CartService.java:79-92`

**문제**: `findByUserIdAndProductId()` 조회 후 `save()` 사이에 갭이 있어 같은 사용자가 같은 상품으로 동시 요청 시 UNIQUE 제약 위반 예외. 500 에러 반환.

**개선**: `DataIntegrityViolationException` catch 후 update fallback. 또는 비관적 락.

---

### 132. `OrderRepository` 통계 쿼리 범위 불일치

**위치**: `domain/order/repository/OrderRepository.java:145,148-151`

**문제**: `countByCreatedAtBetween`은 Spring Data BETWEEN (end inclusive), `countByStatusAndCreatedAtBetween`은 `@Query`로 `< :end` (end exclusive). `end = yesterday.atTime(LocalTime.MAX)`이므로 실질 차이는 미미하지만 의도가 불일치.

**개선**: 두 쿼리를 `>= start AND < end`로 통일, end를 `today.atStartOfDay()`로 전달.

---

### 133. `PaymentService.handleWebhook()` — JSON 파싱 실패 시 500 반환

**위치**: `domain/payment/controller/PaymentController.java:86`

**문제**: `objectMapper.readValue()` 실패 시 500 반환. Toss는 비-2xx 응답에 재시도하므로, malformed payload에 대해 최대 7회 불필요한 재시도를 받는다.

**개선**: `JsonProcessingException` catch 후 로그 남기고 200 반환.

---

### 111. `RefundRequest.reason` 길이 검증 없음

**위치**: `domain/refund/dto/RefundRequest.java:15`

**문제**: `@NotBlank`만 있고 `@Size(max=300)` 없음. `Refund.reason` 컬럼은 `length=300`이므로 초과 입력 시 DB truncation 에러.

**개선**: `@Size(max = 300)` 추가.

### 144. `CouponCreateRequest` — PERCENTAGE 타입 `discountValue` 상한 미검증

**위치**: `domain/coupon/dto/CouponCreateRequest.java`

**문제**: `@DecimalMin("0.01")` + `@DecimalMax("999999999")`로 FIXED_AMOUNT와 PERCENTAGE를 동일하게 검증한다. PERCENTAGE 쿠폰에 `discountValue=500`을 설정하면 500% 할인이 저장된다. 서비스 레벨에서 `discountValue > 100` 체크가 있지만, DTO 검증과 이중 검증이 불일치.

**개선**: `CouponService.create()`에서 PERCENTAGE 시 `0 < discountValue <= 100` 검증 강화. 또는 커스텀 `@Valid` 그룹으로 타입별 DTO 분리.

---

### 145. `UserPoint` 엔티티 — `earn()/use()/refund()` 메서드에 `amount <= 0` 검증 없음

**위치**: `domain/point/entity/UserPoint.java:46-61`

**문제**: `earn()`, `use()`, `refund()` 모든 뮤테이션 메서드에 `amount <= 0` 가드가 없다. `Inventory` 엔티티의 `receive()`에는 검증이 있지만(#126과 동일 패턴) `UserPoint`에는 없다. `use(-100)` 호출 시 `balance -= (-100)` → 잔액 증가.

**개선**: 모든 뮤테이션 메서드에 `if (amount <= 0) throw new IllegalArgumentException` 가드 추가.

---

### 146. `PointTransaction` — `(order_id, type)` 복합 UNIQUE 제약 부재

**위치**: `domain/point/repository/PointTransactionRepository.java`, Flyway 마이그레이션

**문제**: `PointService.earn()`이 `existsByOrderIdAndType(orderId, EARN)`으로 멱등성을 체크하지만, DB 레벨 UNIQUE 제약이 없다. Outbox relay 재시도 등으로 두 스레드가 동시에 `exists()==false`를 받으면 둘 다 INSERT 성공 → 이중 적립. #114(주문 멱등성)과 동일 패턴.

**개선**: Flyway 마이그레이션에서 `ALTER TABLE point_transactions ADD UNIQUE INDEX uk_order_type (order_id, type)` 추가. `DataIntegrityViolationException` catch 후 early return.

---

### 147. `CouponService` — 쿠폰 적용·반환 로깅 부재

**위치**: `domain/coupon/service/CouponService.java`

**문제**: `applyCoupon()`, `releaseCoupon()`, `claim()` 등 주요 비즈니스 메서드에 로그가 없다. 쿠폰 남용·이상 사용 패턴 감지 및 감사(audit) 추적이 불가능.

**개선**: 주요 메서드에 `log.info()` 추가 — userId, couponCode, orderId, discountAmount 포함.

---

### 148. `PointService.refundByOrder()` — 부분 회수 시 모니터링 불가

**위치**: `domain/point/service/PointService.java:168-186`

**문제**: `actualReclaim = Math.min(reclaimAmount, balance)`로 잔액 부족 시 부분 회수만 하고 WARN 로그만 남긴다. 환불은 성공으로 처리되지만 사용자에게 포인트가 전액 복구되지 않는다. 로그만으로는 운영 모니터링·대시보드 집계가 어렵다.

**개선**: 부분 회수 발생 시 별도 메트릭(`point.partial_reclaim`) 발행. 또는 Prometheus 카운터로 누적.

### 149. `ProductDocument.toProductResponse()` — ES 검색 결과에 재고·리뷰 통계 누락

**위치**: `domain/product/document/ProductDocument.java:79-90`

**문제**: ES 검색 결과에서 `availableQuantity`, `avgRating`, `reviewCount`가 null로 반환된다. MySQL `getById()` 응답에는 이 필드가 채워져 있어, 동일 API(`GET /api/products`)가 ES/MySQL 중 어디서 조회되느냐에 따라 응답 구조가 달라진다. 프론트엔드가 null 방어 처리를 해야 한다.

**개선**: ProductDocument에 해당 필드 추가 후 색인 시 DB에서 채우기. 또는 ES 검색 결과에 대해 DB 보강 쿼리 추가(배치 조회).

---

### 150. `CategoryService.getById()` — `@Cacheable` 누락

**위치**: `domain/product/category/service/CategoryService.java`

**문제**: `getList()`와 `getTree()`에는 `@Cacheable`이 있지만 `getById()`에는 없다. 단건 조회 시마다 DB에서 children 로드 + `countActiveProductsByCategoryIds()` 배치 쿼리 2회 실행. 상품 상세 페이지에서 카테고리 정보를 반복 조회할 때 불필요한 DB 히트.

**개선**: `@Cacheable(cacheNames="categories", key="'id_'+#id")` 추가. `update()`/`delete()`의 `@CacheEvict`에 해당 키도 무효화.

---


### 154. `ReviewCreateRequest` / `ReviewUpdateRequest` — `content` `@Size` 누락

**위치**: `domain/product/review/dto/ReviewCreateRequest.java`, `ReviewUpdateRequest.java`

**문제**: `content` 필드에 `@NotBlank`만 있고 `@Size(max)` 없음. `Review.content`는 `@Column(columnDefinition="TEXT")`로 DB 레벨 제약이 느슨하다. 수십 MB 문자열 전송 시 메모리 과다 사용.

**개선**: `@Size(max = 5000)` 추가.

---


### 157. 탈퇴 사용자 리뷰 — username null 노출

**위치**: `domain/product/review/service/ReviewService.java`

**문제**: 사용자 탈퇴(soft delete) 시 `userRepository.findAllById()`가 `@SQLRestriction`으로 필터링하여 해당 사용자를 반환하지 않는다. `usernameMap.get(userId)`가 null → `maskUsername(null)` → null 반환. 클라이언트에 `"username": null` 노출.

**개선**: `usernameMap`에서 userId를 찾을 수 없으면 `"[탈퇴사용자]"` 기본값으로 대체.

---

### 158. `ProductCreateRequest` / `ProductUpdateRequest` — `description` `@Size` 누락

**위치**: `domain/product/dto/ProductCreateRequest.java`, `ProductUpdateRequest.java`

**문제**: `description` 필드에 `@Size(max)` 없음. `Product.description`은 `@Column(columnDefinition="TEXT")`로 무제한. 수십 MB 설명 전송 시 메모리·DB 오���헤드.

**개선**: `@Size(max = 10000)` 추��.


## ⚪ 보류 — 판단 필요 또는 인프라 의존

---

### 43. 대시보�� GROUP BY — `daily_order_stats` 미활용

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
| ~~60~~ | Toss Webhook Replay Attack | 일반 결제 웹훅에 타임스탬프 헤더 없음. `PaymentIdempotencyManager` + 상태 전이 검증으로 충분. |
| ~~61~~ | CSP `unsafe-inline` | REST API 서버 — HTML 직접 렌더링 없음. `/admin-ui`(SBA)가 인라인 스크립트 의존하여 제거 불가. |
| ~~65~~ | `GlobalExceptionHandler` 예외 메시지 노출 | 폴백 핸들러가 `ErrorCode.INTERNAL_SERVER_ERROR.getMessage()` 고정 메시지만 반환. 이미 올바르게 구현됨. |
| ~~90~~ | `EmailService.buildItemsTable()` LazyInitializationException | `OrderRepository.findByIdWithItems()`에 `JOIN FETCH i.product` 이미 포함 — 발생 불가. |
