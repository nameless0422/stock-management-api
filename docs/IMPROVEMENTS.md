# 개선 작업 내역

> Spring Boot 3.5.11 + Java 17 기반 쇼핑몰 재고 관리 API에서 발견·해결한 문제 목록.
> 각 항목은 **문제 인식 → 해결 방법 → 결과** 순으로 기술한다.

---

## 목차

1. [재고 동시성 제어](#1-재고-동시성-제어)
2. [결제 트랜잭션 정합성](#2-결제-트랜잭션-정합성)
3. [환불 재시도 불가 버그](#3-환불-재시도-불가-버그)
4. [N+1 쿼리 및 배치 처리 최적화](#4-n1-쿼리-및-배치-처리-최적화)
5. [Redis Rate Limit 원자성 결함](#5-redis-rate-limit-원자성-결함)
6. [보안 취약점](#6-보안-취약점)
7. [이벤트 유실 — Transactional Outbox](#7-이벤트-유실--transactional-outbox)
8. [결제 취소 이중 처리 경쟁 조건](#8-결제-취소-이중-처리-경쟁-조건)
9. [결제 멱등성 부재](#9-결제-멱등성-부재)
10. [쿠폰 발급 모델 부재](#10-쿠폰-발급-모델-부재)
11. [시니어 코드 리뷰 개선 (9개 항목)](#11-시니어-코드-리뷰-개선-9개-항목)

---

## 1. 재고 동시성 제어

### 문제 인식

쇼핑몰 특성상 인기 상품에는 동시에 수백 건의 주문 요청이 발생한다.
단순 조회→차감 패턴은 다음 시나리오에서 무너진다.

```
재고: 1개 남음

Thread A: SELECT available = 1  → 재고 있음, 진행
Thread B: SELECT available = 1  → 재고 있음, 진행
Thread A: UPDATE reserved += 1  → available = 0
Thread B: UPDATE reserved += 1  → available = -1  ← 초과 예약 발생
```

`@Transactional`만으로는 부족하다. REPEATABLE READ 격리 수준에서는 각 트랜잭션이 시작 시점의 스냅샷을 읽기 때문에 Thread B도 여전히 `available = 1`을 본다. SERIALIZABLE은 데드락 위험과 처리량 저하가 크다.

멀티 인스턴스(수평 확장) 환경이라면 JVM 수준의 `synchronized`는 아무 의미가 없다. 인스턴스 A와 인스턴스 B가 각자의 메모리에서 동시 처리하기 때문이다.

### 해결 방법

**2중 락 전략** 적용:

| 레이어 | 기술 | 역할 |
|--------|------|------|
| 분산 락 | Redisson `@DistributedLock` | 멀티 인스턴스 환경에서 동일 상품 요청 직렬화 |
| DB 락 | `@Lock(PESSIMISTIC_WRITE)` | DB 레벨 보조, 분산 락 누락 경로 차단 |

재고 상태를 3가지로 분리해 의미를 명확히 한다.

```
available = onHand - reserved - allocated

주문 생성  → reserved++
결제 완료  → reserved--, allocated++
주문 취소  → reserved--
결제 취소  → allocated--
```

### 결과

- 동시 100건 주문 시나리오(k6)에서 재고 초과 예약 0건
- 분산 락 획득 실패 시 `LOCK_ACQUISITION_FAILED` 즉시 반환 (무한 대기 없음)

---

## 2. 결제 트랜잭션 정합성

### 문제 인식

결제 확정 후에는 배송 생성, 포인트 적립 같은 부수 효과가 연쇄 실행된다.
초기 구조는 이 모든 로직이 하나의 트랜잭션 안에 묶여 있었다.

```java
// 기존 구조 (문제)
@Transactional
public void applyConfirmResult(Payment payment) {
    try {
        shipmentService.createForOrder(orderId);   // REQUIRED — 같은 트랜잭션 참여
        pointService.earn(userId, amount);          // REQUIRED — 같은 트랜잭션 참여
    } catch (Exception e) {
        log.warn("부수 효과 실패", e);  // 무시하려 했으나...
    }
}
```

`createForOrder()`가 예외를 던지면 Spring이 공유 트랜잭션을 **rollback-only**로 표시한다.
catch로 잡아도 이미 늦었다. 바깥 트랜잭션이 commit을 시도하는 순간 `UnexpectedRollbackException`이 터진다.

결과:
- **Toss Payments**: 결제 완료 처리됨 (돈 빠져나감)
- **DB**: `payment.status = PENDING` 유지 (rollback됨)
- 사용자는 돈을 냈는데 주문이 처리되지 않은 상태 → 재시도해도 idempotency key로 차단 → **결제 유실**

### 해결 방법

- `ShipmentService.createForOrder()` + `PointService.earn()` → `Propagation.REQUIRES_NEW`로 분리
  - 각 부수 효과가 독립 트랜잭션으로 실행되어 실패해도 결제 트랜잭션에 영향 없음
- `PointService.use()` / `refundByOrder()`는 주문과 원자성이 필요하므로 `REQUIRED` 유지

### 결과

- 배송 생성 실패해도 결제 확정은 커밋됨 (수동 보상 처리 가능)
- 결제 유실 버그 완전 제거

---

## 3. 환불 재시도 불가 버그

### 문제 인식

환불 흐름:

1. 사용자가 환불 요청
2. `Refund` 레코드 생성 (PENDING)
3. PG사에 환불 API 호출
4. PG사 일시 장애로 예외 발생 → `Refund.status = FAILED`로 저장

여기까지는 정상이다. 문제는 재시도 시다.

```java
// 기존 코드
if (refundRepository.existsByPaymentId(paymentId)) {
    throw new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS);
}
```

`existsByPaymentId()`는 FAILED 상태인 레코드도 "존재함"으로 판단한다.
PG사가 복구되어 사용자가 다시 환불을 요청해도 `REFUND_ALREADY_EXISTS`로 차단된다.

`Refund` 레코드가 FAILED 상태로 남아 있는 한 **영구적으로 환불 불가** 상태가 된다.
운영자가 DB를 직접 수정하지 않으면 해결이 불가능하다.

### 해결 방법

- `findByPaymentId()`로 교체: 기존 레코드의 상태 분기 처리
  - FAILED → `refund.reset(reason)`으로 기존 레코드 재사용 후 재시도
  - FAILED 아님(PENDING/COMPLETED) → `REFUND_ALREADY_EXISTS` 반환
- `noRollbackFor = Exception.class` 추가 — catch-rethrow 패턴에서 Dirty Checking 보장

### 결과

- 환불 재시도 정상 동작
- PG사 일시 장애 후 복구 시 자동 재처리 가능

---

## 4. N+1 쿼리 및 배치 처리 최적화

### 문제 인식

여러 곳에서 N+1 패턴이 발생하고 있었다.

**`getMyCoupons()` — 2중 N+1**

```
SELECT * FROM user_coupons WHERE user_id = ?                             → 1번
SELECT * FROM coupons WHERE id = ?                                       → N번 (Lazy)
SELECT COUNT(*) FROM coupon_usages WHERE coupon_id = ? AND user_id = ?  → N번
합계: 2N + 1번  →  쿠폰 50개 보유 시 쿼리 101번 발생
```

**`WishlistService`**

위시리스트 아이템 목록을 불러올 때 `product`가 Lazy → 아이템 N개 × SELECT 1번씩 추가.

**`InventorySnapshotScheduler`**

```java
// 기존: 재고 1000건이면 1000번 INSERT
inventories.forEach(inv -> snapshotRepository.save(new DailyInventorySnapshot(...)));
```

자정 스케줄러 실행 시 DB connection을 길게 점유하고 슬로우 쿼리 알람 발생 가능.

**`ReviewRepository`**

```java
// 기존: 상품별 2번의 별도 집계 쿼리
double avgRating  = reviewRepository.avgRatingByProductId(productId);
long   reviewCount = reviewRepository.countByProductId(productId);
```

상품 목록 페이지 20개 기준 집계 쿼리 40번 발생.

**`@ManyToOne` Lazy Loading 전반**

`OrderItem → Product`, `CartItem → Product` 등 연관 엔티티를 개별 접근할 때마다 SELECT 1건씩 추가.
기본 설정에서는 배치 없이 1건씩만 로딩.

### 해결 방법

| 위치 | 변경 전 | 변경 후 |
|------|---------|---------|
| `UserCouponRepository` | Lazy join | `@EntityGraph(coupon)` fetch join |
| `CouponUsageRepository` | N번 count | `countByCouponIdsAndUserId()` IN 절 배치 |
| `WishlistService` | 개별 조회 | `findAllById()` 단일 쿼리 |
| `InventorySnapshotScheduler` | 개별 `save()` N번 | `saveAll()` 배치 INSERT |
| `PointService.refundByOrder()` | 개별 `save()` N번 | `saveAll()` 배치 INSERT |
| `ReviewRepository` | 집계 쿼리 2번 | avgRating + reviewCount 통합 단일 JPQL |
| 전체 `@ManyToOne` | 1건씩 Lazy | `hibernate.default_batch_fetch_size=50` |
| `inventory_transactions` | `inventory_id` 단일 인덱스 | `(inventory_id, created_at)` 복합 인덱스 |

> **주의**: `@BatchSize`는 `@OneToMany`/`@ManyToMany` 컬렉션에만 적용 가능.
> `@ManyToOne`에 붙이면 `AnnotationException` 발생 → `default_batch_fetch_size` 사용.

### 결과

- `getMyCoupons()`: 쿠폰 N건 조회 시 쿼리 수 `2N+1 → 3` (고정)
- `InventorySnapshotScheduler`: 재고 1000건 기준 INSERT `1000 → 1`
- 상품 목록 `@ManyToOne` 배치 로딩으로 개별 SELECT 대폭 감소
- `inventory_transactions` 이력 조회 filesort 제거

---

## 5. Redis Rate Limit 원자성 결함

### 문제 인식

로그인 실패 횟수 제한을 Redis의 `RAtomicLong`으로 구현했으나 두 연산이 비원자적으로 실행됐다.

```java
// 기존 코드
long count = atomicLong.incrementAndGet();  // 연산 1
if (count == 1) {
    atomicLong.expire(15, TimeUnit.MINUTES); // 연산 2 — 크래시 시 실행 안 됨
}
```

두 연산 사이에 애플리케이션이 크래시(OOM, 강제 종료)되면 연산 1만 실행된다.
그 결과 TTL 없는 키가 Redis에 남아 **해당 사용자 계정이 영구 잠금**된다.

### 해결 방법

INCR + EXPIRE를 **Lua 스크립트로 원자화**하여 단일 연산으로 처리한다.

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
```

추가로 `trust-proxy` 프로퍼티 도입: `false` 설정 시 `X-Forwarded-For` / `X-Real-IP` 헤더 무시 → IP 스푸핑 방어.

### 결과

- 앱 크래시 시 영구 잠금 버그 완전 제거
- `trust-proxy=false` 환경에서 IP 조작 불가

---

## 6. 보안 취약점

### 문제 인식

**IDOR (Insecure Direct Object Reference)**

```http
DELETE /api/orders/999/cancel
Authorization: Bearer <사용자 A의 토큰>
```

`orderId=999`가 사용자 B의 주문이어도 취소가 가능했다.
서비스 레이어에서 소유권 검증 없이 `orderId`만으로 처리했기 때문이다.
배송 조회, 결제 조회에서도 동일한 문제가 있었다.

**가격 조작**

```json
POST /api/orders
{ "items": [{"productId": 1, "quantity": 1, "unitPrice": 1}] }
```

`unitPrice`를 클라이언트가 임의로 1원으로 보내면 그대로 주문이 생성됐다.
DB의 실제 가격을 검증하지 않았기 때문이다.

**포인트 음수 기록**

환불 시 사용된 포인트를 복원하는 로직에서 잔액보다 많은 금액을 환불하려 하면 음수 잔액이 기록됐다.
예: 포인트 잔액 300원인데 500원 환불 → 잔액 -200원.

**XSS in 이메일**

배송 완료 이메일 본문에 배송사명, 운송장 번호, 상품명을 이스케이프 없이 HTML에 삽입했다.
공격자가 상품명을 `<script>alert(1)</script>`로 등록하면 이메일 클라이언트에서 스크립트가 실행될 수 있다.

**JWT userId DB round-trip**

모든 인증 요청마다 `username → userId` 변환을 위해 DB SELECT가 1회씩 발생했다.
초당 1,000 요청 환경에서는 이것만으로 초당 1,000번의 불필요한 `users` 테이블 조회가 발생한다.

**Refresh Token 재사용**

Rotation이 없어 탈취된 Refresh Token을 무한히 재사용할 수 있었다.

**TossPayments Webhook 위조**

Webhook 수신 시 서명 검증이 없어 외부 공격자가 임의 결제 완료 이벤트를 위조해 보낼 수 있었다.

### 해결 방법

| 취약점 | 해결 |
|--------|------|
| IDOR | `validateOwnership()` 소유권 검증 4개 엔드포인트 추가 |
| 가격 조작 | `OrderService.create()`에서 DB canonical price 강제 적용, 클라이언트 unitPrice 무시 |
| 포인트 음수 | `actualReclaim = Math.min(balance, reclaimAmount)` |
| XSS | `HtmlUtils.htmlEscape()` on carrier / trackingNumber / productName |
| JWT round-trip | JWT에 `userId` 클레임 포함 → SecurityContext details에서 추출 (DB fallback 유지) |
| Refresh Token | Rotation 적용: `consume()` = getAndDelete() → 1회용 |
| Webhook 위조 | HMAC-SHA256 서명 검증 추가 |

### 결과

- OWASP 기준 주요 취약점 제거
- 가격 조작 주문 완전 차단
- JWT userId 조회 DB 쿼리 제거 (매 요청마다 아끼는 SELECT 1건)

---

## 7. 이벤트 유실 — Transactional Outbox

### 문제 인식

`ApplicationEventPublisher`로 도메인 이벤트를 발행하는 구조의 치명적 약점이 있다.

```
1. 주문 생성 트랜잭션 커밋
2. ApplicationEventPublisher.publishEvent(OrderCreatedEvent) 발행   ← 여기서 크래시
3. 이메일 발송 / 재고 알림 처리
```

커밋과 이벤트 발행 사이에 JVM 크래시가 발생하거나 이벤트 리스너가 예외를 던지면 이벤트는 흔적 없이 사라진다.
재처리할 방법이 없고, 어떤 이벤트가 유실됐는지 추적도 불가능하다.

`@TransactionalEventListener(AFTER_COMMIT)`을 사용해도 AFTER_COMMIT 단계에서 실패한 이벤트는 재시도나 보상 트랜잭션이 불가능하다는 문제는 동일하다.

### 해결 방법

**Transactional Outbox 패턴** 적용.

```
비즈니스 로직 트랜잭션 안에서
OutboxEventStore.save(event)  ← 비즈니스 데이터와 동일 트랜잭션
→ 커밋되면 이벤트도 반드시 DB에 저장됨

OutboxEventRelayScheduler (5초 폴링)
→ PENDING 이벤트 조회 → 발행 → PROCESSED 표시

OutboxEventPurgeScheduler (매일 새벽 3시)
→ 7일 이상 된 처리 완료 이벤트 정리
```

- `OutboxEventStore`: `Propagation.MANDATORY` — 트랜잭션 없이 호출하면 즉시 예외
- Prometheus 게이지 `outbox.dead_letters`: 재시도 한계를 넘긴 이벤트 수 모니터링

### 결과

- 이벤트 유실 0 (DB에 저장된 이벤트는 반드시 발행됨)
- Dead letter 알람으로 처리 실패 이벤트 즉시 감지 가능

---

## 8. 결제 취소 이중 처리 경쟁 조건

### 문제 인식

```
결제 status = CONFIRMED

Thread 1: SELECT payment WHERE key=X → CONFIRMED (취소 가능 판단)
Thread 2: SELECT payment WHERE key=X → CONFIRMED (취소 가능 판단)
Thread 1: PG사 취소 API 호출 → 성공 → status = CANCELLED
Thread 2: PG사 취소 API 호출 → ??? (이미 취소된 결제에 재요청)
Thread 2: UPDATE status = CANCELLED (중복 기록)
```

사용자가 취소 버튼을 빠르게 두 번 클릭하거나 네트워크 재시도로 동일 요청이 두 번 오면 발생할 수 있다.
결제는 1번 취소됐는데 환불이 2번 처리되면 **환불 금액 이중 지급**이 된다.

### 해결 방법

- `findByPaymentKeyWithLock()` 비관적 락으로 결제 행을 잠금
- `PaymentTransactionHelper.loadAndValidateForCancel()`: `readOnly=true` → `@Transactional` + locked query로 변경

### 결과

- 취소 요청이 동시에 들어와도 먼저 락을 획득한 스레드만 처리
- 이중 환불 경쟁 조건 제거

---

## 9. 결제 멱등성 부재

### 문제 인식

네트워크 타임아웃이나 클라이언트 재시도로 동일한 결제 확정 요청이 두 번 도달하면:
- 동일 `orderId`로 두 번의 결제가 생성될 수 있었다
- Toss Payments에도 중복 API 호출이 발생해 예상치 못한 이중 청구 위험이 있었다

### 해결 방법

- `PaymentIdempotencyManager`: Redis SETNX — 동일 orderId 최초 요청만 통과, 이후 요청 즉시 차단
- `TossPaymentsClient`: `Idempotency-Key` 헤더 추가 — PG사 레벨에서도 중복 차단
- `payments.order_id UNIQUE` 제약 (V8 migration) — DB 레벨 최후 방어선

### 결과

- 네트워크 재시도로 인한 이중 결제 완전 차단
- 3중 방어 (Redis → PG사 헤더 → DB 제약)

---

## 10. 쿠폰 발급 모델 부재

### 문제 인식

초기 쿠폰 구현은 **쿠폰 코드를 아는 누구나** 주문 시 적용할 수 있는 구조였다.
두 가지 비즈니스 요구사항을 충족하지 못했다.

**발급 전용 쿠폰 불가**

"VIP 고객에게만 발급하는 쿠폰"을 만들어도, 코드를 아는 일반 사용자가 그냥 입력해서 사용할 수 있었다.
선물 쿠폰, 사과 쿠폰 등 특정 대상에게만 지급하는 쿠폰을 구현할 방법이 없었다.

**내 쿠폰 지갑 없음**

`GET /api/coupons/my`가 존재하지 않았다.
사용자는 "내가 어떤 쿠폰을 갖고 있는지" 볼 수 없었고, 주문 화면에서 쿠폰 코드를 외워서 직접 입력해야 했다.
쿠폰 만료일이나 사용 가능 여부도 사전에 확인할 방법이 없었다.

`coupon_usages` 테이블은 이미 있었지만 이것은 **사용 이력** 추적용이지 **발급/보유** 모델이 아니었다.

### 해결 방법

`isPublic` 플래그로 쿠폰 유형을 구분한다.

| 유형 | `isPublic` | 사용 조건 |
|------|------------|-----------|
| 공개 쿠폰 (프로모 코드) | `true` | 코드를 아는 누구나 `POST /claim`으로 등록 후 사용 |
| 발급 전용 쿠폰 | `false` | Admin이 `POST /{id}/issue`로 특정 사용자에게 발급한 경우만 사용 |

- `POST /api/coupons/claim` — 공개 쿠폰 코드 입력 시 `user_coupons`에 등록
- `GET /api/coupons/my` — 내 지갑의 쿠폰 목록 + `isUsable` 상태 반환
- `applyCoupon()` — 비공개 쿠폰은 `user_coupons` 등록 여부 검증 추가

### 결과

- 발급 전용 쿠폰 미등록 사용자 차단 (`COUPON_NOT_ISSUED` 403)
- 사용자가 보유 쿠폰 목록과 사용 가능 여부를 사전에 확인 가능

---

## 11. 시니어 코드 리뷰 개선 (9개 항목)

### 문제 인식 및 해결

| # | 문제 인식 | 해결 방법 |
|---|-----------|-----------|
| 1 | 주문 생성 시 클라이언트 전달 `unitPrice`를 그대로 신뢰 → 1원 주문 가능 | `OrderService.create()`에서 DB canonical price 강제 적용 |
| 2 | `JwtTokenProvider` / `JwtAuthenticationFilter`가 `security/` 패키지에 고립 → 다른 모듈에서 접근 어려움 | `common.security`로 이동, 패키지 일관성 확보 |
| 3 | 비즈니스 규칙 위반 에러가 `409 CONFLICT`로 반환 → 의미 오용 (Conflict는 리소스 충돌) | `422 UNPROCESSABLE_ENTITY`로 변경 |
| 4 | `OrderService` 여러 메서드가 `SecurityContextHolder`를 직접 접근 → 테스트 어렵고 결합도 높음 | `userId Long` 파라미터로 교체, SecurityContext 의존성 제거 |
| 5 | `OutboxEventProcessor`에서 모든 이벤트를 하나의 트랜잭션으로 처리 → 한 이벤트 실패 시 전체 롤백 | 이벤트별 `REQUIRES_NEW` 독립 트랜잭션으로 분리 |
| 6 | `OrderService`가 `PaymentRepository`, `ShipmentRepository`, `ReviewRepository` 직접 의존 → God Service | `OrderDetailService` 분리 |
| 7 | 서비스 레이어가 `OutboxEventStore` 구현체에 직접 의존 → 테스트/변경 어려움 | `OutboxSupport` 인터페이스 도입 |
| 8 | 첫 주문 시 `UserPoint` 레코드 생성 → 동시 첫 주문 시 UK 충돌 | `UserService.signup()` 시점에 `UserPoint` 선생성 |
| 9 | `JWT_SECRET` 등 민감 설정이 `application.properties`에 기본값으로 하드코딩 | `application-prod.properties` + 환경변수 필수화 |

---

## 테스트 커버리지 추이

| 시점 | 테스트 수 |
|------|-----------|
| 기본 도메인 완성 (Product / Inventory / Order / Payment / User) | ~100개 |
| 리뷰 / 위시리스트 / 포인트 / 환불 도메인 + 통합 테스트 | 465개 |
| 전 컨트롤러 단위 테스트 완비 | 529개 |
| 배치 / Elasticsearch / 쿠폰 / 카테고리 추가 | ~580개 |
| 시니어 코드 리뷰 개선 + 쿠폰 발급 모델 | **601개** |
