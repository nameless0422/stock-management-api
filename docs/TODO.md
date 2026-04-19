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
  Flyway V1~V37 — 스키마 버전 관리
```

**주요 데이터 흐름**: 주문 생성 → 재고 예약(분산 락) → PENDING → Toss 결제 승인 → Outbox 이벤트(배송·포인트) → CONFIRMED → 배송 상태 전이 → 완료

**동시성 전략**: 재고 변경 `@DistributedLock(Redisson)` + `SELECT FOR UPDATE`(이중), 결제 확정 `PaymentIdempotencyManager(Redis SETNX)`, 쿠폰 발급 `findByCodeWithLock(비관적 락)`

---

## 🔴 긴급 — 프로덕션 버그 (데이터 손실·사용자 피해)

### 80. Payment 금액에 쿠폰/포인트 할인이 반영되지 않음 — 사용자 과다 결제

**위치**: `domain/payment/service/PaymentService.java:95`, `:120`

**문제**: `prepare()`에서 `order.getTotalAmount()`로 금액을 검증하고 Payment에도 원가를 저장한다. `OrderService.create()`에서 쿠폰 할인(`discountAmount`)과 포인트 사용(`usedPoints`)은 `totalAmount`를 변경하지 않고 별도 필드에만 기록하므로, **Payment.amount = 상품 합계 원가**가 된다. Toss에 원가 전액이 결제 요청되어 사용자가 할인·포인트 차감 없이 전액을 지불하게 된다.

**개선**: Order에 `getPayableAmount()` (= `totalAmount - discountAmount - usedPoints`, 최소 0) 계산 메서드를 추가. `prepare()`에서 이 값으로 검증 및 Payment 저장.

---

### 81. 동일 상품 중복 주문 항목 시 `Collectors.toMap` 500 에러

**위치**: `domain/order/service/OrderService.java:131-132`, `:456-457`

**문제**: `request.getItems()`에 동일 `productId`가 2회 이상 포함되면 `Collectors.toMap(Product::getId, p -> p)`에서 `IllegalStateException: Duplicate key`가 발생. 500 에러로 사용자에게 노출된다. `preview()`에서도 동일.

**개선**: 요청 검증에서 중복 `productId` 사전 차단, 또는 `Collectors.toMap`에 merge function(`(a, b) -> a`) 지정.

---

> 아래 #52는 완료 처리됨.

### ✅ 52. Toss 승인 완료 후 주문 만료 경합 — `PAYMENT_IN_PROGRESS` 상태로 해결

**위치**: `domain/payment/service/PaymentTransactionHelper.java`, `domain/order/entity/Order.java`, `domain/order/entity/OrderStatus.java`

**문제(해결됨)**: Toss API 승인 요청 중(`callTossConfirmApi()` 대기, 최대 30초) 만료 스케줄러가 Order를 CANCELLED로 변경 가능. Toss는 결제를 완료했으나 주문은 취소된 상태 → 사용자는 청구됨, 서버는 `CRITICAL` 로그만 출력.

**적용된 해결책** (Stripe `processing` / Magento `payment_review` 동일 패턴):
- `OrderStatus.PAYMENT_IN_PROGRESS` 추가 — Toss HTTP 대기 중 전용 상태
- `loadAndValidateForConfirm()`: 검증 완료 후 `order.startPayment()` 호출 → PENDING → PAYMENT_IN_PROGRESS 커밋
- `findExpiredPendingOrderIds()`: `status = 'PENDING'`만 조회 → PAYMENT_IN_PROGRESS 주문은 자동 스킵
- Toss API 실패 시 catch 블록에서 `resetOrderOnPaymentError()` (`REQUIRES_NEW`) 호출 → PENDING 복원
- `Order.confirm()`: PENDING(가상계좌 Webhook 경로) + PAYMENT_IN_PROGRESS(일반 결제 경로) 양쪽 허용

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

### 66. `PaymentService.confirm()` catch 블록 — `resetOrderOnPaymentError()` 실패 시 Order 영구 PAYMENT_IN_PROGRESS

**위치**: `domain/payment/service/PaymentService.java:166-172`

**문제**: catch 블록 내 호출 순서상 `resetOrderOnPaymentError()`가 예외를 던지면 `release(idempotencyKey)` 가 실행되지 않는다.

```java
catch (Exception e) {
    transactionHelper.resetOrderOnPaymentError(...); // ← 여기서 실패(DB 장애)하면
    idempotencyManager.release(idempotencyKey);       // ← 이 줄은 건너뜀
    throw e;
}
```

- `PROCESSING_TTL = 60초`이므로 Redis 키 자체는 1분 후 자동 만료 → 재시도 허용됨
- **그러나** Order 상태가 PAYMENT_IN_PROGRESS로 영구 잔존 → 만료 스케줄러가 영원히 스킵 → 사용자 재주문 불가
- DB가 순간 장애 후 복구돼도 이 Order는 stuck 상태로 방치됨

**개선**: `try-finally`로 `release()`를 보장하거나, `resetOrderOnPaymentError()` 내에서 예외를 삼키고 로그만 남기는 방어 처리 추가.

---

### 72. `CouponService.releaseCoupon()` — 쿠폰 `usageCount` lost update 가능

**위치**: `domain/coupon/service/CouponService.java:228`, `domain/coupon/entity/Coupon.java:120`

**문제**: `releaseCoupon()`에서 쿠폰을 LAZY 로드(잠금 없음) 후 `decreaseUsage()`로 dirty checking UPDATE를 한다. 같은 시각에 `applyCoupon()`이 `findByCodeWithLock()`(FOR UPDATE)으로 동일 쿠폰을 잠그고 `increaseUsage()` → 커밋하는 경우, `releaseCoupon()`의 flush가 DB에 이미 커밋된 증가값을 덮어쓰는 **lost update**를 발생시킨다.

```
T1(release) : SELECT coupon (usageCount=5, 잠금 없음) → in-memory: 4
T2(apply)   : SELECT FOR UPDATE (usageCount=5) → 6으로 증가 → 커밋 (DB=6)
T1(flush)   : UPDATE coupons SET usage_count=4  ← DB의 6을 4로 덮어씀
```

결과: `maxUsageCount=5`인 쿠폰이 실제 6회 사용됐음에도 `usageCount=4`로 기록되어 추가 사용이 허용됨.

**개선**: `releaseCoupon()`에서 쿠폰을 `findByIdWithLock()`으로 비관적 잠금 후 조회하거나, `@Modifying @Query("UPDATE coupons SET usage_count = usage_count - 1 WHERE id = ?")` 원자적 SQL로 교체.

---

### 73. `CouponService.claim()` / `issueToUser()` — TOCTOU: 동시 요청 시 제네릭 409 반환

**위치**: `domain/coupon/service/CouponService.java:154`, `:91`

**문제**: `existsByUserIdAndCouponId()` 체크 통과 → `save()` 사이에 동일 사용자의 다른 요청이 check를 통과하면, 양쪽이 INSERT를 시도한다. DB UNIQUE KEY `uk_user_coupons(user_id, coupon_id)`가 중복을 막고, `GlobalExceptionHandler`가 `DataIntegrityViolationException`을 **HTTP 409** 로 변환하므로 500 에러는 발생하지 않는다. 그러나 응답 바디가 `DataIntegrityViolationException` 경로로 내려가므로 `COUPON_ALREADY_ISSUED` 비즈니스 에러 코드 대신 **제네릭 메시지**가 반환된다. `issueToUser()`도 동일 패턴.

**개선**: `save()` 주변에 `DataIntegrityViolationException` catch → `BusinessException(COUPON_ALREADY_ISSUED)` 재발행. 또는 `@DistributedLock`으로 같은 사용자의 동시 claim 직렬화.

---

### 77. Outbox `POINT_EARN` — `earn()` 비멱등성으로 포인트 이중 적립 가능

**위치**: `common/outbox/OutboxEventProcessor.java`, `domain/point/service/PointService.java:96`

**문제**: `processOne()`은 `REQUIRES_NEW` 트랜잭션이고, 내부에서 호출하는 `earn()`도 `REQUIRES_NEW`로 독립 커밋된다. `earn()` 커밋 직후 `processOne` 트랜잭션이 롤백(DB 순간 장애, 네트워크 등)되면 `outbox.markPublished()`가 반영되지 않는다. 다음 relay 사이클에서 같은 Outbox 이벤트를 다시 처리 → `earn()` 재호출 → **포인트 이중 적립**.

```
processOne() REQUIRES_NEW {
    dispatch() → earn() REQUIRES_NEW → 커밋 ✅ (포인트 +10)
    outbox.markPublished()            → 롤백 ❌
}
→ 다음 relay: earn() 재호출 → 포인트 +10 (총 +20)
```

`PointTransactionRepository`에 `existsByOrderIdAndType()` 같은 멱등성 체크가 없으므로 중복 적립이 DB에 그대로 기록된다.

**개선**: `earn()` 내부에서 `pointTransactionRepository.existsByOrderIdAndType(orderId, EARN)` 선행 체크 → 이미 존재하면 early return. 또는 `(userId, orderId, type)` UNIQUE 제약(마이그레이션) 추가.

---

### 78. `PaymentService.cancel()` — `applyCancelResult()` TX 실패 시 Toss "already cancelled" stuck

**위치**: `domain/payment/service/PaymentService.java` `cancel()`, `domain/payment/service/PaymentTransactionHelper.java` `applyCancelResult()`

**문제**: `cancel()`은 `NOT_SUPPORTED`로 실행되며, Toss cancel API 호출 성공 후 `applyCancelResult()` 트랜잭션이 실패(DB 장애, 타임아웃)하면:
- Toss: 결제 취소 완료 (환불 진행)
- DB: `payment.status = DONE`, `order.status = CONFIRMED` 그대로 잔존

이후 재시도 시 `applyCancelResult()` 진입 전 Toss cancel API를 다시 호출 → Toss가 "이미 취소된 결제" 에러 반환 → `BusinessException` throw → DB 상태 영구 미갱신. `RefundService.requestRefund()`에서도 `payment.status != DONE` 체크로 인해 재시도 불가.

**개선**: Toss cancel API 응답 전 `payment`를 `CANCEL_IN_PROGRESS` 중간 상태로 먼저 커밋 (confirm의 `PAYMENT_IN_PROGRESS` 패턴과 동일). `applyCancelResult()` 재시도 시 이미 취소 완료인 경우 Toss API 응답 무시하고 DB 상태만 갱신하는 idempotent 경로 추가.

---

### 85. `CacheConfig` `LaissezFaireSubTypeValidator` — 역직렬화 가젯 공격 벡터

**위치**: `common/config/CacheConfig.java:44-47`

**문제**: `LaissezFaireSubTypeValidator`는 **모든 타입의 역직렬화를 허용**한다. `activateDefaultTyping(NON_FINAL)`과 결합하면, Redis에 악의적 JSON 페이로드 주입 시 임의 클래스 인스턴스화 → RCE 가능.

**개선**: `BasicPolymorphicTypeValidator.builder().allowIfBaseType("com.stockmanagement.").allowIfBaseType("java.util.").allowIfBaseType("java.time.").build()` 화이트리스트 전환.

---

### 97. `AuthController.logout()` — 인증 없이 호출 가능

**위치**: `domain/user/controller/AuthController.java:64-73`

**문제**: `/api/auth/**` 전체가 `permitAll()`이어서 로그아웃도 인증 없이 호출 가능. `Authorization` 헤더만 있으면 JWT 검증 결과와 무관하게 블랙리스트 등록 진행. 제3자가 유효 토큰을 무효화하는 서비스 거부 가능.

**개선**: 로그아웃을 `/api/auth/**` 패턴에서 분리하여 `authenticated()` 규칙 적용. 또는 `revoke()` 전 `validateToken()` 검증.

---

### 82. 회원 탈퇴 시 Access Token 미무효화 — 최대 24시간 API 접근 가능

**위치**: `domain/user/service/UserService.java:132-143`

**문제**: `deactivate()`는 Refresh Token만 폐기(`revokeAll`)하고, Access Token을 블랙리스트에 등록하지 않는다. JWT 유효 시간이 24시간이므로, 탈퇴 직후에도 기존 Access Token으로 API 접근이 가능하다.

**개선**: `deactivate()` 호출 시 현재 요청의 Access Token도 `jwtBlacklist.revoke()`로 등록. 또는 `JwtAuthenticationFilter`에서 사용자 활성 상태를 Redis 캐시 기반 추가 확인.

---

### 83. `LoginRateLimiter` username 단독 제한 — 타인 계정 잠금(Account Lockout DoS) 가능

**위치**: `common/security/LoginRateLimiter.java:44`

**문제**: 공격자가 타인의 username으로 5회 틀린 비밀번호를 입력하면 해당 사용자의 로그인이 15분간 차단된다. IP 기반 제한이 병행되지 않아 IP를 바꾸며 반복 가능.

**개선**: `username + IP` 복합 키 사용, 또는 username 단독 임계값을 20회로 높이고 IP 기반 제한(10회/15분) 추가하는 이중 구조.

---

### 84. `SignupRequest.username` 패턴 검증 없음 — Redis 키 오염 가능

**위치**: `domain/user/dto/SignupRequest.java:9-11`

**문제**: username에 `@NotBlank @Size(min=3, max=50)`만 적용. 콜론(`:`)·줄바꿈(`\n`) 등 특수문자 허용. Redis 키 조합(`rate-limit:login:{username}`, `refresh:user:{username}`)에 직접 사용되어 키 구조 오염, 로그 인젝션, unicode zero-width 문자로 시각적 동일 username 생성 가능.

**개선**: `@Pattern(regexp = "^[a-zA-Z0-9_-]+$")` 추가.

---

### 86. `StorageConfig` `S3Presigner` `forcePathStyle` 누락 — MinIO Presigned URL 호스트 불일치

**위치**: `common/config/StorageConfig.java:44-51`

**문제**: `S3Client`에는 `.forcePathStyle(true)` 설정이 있지만, `S3Presigner`에는 누락. Presigner가 virtual-hosted style URL(`http://bucket.endpoint/key`)을 생성하여 MinIO 환경에서 DNS 해석 실패.

**개선**: `S3Presigner.builder()` 체인에 `.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())` 추가.

---

### 87. `ProductService.update()` `@CachePut`이 이미지 없는 응답을 캐시 — getById()와 불일치

**위치**: `domain/product/service/ProductService.java:182-189`

**문제**: `update()`는 `@CachePut`으로 캐시를 갱신하지만 `buildProductResponse()`(이미지 **미포함**)를 호출한다. `getById()`는 `buildProductResponseWithImages()`(이미지 **포함**)를 캐시에 저장. `update()` 후 캐시에 이미지 없는 응답이 들어가고, 이후 `getById()`가 이 캐시를 반환하므로 이미지가 사라져 보인다.

**개선**: `update()`에서도 `buildProductResponseWithImages()` 사용. 또는 `@CachePut` 대신 `@CacheEvict`로 무효화.

---

### 88. `ProductImageService` 이미지 변경 시 products 캐시 미무효화

**위치**: `domain/product/image/service/ProductImageService.java`

**문제**: `getById()`가 `@Cacheable`이고 이미지 목록을 캐시에 포함한다. 그런데 `saveImage()`/`deleteImage()`/`updateImageOrder()`에서 `products` 캐시를 evict하지 않아, 이미지 등록/삭제 후에도 이전 이미지 목록이 포함된 캐시가 반환된다.

**개선**: `ProductImageService`의 변경 메서드에 `@CacheEvict(cacheNames = "products", key = "#productId")` 추가.

---

### 89. `PresignedUrlRequest.fileExtension` 문자 검증 없음 — 오브젝트 키 구조 오염

**위치**: `domain/product/image/dto/PresignedUrlRequest.java:12`

**문제**: `@NotBlank`만 있고 허용 확장자/문자 검증 없음. `fileExtension = "../../etc/passwd"` 같은 값으로 오브젝트 키 구조가 오염된다.

**개선**: `@Pattern(regexp = "^[a-zA-Z0-9]{1,10}$")` 허용 확장자 패턴 추가.

---

### 90. `EmailService.buildItemsTable()` `item.getProduct()` — `@Async` 스레드에서 LazyInitializationException 가능성

**위치**: `common/email/EmailService.java:213`

**문제**: `@Async` 비동기 스레드에서 `item.getProduct().getName()` 호출. `EmailEventListener`에 `@Transactional(readOnly=true)`가 있어 새 영속성 컨텍스트가 열리지만, `findByIdWithItems()`가 `product`까지 fetch join하지 않으면 `LazyInitializationException` 발생.

**개선**: `OrderRepository.findByIdWithItems()`가 `JOIN FETCH i.product`까지 포함하는지 확인. 미포함 시 쿼리 확장 또는 이벤트 페이로드에 product name 포함.

---

### 91. `PointService.getOrCreate()` 동시 요청 시 Duplicate Key 예외

**위치**: `domain/point/service/PointService.java:191-194`

**문제**: 동일 사용자의 첫 주문 2건이 동시에 들어오면, 두 스레드 모두 `findByUserIdWithLock()` empty → INSERT 시도 → `user_points.user_id UNIQUE` 위반. `PointService.earn()`은 `REQUIRES_NEW`로 독립 트랜잭션이므로 결제 확인 전체가 롤백되지는 않지만 포인트 적립이 누락된다.

**개선**: `DataIntegrityViolationException` catch 후 재조회 retry 패턴 적용.

---

### 92. `CouponCreateRequest` 날짜 교차 검증 없음 + PERCENTAGE 100% 초과 허용

**위치**: `domain/coupon/dto/CouponCreateRequest.java`

**문제**: (1) `validFrom >= validUntil`이면 즉시 만료 상태 쿠폰이 생성됨. (2) `discountType=PERCENTAGE`일 때 `discountValue > 100` 허용. `maxDiscountAmount` 설정에 따라 의도치 않은 할인 금액 발생 가능.

**개선**: (1) `validFrom.isAfter(validUntil)` 검증. (2) PERCENTAGE 시 `discountValue <= 100` 검증 추가.

---

### 93. `WishlistService.getList()` 필터링 후 `totalElements` 불일치

**위치**: `domain/product/wishlist/service/WishlistService.java:98-105`

**문제**: soft-deleted 상품을 필터링한 후 `PageImpl`의 `totalElements`를 원래 값 그대로 전달. 프론트엔드가 "전체 N건" 표시나 페이지 수 계산 시 부정확.

**개선**: DB 쿼리 레벨에서 DISCONTINUED 상품 제외(`JOIN Product WHERE status = ACTIVE`). 또는 필터링 후 content 크기 기반으로 totalElements 재계산.

---

### 94. `RefundService.requestRefund()` 동시 요청 시 Duplicate Key → 500 에러

**위치**: `domain/refund/service/RefundService.java:83-97`

**문제**: 동일 결제에 대해 동시 환불 요청 시 두 스레드 모두 `findByPaymentId()` empty → INSERT → `refunds.payment_id UNIQUE` 위반. `noRollbackFor=Exception.class`이지만 Hibernate가 `rollback-only` 표시 → `UnexpectedRollbackException` → 500 에러.

**개선**: `save()` 주변에서 `DataIntegrityViolationException` catch 후 재조회. 또는 비관적 락으로 직렬화.

---

### 102. `PaymentTransactionHelper.applyConfirmResult()` non-DONE 응답 시 `fail()` 롤백 가능성

**위치**: `domain/payment/service/PaymentTransactionHelper.java:145-157`

**문제**: Toss 응답이 DONE이 아니면 `payment.fail()` 후 `BusinessException` throw. 예외로 인해 Spring이 rollback-only 표시 → `fail()` dirty checking 미커밋. Payment가 PENDING으로 잔존하여 재시도 시 Toss API 재호출.

**개선**: non-DONE 처리를 `REQUIRES_NEW` 트랜잭션으로 분리. 또는 에러 상태를 반환값으로 전달.

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

**개선**: `findOrderStats(start, end)` — `WHERE created_at BETWEEN :start AND :end GROUP BY status` 단일 쿼리로 count·revenue를 한 번에 조회. 일 1회 실행이므로 체감 영향은 미미하나, 패턴 정리 차원.

---

### 68. `InventorySnapshotScheduler` — `Page<T>` COUNT 중복 쿼리 → `Slice<T>` 전환

**위치**: `domain/inventory/scheduler/InventorySnapshotScheduler.java:50`

**문제**: `inventoryRepository.findAll(PageRequest.of(pageNum, PAGE_SIZE))`는 Spring Data JPA `Page<T>` 계약상 매 페이지마다 `SELECT COUNT(*) FROM inventory` 쿼리를 추가로 실행한다. `totalElements`는 첫 번째 페이지(line 54)에서만 사용하고 이후 페이지에서는 `isLast()` 만 체크하므로 나머지 페이지의 COUNT 쿼리는 불필요한 오버헤드다.

**개선**: `Page<T>` → `Slice<T>` 전환. 첫 페이지만 `Page<T>`로 조회해 `totalElements`를 캡처하고 이후 페이지는 `Slice<T>`를 사용하거나, `findAll(PageRequest…)` 대신 `id > :lastId LIMIT n` 커서 쿼리로 교체.

---

### 69. `PaymentService.getByOrderId()` — 소유권 체크에 전체 Order 엔티티 로드

**위치**: `domain/payment/service/PaymentService.java:308`

**문제**: USER 소유권 검증 시 `orderRepository.findById(orderId)`로 Order 전체 엔티티를 로드한다. 목적은 `order.getUserId()` 스칼라 값 추출뿐이며, `LazyInitializationException` 방지 목적의 fetch join도 불필요하다.

```java
Order order = orderRepository.findById(orderId)  // 전체 엔티티 로드
        .orElseThrow(...);
if (!order.getUserId().equals(userId)) { ... }
```

`ShipmentService.getByOrderId()`는 이미 동일 문제를 `findUserIdById()` 프로젝션으로 해결했으나, `PaymentService`는 미적용 상태.

**개선**: `orderRepository.findUserIdById(orderId)` 스칼라 쿼리로 교체 (일관성 + 불필요 컬럼 로드 제거).

---

### 70. `CartService.addOrUpdate()` — 응답 구성용 전체 장바구니 재조회

**위치**: `domain/order/cart/service/CartService.java:92`

**문제**: 항목 추가·수량 변경 후 `return getCart(userId)`를 호출한다. `getCart()`는 내부에서 `findByUserId()` + `findAllByProductIdIn()` 2개 쿼리를 추가로 실행한다. 방금 저장한 항목을 포함한 장바구니를 다시 전부 다시 읽는 구조다.

**개선**: 변경된 CartItem 상태를 in-memory로 응답에 반영하거나, `addOrUpdate()` 반환 타입을 `void`/단건 DTO로 바꾸고 클라이언트가 별도 GET으로 최신 장바구니를 조회하게 분리.

---

### 74. `PointService.refundByOrder()` — 포인트 미사용 주문도 항상 `SELECT FOR UPDATE`

**위치**: `domain/point/service/PointService.java:147-148`

**문제**: `getOrCreate(userId)`가 메서드 진입 직후 호출되어, 주문에 `USE`/`EARN` 트랜잭션이 없어도 항상 `SELECT ... FOR UPDATE`를 실행한다 (포인트를 전혀 사용하지 않은 주문 취소 포함). 포인트 계정이 없으면 빈 `UserPoint` 레코드를 새로 INSERT한다 — 이 레코드는 사용되지 않는 유령(ghost) 레코드다.

**개선**: `pointTransactionRepository.findByOrderId(orderId)`를 먼저 실행하고, 결과가 비어 있으면 `getOrCreate()` 없이 early return.

---

### 75. `RefundService` — `userRepository.findByUsername()` DB round-trip

**위치**: `domain/refund/service/RefundService.java:60`, `:114`, `:124`

**문제**: `requestRefund()`, `getById()`, `getByPaymentId()` 세 메서드 모두 `userRepository.findByUsername(username)`으로 User 엔티티 전체를 로드한다. 목적은 `user.getId()`(Long) 하나뿐이며, JWT 클레임에서 추출한 userId를 파라미터로 받으면 DB 조회 없이 처리 가능하다. 이미 다른 서비스(#29, #49, #69, #71)에서 적용된 동일 패턴의 미적용 케이스.

**개선**: 시그니처를 `(String username)` → `(Long userId)` 로 변경. 컨트롤러에서 `resolveUserId()` 헬퍼로 JWT claim 추출.

---

### 79. Outbox `SHIPMENT_CREATE` — nested `REQUIRES_NEW` 실패 시 false dead letter 누적

**위치**: `common/outbox/OutboxEventProcessor.java`, `domain/shipment/service/ShipmentService.java` `createForOrder()`

**문제**: #77과 동일한 nested REQUIRES_NEW 패턴. `createForOrder()`가 `REQUIRES_NEW`로 배송 레코드를 독립 커밋한 뒤 `processOne` TX가 롤백되면, 다음 relay 사이클에서 `createForOrder()` 재시도 → `ShipmentAlreadyExists` 예외 → `outbox.recordFailure()`. 이를 5회 반복하면 MAX_RETRY 도달 → **dead letter** 로 분류된다. 실제 배송은 이미 올바르게 생성됐음에도 운영 알림(`outbox.dead_letters` Gauge 증가)이 오발된다.

**개선**: `createForOrder()` 내부에서 `shipmentRepository.existsByOrderId(orderId)` 선행 체크 → 이미 존재하면 early return (idempotent). 또는 `order_id UNIQUE` 제약을 DB에 추가하고, `DataIntegrityViolationException`을 catch 후 정상 처리.

---

### 76. `PaymentTransactionHelper.loadAndValidateForCancel()` — 소유권 체크에 전체 Order 엔티티 로드

**위치**: `domain/payment/service/PaymentTransactionHelper.java:273`

**문제**: USER 소유권 검증 시 `orderRepository.findById(payment.getOrderId())`로 Order 엔티티 전체를 로드한다. `order.getUserId()` 스칼라 값만 필요하며, `findUserIdById()` 스칼라 프로젝션이 이미 존재한다. #69(PaymentService.getByOrderId)와 동일 패턴.

**개선**: `orderRepository.findUserIdById(payment.getOrderId())` 스칼라 쿼리로 교체.

---

### 71. `OrderService.getHistory()` — 소유권 체크에 전체 Order 엔티티 로드

**위치**: `domain/order/service/OrderService.java:291`

**문제**: `historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)` 호출 전 소유권 검증을 위해 `orderRepository.findById(orderId)`로 전체 Order 엔티티를 로드한다. items 컬렉션은 이 흐름에서 사용하지 않으며 LAZY 이므로 로딩되진 않지만, 엔티티 전체를 로드할 이유도 없다.

**개선**: `findUserIdById()` 스칼라 프로젝션으로 교체. 이미 #49, #69와 동일 패턴의 미적용 케이스.

---

### 25. API 버전 관리 부재

**위치**: 모든 컨트롤러 (`/api/products`, `/api/orders` 등)

**문제**: 버전 prefix 없이 `/api/` 직접 사용. 응답 스키마 변경 시 하위 호환성 관리 불가.

**개선**: `/api/v1/` prefix 도입.

---

### 95. `GlobalExceptionHandler` — `HttpMessageNotReadableException` 등 4xx 에러가 500으로 응답

**위치**: `common/exception/GlobalExceptionHandler.java`

**문제**: JSON 파싱 실패(`HttpMessageNotReadableException`), 쿼리 파라미터 타입 불일치(`MethodArgumentTypeMismatchException`), 필수 파라미터 누락(`MissingServletRequestParameterException`) 등이 전용 핸들러 없이 최하단 `Exception` 핸들러로 빠져 500 응답. 에러 모니터링 노이즈.

**개선**: 각각에 대해 `@ExceptionHandler` 추가, 400 Bad Request로 응답.

---

### 96. `SecurityConfig` — Swagger UI 운영 환경 무인증 노출

**위치**: `common/config/SecurityConfig.java:81`

**문제**: `/swagger-ui/**`, `/v3/api-docs/**`가 `permitAll()`. 프로파일 분기 없이 운영에서도 전체 API 스키마 공개.

**개선**: `springdoc.api-docs.enabled=${SWAGGER_ENABLED:false}` 환경 변수 전환. 또는 ADMIN 권한 필요하도록 변경.

---

### 98. `RefreshTokenStore` 역색인 Set TTL 없음 — Redis 메모리 누수

**위치**: `common/security/RefreshTokenStore.java:45`

**문제**: `refresh:user:{username}` Set은 `issue()` 시 TTL 미설정. 개별 토큰 키(`refresh:token:{uuid}`)는 30일 TTL로 만료되지만, Set 내 UUID 문자열은 만료 후에도 잔존. 장기 활성 사용자의 역색인 크기가 무한 증가.

**개선**: `issue()` 시 Set에 35일 TTL 설정. 또는 주기적 정리 스케줄러 추가.

---

### 99. `AsyncConfig` `RejectedExecutionHandler` 미설정 — 이벤트 무음 소실

**위치**: `common/config/AsyncConfig.java:27-36`

**문제**: `maxPoolSize=8`, `queueCapacity=50`. 동시에 58개 이상 비동기 이벤트 발생 시 기본 `AbortPolicy`에 의해 `RejectedExecutionException` → 이벤트 소실. `@Async void` 반환이므로 호출자에게 전파 안 됨.

**개선**: `CallerRunsPolicy`로 거부된 작업을 호출 스레드에서 실행. 또는 `AsyncUncaughtExceptionHandler` 커스텀 구현.

---

### 100. `RequestIdFilter` 외부 주입 `X-Request-Id` 무검증 — 로그 인젝션

**위치**: `common/filter/RequestIdFilter.java:32-35`

**문제**: 클라이언트가 보낸 `X-Request-Id`를 길이/형식 검증 없이 사용. 줄바꿈(`\n`, `\r`) 포함 시 가짜 로그 라인 삽입 가능. 수 KB 길이 값으로 로그 부풀리기.

**개선**: 길이 상한(64자) + 영숫자/하이픈 패턴 검증. 초과 시 무시하고 UUID 생성.

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

**문제**: JWT details에서 userId 추출하는 동일한 4줄 메서드가 6곳에 copy-paste. 변경 시 모든 곳 수정 필요.

**개선**: `SecurityUtils`에 유틸리티 메서드 추출. 또는 `HandlerMethodArgumentResolver`로 `@CurrentUserId Long userId` 어노테이션 자동 주입.

---

### 105. 비밀번호 정책 부재 — 복잡도 요구사항 없음

**위치**: `domain/user/dto/SignupRequest.java:14-15`, `ChangePasswordRequest.java:17`

**문제**: `@Size(min=8)` 뿐. `aaaaaaaa` 같은 단순 비밀번호 허용. credential stuffing/사전 공격에 취약.

**개선**: 대문자/소문자/숫자/특수문자 중 최소 2~3종 포함 `@Pattern` 또는 커스텀 `ConstraintValidator`.

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

**문제**: 서비스에서 `reserved < quantity` 시 예외를 던지지만, 엔티티의 `releaseReservation()`은 `Math.max(0, reserved - quantity)`로 음수를 조용히 0으로 클램핑. 서비스 검증으로 엔티티 로직은 dead code이나, 다른 호출 경로 추가 시 데이터 불일치를 조용히 삼킬 수 있다.

**개선**: 엔티티 메서드가 자체적으로 `quantity > reserved` 시 예외를 던지도록 통일.

---

### 110. `TossPaymentsConfig` — `secretKey` null 시 `"null:"` 인코딩

**위치**: `common/config/TossPaymentsConfig.java:49-50`

**문제**: `toss.secret-key` 프로퍼티 누락 시 `(secretKey + ":").getBytes()`가 `"null:"` 로 인코딩. NPE는 아니지만 잘못된 인증 헤더로 Toss API 401 실패. 원인 파악 어려움.

**개선**: `@PostConstruct`에서 null/빈 문자열 검증 후 `IllegalStateException` throw. JWT, Admin과 동일 패턴.

---

### 111. `RefundRequest.reason` 길이 검증 없음

**위치**: `domain/refund/dto/RefundRequest.java:15`

**문제**: `@NotBlank`만 있고 `@Size(max=300)` 없음. `Refund.reason` 컬럼은 `length=300`이므로 초과 입력 시 DB truncation 에러.

**개선**: `@Size(max = 300)` 추가.

---

### 112. `RedisConfig` — `RedissonClient` `destroyMethod` 미설정

**위치**: `common/config/RedisConfig.java:26-31`

**문제**: `RedissonClient`는 `Closeable` 미구현이므로 Spring 자동 destroy 추론 불가. Graceful shutdown 시 Netty EventLoopGroup과 Redis 연결이 정리되지 않고 강제 종료.

**개선**: `@Bean(destroyMethod = "shutdown")` 명시.

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
