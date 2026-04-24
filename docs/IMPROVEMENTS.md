# 개선 작업 내역

> Spring Boot 3.5.11 + Java 17 기반 쇼핑몰 재고 관리 API에서 발견·해결한 기술적 문제 목록.
> 각 항목은 **배경 및 문제정의 → 솔루션 → 기능 구현 → Trade-off → 결과** 순으로 기술한다.

---

## 서비스 운영 규모 가정

아래 개선 항목들이 실제로 문제가 될 수 있는 서비스 규모를 기준으로 설명한다.
각 수치는 문서 내 예시("초당 1,000 요청", "동시 100건 주문", "멀티 인스턴스")와 일관되도록 설정했다.

### 사용자 / 트래픽

| 지표 | 수치 |
|------|------|
| 일평균 활성 사용자 (DAU) | 50,000명 |
| 월간 활성 사용자 (MAU) | 200,000명 |
| 평시 TPS | 100~200 req/s |
| 피크 TPS | **1,000 req/s** |
| 피크 시간대 동시 주문 | **100건/분** |
| 일 주문 건수 | 5,000건 |

피크 트래픽은 타임 세일·특가 행사 시 발생하며 평시 대비 5~10배 수준으로 가정한다.

### 서버 스펙

각 역할은 전용 인스턴스에서 독립적으로 운영된다.

| 역할 | AWS 서비스 | 사양 | 인스턴스 수 |
|------|-----------|------|------------|
| Load Balancer | ALB | — | 1 (managed) |
| API Server (Spring Boot) | EC2 | 4 vCPU / 8GB RAM | **2** (독립 JVM) |
| DB (MySQL 8) | RDS | 4 vCPU / 16GB RAM / SSD 500GB | **2** (Primary + Replica) |
| Redis | ElastiCache | 2 vCPU / 4GB RAM | **1** |
| Elasticsearch | EC2 | 2 vCPU / 4GB RAM | **3** (cluster) |
| **합계** | | | **9** |

```
                  ┌──────────────────── AWS VPC ──────────────────────────────┐
                  │                                                            │
Internet ─→ ALB ─┤─→ ┌─ EC2 ─────────────────┐                              │
                  │   │  API Server 1          │                              │
                  │   │  JVM Process / :8080   ├──┐                           │
                  │   │  (독립 메모리 공간)     │  │                           │
                  │   └────────────────────────┘  │  ← 두 JVM은              │
                  │                               │    메모리 공유 없음       │
                  ├─→ ┌─ EC2 ─────────────────┐  │                           │
                  │   │  API Server 2          │  │                           │
                  │   │  JVM Process / :8080   ├──┤                           │
                  │   │  (독립 메모리 공간)     │  │                           │
                  │   └────────────────────────┘  │                           │
                  │                               │ (공유 외부 저장소)         │
                  │          ┌────────────────────┼──────────────┐            │
                  │          ▼                    ▼              ▼            │
                  │  ┌──────────────────┐  ┌──────────────┐  ┌───────────┐   │
                  │  │   RDS MySQL 8    │  │ ElastiCache  │  │ EC2 × 3   │   │
                  │  │  Primary (write) │  │   Redis      │  │ ES cluster│   │
                  │  │  Replica  (read) │  │  분산 락     │  │ 상품 검색 │   │
                  │  └──────────────────┘  └──────────────┘  └───────────┘   │
                  └────────────────────────────────────────────────────────────┘
```

> 포트폴리오 PDF 제출 시에는 이 다이어그램을 [draw.io](https://app.diagrams.net) 등으로 시각화해 이미지로 첨부하면 가독성이 높아진다.

ALB가 요청을 두 인스턴스에 분산하므로 동일 상품 주문이 Server 1·2에 동시 도달할 수 있다. 두 JVM은 메모리가 완전히 분리되어 있어 `synchronized`로는 인스턴스 간 동시성을 제어할 수 없다. Redis(ElastiCache)는 두 인스턴스가 함께 바라보는 **공유 외부 저장소**이기 때문에 분산 락 역할이 가능하다.

### 이 규모가 각 개선 항목과 연결되는 이유

| 규모 조건 | 연결 항목 | 이유 |
|-----------|-----------|------|
| API Server 2대 이상 | [1. 재고 동시성](#1-재고-동시성-제어) | JVM `synchronized`로는 인스턴스 간 동시성 제어 불가 → 분산 락 필요 |
| 피크 TPS 1,000 | [8. JWT round-trip](#8-보안-취약점) | 인증마다 DB SELECT → 초당 1,000번 불필요한 쿼리 |
| 피크 TPS 1,000 | [9. CategoryService 캐시](#9-categoryservice-redis-캐시) | 캐시 미적용 시 상품 목록 API 호출마다 `categories` SELECT → 초당 1,000번 불필요한 쿼리 |
| 상품 페이지 조회 (20건) | [7. N+1 최적화](#7-n1-쿼리-및-배치-처리-최적화) | `InventoryRepository` `@EntityGraph` 누락 → product SELECT 20회 추가 |
| 자정 배치 (재고 10,000건) | [7. N+1 최적화](#7-n1-쿼리-및-배치-처리-최적화) | 전체 재고 단일 트랜잭션 로드 → 힙 과부하 + 테이블 락 장시간 점유 |
| 동시 주문 100건/분 | [1. 재고 동시성](#1-재고-동시성-제어), [2. TOCTOU](#2-결제-취소-이중-처리-경쟁-조건) | 재고 1개 남은 상품에 수십 건 동시 진입 → 초과 예약·이중 처리 현실화 |
| 일 주문 5,000건 | [5. 결제 멱등성](#5-결제-멱등성) | 재시도율 0.1%만 가정해도 하루 5건의 중복 결제 위험 |
| Redis 단일 인스턴스 | [3. Lua 원자성](#3-redis-rate-limit-원자성-결함), [5. 결제 멱등성](#5-결제-멱등성) | Redis 장애 시 3중 방어의 1레이어 소실 → 나머지 레이어 역할 명확화 |

---

## 목차

1. [재고 동시성 제어](#1-재고-동시성-제어)
2. [결제 취소 이중 처리 경쟁 조건](#2-결제-취소-이중-처리-경쟁-조건)
3. [Redis Rate Limit 원자성 결함](#3-redis-rate-limit-원자성-결함)
4. [결제 트랜잭션 정합성](#4-결제-트랜잭션-정합성)
5. [결제 멱등성](#5-결제-멱등성)
6. [이벤트 유실 — Transactional Outbox](#6-이벤트-유실--transactional-outbox)
7. [N+1 쿼리 및 배치 처리 최적화](#7-n1-쿼리-및-배치-처리-최적화)
8. [보안 취약점](#8-보안-취약점)
9. [CategoryService Redis 캐시](#9-categoryservice-redis-캐시)

---

## 1. 재고 동시성 제어

### 배경 및 문제정의

* **상황:** 인기 상품에 동시에 수백 건의 주문 요청이 발생하는 쇼핑몰 특성
* **문제:** 단순 조회→차감 패턴은 동시 접근 시 재고 초과 예약(oversell)을 유발함

```
재고: 1개 남음

Thread A: SELECT available = 1  → 재고 있음, 진행
Thread B: SELECT available = 1  → 재고 있음, 진행
Thread A: UPDATE reserved += 1  → available = 0
Thread B: UPDATE reserved += 1  → available = -1  ← 초과 예약 발생
```

* `@Transactional`만으로는 부족함 — REPEATABLE READ 격리 수준에서 각 트랜잭션은 시작 시점의 스냅샷을 읽어 Thread B도 `available = 1`을 봄. SERIALIZABLE은 데드락 위험과 처리량 저하가 큼
* 멀티 인스턴스(수평 확장) 환경에서 JVM `synchronized`는 의미 없음 — 두 JVM은 메모리가 완전히 분리되어 있어 인스턴스 간 동시성을 제어할 수 없음

### 솔루션: 2중 락 전략

* 단일 레이어로는 멀티 인스턴스 환경과 DB 레벨 동시성을 동시에 제어할 수 없음
* 분산 락(인스턴스 간 직렬화)과 DB 비관적 락(분산 락 누락 경로 차단)을 조합하여 각 레이어가 독립적으로 방어

| 레이어 | 기술 | 역할 |
|--------|------|------|
| 분산 락 | Redisson `@DistributedLock` | 멀티 인스턴스 환경에서 동일 상품 요청 직렬화 |
| DB 락 | `@Lock(PESSIMISTIC_WRITE)` | DB 레벨 보조, 분산 락 누락 경로 차단 |

### 기능 구현

**1. 재고 상태 3분리**

주문·결제·취소 각 단계의 재고 의미를 명확히 구분한다.

```
available = onHand - reserved - allocated

주문 생성  → reserved++
결제 완료  → reserved--, allocated++
주문 취소  → reserved--
결제 취소  → allocated--
```

**2. 분산 락 + 비관적 락 적용**

```java
@DistributedLock(key = "'lock:inventory:' + #productId", waitTime = 3, leaseTime = 5)
@Transactional
public void reserve(Long productId, int quantity) {
    Inventory inv = inventoryRepository.findByProductIdWithLock(productId);
    inv.reserve(quantity);  // available < 0이면 InsufficientStockException
}
```

### Trade-off

비관적 락과 분산 락을 동시에 사용하면 처리량(Throughput)이 저하된다. 재고 차감·반환 핵심 경로에만 선별 적용하고, 단순 조회 API에는 락을 사용하지 않았다.

### 결과

* 동시 100건 주문 시나리오(k6)에서 재고 초과 예약 0건
* 분산 락 획득 실패 시 `LOCK_ACQUISITION_FAILED` 즉시 반환 (무한 대기 없음)

---

## 2. 결제 취소 이중 처리 경쟁 조건

### 배경 및 문제정의

* **상황:** 사용자가 취소 버튼을 빠르게 두 번 클릭하거나, 네트워크 재시도로 동일 요청이 두 번 도달하는 상황
* **문제:** TOCTOU(Time-of-Check to Time-of-Use) 취약점 — 상태 확인 시점과 변경 시점 사이에 다른 스레드가 끼어들 수 있음

```
결제 status = CONFIRMED

Thread 1: SELECT payment WHERE key=X → CONFIRMED (취소 가능 판단)
Thread 2: SELECT payment WHERE key=X → CONFIRMED (취소 가능 판단)
Thread 1: PG사 취소 API 호출 → 성공 → status = CANCELLED
Thread 2: PG사 취소 API 호출 → ??? (이미 취소된 결제에 재요청)
Thread 2: UPDATE status = CANCELLED  ← 중복 기록
```

* 프로덕션에서 발생 시 결제는 1번 취소됐는데 환불이 2번 지급되는 **금전적 손실**로 이어짐
* PG사 API 특성에 따라 중복 취소 자체가 에러 없이 성공하는 경우도 있어 감지가 어려움

### 솔루션: 조회-변경을 단일 락 범위로 통합

* 상태 확인과 변경을 별도의 연산으로 수행하면 두 연산 사이에 다른 스레드가 개입할 수 있음
* 조회 시점에 비관적 락으로 결제 행을 잠가 상태 확인과 변경이 하나의 락 범위 안에서 이루어지도록 함

### 기능 구현

**1. 비관적 락 조회 메서드 추가**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Payment> findByPaymentKeyWithLock(String paymentKey);
```

**2. 취소 검증 메서드 트랜잭션 변경**

`loadAndValidateForCancel()`을 `readOnly=true`에서 `@Transactional` + locked query로 변경하여 조회와 검증이 동일 트랜잭션 내에서 이루어지게 한다.

### Trade-off

비관적 락은 동일 결제 행에 대한 동시 접근을 직렬화하므로 취소 처리 중 다른 조회도 대기해야 한다. 취소 요청은 전체 결제 건수 대비 빈도가 낮아 실제 처리량 영향은 미미하다.

### 결과

* 취소 요청이 동시에 들어와도 먼저 락을 획득한 스레드만 PG사 API를 호출
* 이후 스레드는 DB에서 이미 `CANCELLED` 상태를 읽어 즉시 반환 — 환불 이중 지급 차단

---

## 3. Redis Rate Limit 원자성 결함

### 배경 및 문제정의

* **상황:** 로그인 실패 횟수 제한을 Redis `RAtomicLong`으로 구현
* **문제:** INCR + EXPIRE 두 연산이 비원자적으로 실행되어, 두 연산 사이에 크래시 발생 시 TTL 없는 키가 Redis에 남음

```java
// 기존 코드
long count = atomicLong.incrementAndGet();  // 연산 1
if (count == 1) {
    atomicLong.expire(15, TimeUnit.MINUTES); // 연산 2 — 크래시 시 실행 안 됨
}
```

* 앱 크래시(OOM, 강제 종료) 시 연산 1만 실행되어 **해당 계정이 영구 잠금** 상태가 됨 — 사용자 지원팀이 직접 Redis 키를 삭제하기 전까지 로그인 불가
* 멀티 인스턴스 환경에서 두 인스턴스가 거의 동시에 `incrementAndGet()`을 호출하면 둘 다 `count=1`로 읽어 TTL을 누락할 수도 있음

### 솔루션: Lua 스크립트로 원자화

* Redis는 단일 스레드로 Lua 스크립트를 실행하므로 두 명령 사이에 다른 명령이 끼어들 수 없음
* INCR + EXPIRE를 단일 Lua 스크립트로 합쳐 원자성을 보장

### 기능 구현

**1. INCR + EXPIRE Lua 스크립트 작성**

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
```

### Trade-off

Lua 스크립트 실행 중에는 Redis가 다른 명령을 처리하지 않는다. 이 스크립트는 2개 명령으로 구성되어 실행 시간이 수 μs에 불과해 실질적 영향은 없다. Redis Cluster 환경에서는 `KEYS[1]`이 단일 슬롯에 속해야 하는 제약이 있다.

### 결과

* 앱 크래시 시에도 TTL이 보장되어 영구 잠금 버그 제거
* 멀티 인스턴스 환경에서도 INCR과 EXPIRE가 항상 함께 실행

---

## 4. 결제 트랜잭션 정합성

### 배경 및 문제정의

* **상황:** 결제 확정 후 배송 생성, 포인트 적립 같은 부수 효과가 연쇄 실행되는 구조
* **문제:** 모든 로직이 단일 트랜잭션에 묶여 있어, 부수 효과 실패가 결제 자체를 롤백시킴

```java
// 기존 구조 (문제)
@Transactional
public void applyConfirmResult(Payment payment) {
    try {
        shipmentService.createForOrder(orderId);  // REQUIRED — 같은 트랜잭션 참여
        pointService.earn(userId, amount);         // REQUIRED — 같은 트랜잭션 참여
    } catch (Exception e) {
        log.warn("부수 효과 실패", e);  // 무시하려 했으나...
    }
}
```

* `createForOrder()`가 예외를 던지면 Spring이 공유 트랜잭션을 **rollback-only**로 표시함 — catch로 잡아도 이미 늦어 바깥 트랜잭션 commit 시 `UnexpectedRollbackException` 발생
* **Toss는 결제 완료(돈 빠져나감)인데 DB는 `payment.status = PENDING` 유지** — 사용자가 재시도해도 idempotency key로 차단 → 결제 유실 상태 지속

### 솔루션: 부수 효과를 독립 트랜잭션으로 분리

* 결제 확정과 배송·포인트 생성은 비즈니스적으로 독립적인 작업임
* 부수 효과는 `REQUIRES_NEW`로 별도 트랜잭션에서 처리하여 실패해도 결제 확정에 영향을 주지 않게 함

| 메서드 | 전파 레벨 | 이유 |
|--------|-----------|------|
| `ShipmentService.createForOrder()` | `REQUIRES_NEW` | 결제 트랜잭션과 독립 커밋·롤백 |
| `PointService.earn()` | `REQUIRES_NEW` | 동일 |
| `PointService.use()` / `refundByOrder()` | `REQUIRED` | 주문·취소와 원자성 보장 필요 |

### 기능 구현

**1. 부수 효과 REQUIRES_NEW 적용**

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void createForOrder(Long orderId) {
    // 결제 트랜잭션과 별개로 커밋/롤백 — 실패해도 결제 확정에 영향 없음
}
```

**2. 불일치 감지 및 보상 처리**

Dead Letter 알람(`outbox.dead_letters`)으로 실패 건을 감지하여 다음 두 가지 방식으로 보상한다.

* 운영 툴에서 `POST /api/admin/orders/{orderId}/retry-side-effects` 수동 재시도
* 매 시간 `payment.status = CONFIRMED AND shipment IS NULL` 조건으로 누락 건 자동 감지 후 재처리

### Trade-off

`REQUIRES_NEW`는 새 DB 커넥션을 획득하므로 커넥션 풀 사용량이 증가한다. 배송·포인트 실패 시 결제만 성공하는 불일치 상태가 발생하므로 운영 보상 처리 프로세스가 필요하다.

### 결과

* 배송 생성이 실패해도 결제 확정 커밋은 보장됨
* Dead Letter 알람 → 운영 툴 재시도 또는 배치 자동 보상으로 불일치 해소
* 사용자 관점에서 "돈은 나갔는데 주문이 없다"는 상황 차단

---

## 5. 결제 멱등성

### 배경 및 문제정의

* **상황:** 네트워크 타임아웃이나 클라이언트 재시도로 동일한 결제 확정 요청이 두 번 도달할 수 있음 ("3초 응답 없음 → 자동 재시도" 설정 하나로 운영 환경에서 실제로 발생)
* **문제:** 동일 `orderId`로 두 번의 결제가 생성되고 Toss Payments에 중복 API 호출 → 이중 청구 위험

단일 방어선만으로는 부족하다.

* Redis만 있으면: Redis 장애 시 완전 무방비
* DB UNIQUE만 있으면: 두 요청이 동시에 도달해 트랜잭션 커밋 전이면 두 번 모두 PG사 API를 호출하게 됨

### 솔루션: 3중 독립 방어

* 각 레이어가 서로 다른 장애 시나리오를 담당하여 단일 장애 지점 없이 방어

```
1. Redis SETNX (PaymentIdempotencyManager)
   — 동일 orderId 최초 요청만 통과, 빠른 선제 차단, DB 부하 없음

2. Toss Payments Idempotency-Key 헤더
   — PG사 레벨에서 중복 API 호출 차단

3. payments.order_id UNIQUE 제약 (V8 migration)
   — DB 레벨 최후 방어선
```

| 장애 상황 | 동작 |
|-----------|------|
| Redis 장애 | SETNX 우회 → Toss Idempotency-Key로 이중 청구 차단 → DB UNIQUE로 중복 INSERT 차단 |
| DB 부하·느린 응답 | Redis SETNX가 첫 요청만 통과 → PG사 API 호출 1회로 제한 |

### 기능 구현

**1. Redis SETNX — 선제 차단**

`PaymentIdempotencyManager`: `orderId`를 키로 SETNX, TTL은 결제 처리 예상 시간보다 충분히 길게 설정.

**2. Toss Payments Idempotency-Key 헤더**

`TossPaymentsClient` 요청 헤더에 `Idempotency-Key` 추가하여 PG사가 자체적으로 중복 요청을 차단하게 함.

**3. DB UNIQUE 제약 — 최후 방어선**

`V8 migration`: `payments.order_id UNIQUE` 제약 추가. Redis 장애·코드 버그·예상치 못한 경로 모두 차단.

### Trade-off

3중 방어는 구현·운영 복잡도를 높인다. Redis SETNX의 TTL이 결제 처리 시간보다 짧으면 키 만료 사이에 재시도 요청이 통과될 수 있다. Redis SPOF 문제는 ElastiCache Multi-AZ 또는 Resilience4j Circuit Breaker(이미 프로젝트에 적용됨)로 완화 가능하다.

### 결과

* 네트워크 재시도로 인한 이중 결제 차단
* Redis 장애·코드 결함·PG사 오동작 각각의 시나리오에서 독립적으로 방어
* Redis SPOF 리스크는 인지하고 있으며 Multi-AZ 또는 Circuit Breaker로 완화 가능

---

## 6. 이벤트 유실 — Transactional Outbox

### 배경 및 문제정의

* **상황:** `ApplicationEventPublisher`로 도메인 이벤트를 발행하는 구조 사용 중
* **문제:** 커밋 직후, 이벤트 발행 직전 JVM 크래시 발생 시 이벤트가 영구 유실됨

```
1. 주문 생성 트랜잭션 커밋
                              ← 여기서 JVM 크래시 발생 시 이벤트 영구 유실
2. publishEvent(OrderCreatedEvent)
3. 이메일 발송 / 재고 알림
```

* `@TransactionalEventListener(AFTER_COMMIT)` 사용 시도 동일 — AFTER_COMMIT 단계에서 실패한 이벤트는 재시도나 보상 트랜잭션이 불가능함

### 솔루션: Transactional Outbox 패턴

* 이벤트 발행과 비즈니스 로직을 **동일 트랜잭션으로 묶어** 커밋되면 반드시 저장되도록 보장
* 별도 스케줄러가 저장된 이벤트를 폴링하여 발행 — JVM 재기동 시 PENDING 이벤트 자동 재처리

### 기능 구현

**1. 비즈니스 트랜잭션과 이벤트 저장 원자화**

```java
@Transactional(propagation = Propagation.MANDATORY)
public void save(DomainEvent event) {
    // 트랜잭션 없이 호출 시 IllegalTransactionStateException
    // → 이벤트 저장 누락 경로를 런타임에 즉시 감지
}
```

**2. OutboxEventRelayScheduler — 5초 폴링**

PENDING 이벤트 조회 → 발행 → PROCESSED 표시. JVM 재기동 시 미처리 이벤트 자동 재처리.

**3. OutboxEventPurgeScheduler + 모니터링**

매일 새벽 3시 7일 이상 처리 완료 이벤트 정리. Prometheus 게이지 `outbox.dead_letters`로 재시도 한계 초과 이벤트 실시간 모니터링.

### Trade-off

폴링 방식은 이벤트 처리에 최대 5초의 지연을 허용한다. 이메일·알림처럼 수 초 지연이 허용되는 용도에 적합하다. 재고 부족 즉시 경보처럼 실시간성이 중요한 경우라면 Kafka 등 메시지 브로커 도입이 더 적합하다.

### 결과

* DB에 저장된 이벤트는 반드시 발행됨 (JVM 크래시 후 재기동 시 PENDING 이벤트 자동 재처리)
* Dead letter 알람으로 처리 실패 이벤트 즉시 감지 가능

---

## 7. N+1 쿼리 및 배치 처리 최적화

### 배경 및 문제정의

* **`getMyCoupons()` — 2중 N+1:** 쿠폰 50개 보유 시 쿼리 101번 발생 (user_coupons SELECT 1 + coupons SELECT N + coupon_usages COUNT N)
* **`InventorySnapshotScheduler` — 개별 save():** 재고 1,000건 기준 INSERT 1,000번 실행 → DB connection 장시간 점유, 슬로우 쿼리 알람 발생 가능
* **`ReviewRepository` — 분리 집계:** 상품별 avgRating + reviewCount를 각각 조회 → 상품 20개 기준 쿼리 40번
* **`@ManyToOne` 전반 — 배치 미적용:** `OrderItem → Product`, `CartItem → Product` 등 개별 접근 시 SELECT 1건씩 추가. 기본 설정에서는 배치 없이 1건씩만 로딩
* **`InventoryRepository` — product fetch join 누락:** `findByProductId()`, `findAllByProductIdIn()`이 `@EntityGraph` 없이 Lazy 로딩 → 상품 목록 20건 조회 시 product SELECT 20번 추가 발생
* **`InventorySnapshotScheduler` — 단일 트랜잭션 전체 로드:** `findAll()`로 재고 10,000건을 한 번에 힙에 적재 → GC 압박 + 트랜잭션 유지 중 `inventory` 테이블 shared lock 장시간 점유

### 솔루션

| 위치 | 변경 전 | 변경 후 |
|------|---------|---------|
| `UserCouponRepository` | Lazy join | `@EntityGraph(coupon)` fetch join |
| `CouponUsageRepository` | N번 count | `countByCouponIdsAndUserId()` IN 절 배치 |
| `InventorySnapshotScheduler` | 개별 `save()` N번 | `saveAll()` 배치 INSERT |
| `ReviewRepository` | 집계 쿼리 2번 | avgRating + reviewCount 통합 단일 JPQL |
| 전체 `@ManyToOne` | 1건씩 Lazy | `hibernate.default_batch_fetch_size=50` |
| `inventory_transactions` | `inventory_id` 단일 인덱스 | `(inventory_id, created_at)` 복합 인덱스 |
| `InventoryRepository` | product Lazy 로딩 (N+1) | `@EntityGraph({"product"})` — `findByProductId`, `findAllByProductIdIn` |
| `InventorySnapshotScheduler` | `findAll()` 단일 트랜잭션 전체 로드 | `findAll(PageRequest.of(page, 1000))` 페이지 루프 + 배치마다 독립 트랜잭션 커밋 |

### 기능 구현

**1. @EntityGraph + IN 절 배치로 getMyCoupons() 최적화**

```java
@EntityGraph(attributePaths = {"coupon"})
List<UserCoupon> findByUserId(Long userId);
```

`CouponUsageRepository.countByCouponIdsAndUserId()`: 쿠폰 ID 목록을 IN 절로 한 번에 집계.

**2. default_batch_fetch_size 전역 적용**

```properties
# application.properties
spring.jpa.properties.hibernate.default_batch_fetch_size=50
```

> **주의**: `@BatchSize`는 `@OneToMany`/`@ManyToMany` 컬렉션에만 적용 가능. `@ManyToOne`에 붙이면 `AnnotationException` 발생 → `default_batch_fetch_size` 사용.

**3. InventorySnapshotScheduler 페이지 단위 처리**

`InventorySnapshotProcessor(@Component)`를 별도 빈으로 분리하여 Spring AOP 프록시를 통해 배치마다 독립 `@Transactional` 커밋.

> **이유**: 동일 클래스 내 self-call은 프록시를 거치지 않아 `@Transactional`이 적용되지 않으므로 외부 빈으로 분리해 호출해야 한다.

페이지 단위 처리는 부분 실패 시 롤백 범위를 1페이지(1,000건)로 제한하는 장점도 있다. 첫 페이지 조회 후 다른 인스턴스가 동일 날짜 스냅샷을 삽입하는 레이스 컨디션은 `DataIntegrityViolationException` catch로 방어하며 해당 배치를 스킵한다.

### Trade-off

`default_batch_fetch_size`는 전역 설정으로 배치 로딩이 불필요한 단건 조회에도 적용되어 미세한 메모리 오버헤드가 있다. `@EntityGraph` fetch join은 컬렉션이 아닌 단일 `@ManyToOne`에만 적용했다 (페이징과 컬렉션 fetch join 조합 시 `HibernateJpaDialect` 경고 발생).

### 결과

| 위치 | 변경 전 | 변경 후 | 개선율 |
|------|---------|---------|--------|
| `getMyCoupons()` (쿠폰 50개 기준) | 쿼리 101번 | 3번 | **97% 감소** |
| `InventorySnapshotScheduler` (재고 1,000건) | INSERT 1,000번 | 1번 (`saveAll`) | **99.9% 감소** |
| 상품 목록 `ReviewRepository` (상품 20개) | 집계 쿼리 40번 | 1번 | **97% 감소** |
| `inventory_transactions` 이력 조회 | filesort 발생 | 인덱스 스캔 | filesort 제거 |
| `InventoryRepository.findAllByProductIdIn` (상품 페이지 20건) | product SELECT 20번 (N+1) | JOIN 1번 | **95% 감소** |
| `InventorySnapshotScheduler` 페이지 처리 (재고 10,000건) | 단일 트랜잭션, 힙 전체 로드 | 1,000건 단위 10회 독립 커밋 | 힙 사용량 **~1/10**, 락 점유 시간 **~1/10** |

---

## 8. 보안 취약점

### 배경 및 문제정의

* **IDOR (Insecure Direct Object Reference):** `orderId=999`가 사용자 B의 주문이어도 소유권 검증 없이 취소 가능. 배송 조회, 결제 조회에서도 동일한 문제
* **가격 조작:** 클라이언트가 `unitPrice`를 1원으로 보내면 그대로 주문 생성 — 정가 100,000원짜리 상품을 1원에 구매 가능
* **JWT userId DB round-trip:** 모든 인증 요청마다 `username → userId` 변환을 위해 DB SELECT 발생 — 초당 1,000 req 기준 인증 처리만으로 `users` 테이블 초당 1,000번 조회 (req:select = 1:1)
* **TossPayments Webhook 위조:** 서명 검증 없이 수신 — 외부 공격자가 임의 결제 완료 이벤트를 위조해 보낼 수 있음

### 솔루션

| 취약점 | 해결 전략 |
|--------|-----------|
| IDOR | 서비스 레이어에서 소유권 검증 — 인증된 userId와 리소스 소유자 일치 여부 확인 |
| 가격 조작 | 클라이언트 `unitPrice` 무시, 주문 생성 시 DB에서 canonical price 강제 적용 |
| JWT round-trip | JWT 생성 시 `userId` 클레임 포함 — SecurityContext에서 직접 추출 (DB fallback 유지) |
| Webhook 위조 | HMAC-SHA256 서명 검증 추가 |

### 기능 구현

**1. IDOR 소유권 검증**

`validateOwnership()` 소유권 검증을 4개 엔드포인트(주문 취소, 배송 조회, 결제 prepare, 결제 조회)에 추가.

**2. 주문 생성 시 DB canonical price 강제 적용**

`OrderService.create()`에서 클라이언트 전달 `unitPrice`를 무시하고 `productRepository`에서 조회한 실제 가격을 강제 적용.

**3. JWT userId 클레임으로 DB round-trip 제거**

```java
// JwtAuthenticationFilter: userId를 details에 저장
Long userId = Long.parseLong(claims.get("userId").toString());
authentication.setDetails(userId);

// Service: DB 없이 SecurityContext에서 바로 추출
Object details = SecurityContextHolder.getContext()
    .getAuthentication().getDetails();
if (details instanceof Long id) return id;  // DB 조회 불필요
return userRepository.findByUsername(username).getId();  // fallback
```

**4. Toss Webhook HMAC-SHA256 서명 검증**

`TossWebhookVerifier`: 요청 헤더의 서명 값과 HMAC-SHA256으로 직접 계산한 값을 비교.

### Trade-off

JWT에 `userId`를 포함하면 토큰 크기가 소폭 증가한다. `userId`는 변경되지 않는 값이지만 기존 발급 토큰 만료 전까지 새 클레임이 반영되지 않으므로 DB fallback 경로를 유지했다.

### 결과

* 가격 조작: 클라이언트가 어떤 값을 보내도 DB 가격이 강제 적용됨
* JWT round-trip: 초당 1,000 req 기준 `users` SELECT 1,000회 → 0회 (req:select = 1:1 → 1:0)
* IDOR: 타인 주문·결제·배송 접근 차단

---

## 9. CategoryService Redis 캐시

### 배경 및 문제정의

* **상황:** `CategoryService.getList()` / `getTree()`는 호출마다 `categoryRepository.findAll()`을 실행하고, `getTree()`는 추가로 전체 카테고리를 순회해 부모-자식 트리를 in-memory에서 조립
* **문제:** 카테고리는 관리자가 생성·수정·삭제할 때만 변경되는 **거의 정적인 데이터**인데 상품 목록 API가 매 요청마다 이를 호출

```
피크 TPS 1,000 기준:
  상품 목록 API 1,000회/s
  → CategoryService.getList() 1,000회/s
  → SELECT * FROM categories 1,000회/s
```

* `getTree()`는 카테고리 수에 비례하는 O(n) 연산이 매 호출마다 반복되어 카테고리 규모가 늘어날수록 CPU 부하가 증가

### 솔루션: @Cacheable 적용 + Redis 직렬화 호환성 확보

* 거의 변하지 않는 카테고리 데이터를 Redis에 캐싱하여 불필요한 DB 조회와 in-memory 조립 연산을 제거
* write 메서드에 `@CacheEvict` 적용으로 캐시 무효화 보장

### 기능 구현

**1. @Cacheable / @CacheEvict 적용 + TTL 설정**

```java
@Cacheable(cacheNames = "categories", key = "'list'")
public List<CategoryResponse> getList() { ... }

@CacheEvict(cacheNames = "categories", allEntries = true)
@Transactional
public CategoryResponse create(CategoryCreateRequest request) { ... }
// update(), delete() 동일
```

`CacheConfig`에 `categories` 전용 TTL 30분 추가.

**2. Redis 직렬화 호환성 문제 해결**

`List<T>`를 직접 캐싱하는 과정에서 3가지 직렬화 문제가 발생했다.

| # | 증상 | 원인 | 해결 |
|---|------|------|------|
| 1 | `List<CategoryResponse>` HIT 시 역직렬화 실패 | `As.PROPERTY`는 JSON 배열에 `@class` 프로퍼티 내장 불가 → 타입 정보 누락 | `CacheConfig` 직렬화 방식을 `As.WRAPPER_ARRAY`로 변경 |
| 2 | 캐시 리스트 역직렬화 불가 | `Stream.toList()` 반환 타입이 JDK 내부 클래스(`ImmutableCollections$ListN`) → Jackson 인스턴스화 불가 | `Collectors.toList()`로 교체 |
| 3 | `CategoryResponse` 역직렬화 불가 | Lombok `@Builder`만 있으면 Jackson이 역직렬화 생성자를 찾지 못함 | `@Jacksonized` 추가 + `children` 필드 `new ArrayList<>()` |

> **교훈**: 기존 캐시(`products`, `inventory`, `orders`)는 단일 객체 캐싱이라 `As.PROPERTY`에서 문제가 없었다. `List<T>` 직접 캐싱 시 컬렉션 타입 래퍼 방식이 달라지므로 통합 테스트에서 캐시 HIT 경로까지 반드시 검증해야 한다.

### Trade-off

| 항목 | 내용 |
|------|------|
| 최대 30분 Stale | write API 누락 시 TTL 만료까지 구버전 카테고리 노출. 현재는 `create / update / delete` 모두 `@CacheEvict` 적용 |
| Write 경로 관리 부담 | 향후 카테고리 변경 로직 추가 시 `@CacheEvict` 누락이 silent bug가 됨 — 코드 리뷰에서 반드시 확인 필요 |
| `As.WRAPPER_ARRAY` 전환 영향 | 기존 캐시 키(`products::*`, `inventory::*`, `orders::*`) 직렬화 포맷 변경 → 운영 배포 시 기존 키 무효화 필요 |
| 직렬화 제약 | 캐시 대상 DTO에 `@Jacksonized`와 가변 `List` 강제 — 누락 시 캐시 HIT에서만 역직렬화 에러 발생 |
| Cold Start | 재배포 직후 또는 TTL 만료 직후 첫 요청은 DB 조회 + 트리 조립 발생 |

### 결과

| 지표 | 변경 전 | 변경 후 |
|------|---------|---------|
| 상품 목록 API 1회당 `categories` SELECT | 1번 | **0번** (캐시 HIT) |
| `getTree()` in-memory 트리 조립 | 매 호출 O(n) | **0** (캐시 HIT, TTL 내 재사용) |
| `categories` DB 쿼리 (피크 TPS 1,000 기준) | 초당 **1,000번** | 캐시 갱신 시만 1번 (**99.9% 감소**) |

---

## 10. 코드 리뷰 개선 항목

> 코드 리뷰를 통해 발견한 보안 취약점·버그·성능 결함·코드 품질 문제 중 완료된 항목.
> 항목 1~9의 주요 개선과 별도로, 세부 항목 단위로 정리한다.

### 프로덕션 버그 수정

| # | 항목 | 조치 |
|---|------|------|
| 80 | Payment 금액에 쿠폰/포인트 할인 미반영 — 사용자 과다 결제 | `Order.getPayableAmount()` 추가 (totalAmount − discountAmount − usedPoints), `PaymentService.prepare()`에서 payableAmount 기준으로 검증·저장 |
| 81 | 동일 상품 중복 주문 항목 시 `Collectors.toMap` 500 에러 | `OrderService.create()/preview()`에서 중복 productId 사전 차단 (INVALID_INPUT 400) |
| 1 | 음수 재고 허용 버그 | `InventoryService` — `BusinessException(INVENTORY_STATE_INCONSISTENT)` throw |
| 2 | 포인트 롤백 불일치 | `OrderService.create()` — `validateBalance()` fail-fast 사전 검증 추가 |
| 3 | 배송·포인트 무음 삼킴 | `PaymentTransactionHelper` — Outbox 패턴(`SHIPMENT_CREATE`, `POINT_EARN`) 전환 |
| 4 | null userId NPE | `OrderExpiryScheduler` — `cancelBySystem()` 전용 메서드, Order에서 userId 직접 조회 |
| 5 | saveAll() 예외 무음 삼킴 | `InventorySnapshotScheduler` — `DataIntegrityViolationException` 분리, 장애 시 re-throw |
| 6 | SpEL null NPE | `DistributedLockAspect` — `resolveKey()` null/예외 시 `BusinessException` throw |
| 113 | 부분 취소 금액 DB 미반영 — Toss 부분 환불 + DB 전액 CANCELLED | `Payment.cancel(reason, cancelAmount)` 2인자, `cancelledAmount` 누적 필드, `PARTIAL_CANCELLED` 상태 활성화, 부분 취소 시 `orderService.refund()` 미실행 |

---

### 보안 취약점 수정

| # | 항목 | 조치 |
|---|------|------|
| 46 | JWT Secret 기본값 → 시작 실패 | `JwtTokenProvider @PostConstruct` `IllegalStateException` |
| 58 | Admin 기본 자격증명 → 시작 실패 | `AdminSecurityConfig @PostConstruct` `IllegalStateException` |
| 110 | TossPaymentsConfig secretKey null 시 `"null:"` 인코딩 | `@PostConstruct` null/blank 검증 후 `IllegalStateException` throw |
| 63 | Actuator 엔드포인트 과다 노출 | `env/heapdump/threaddump/beans/mappings/loggers` 비활성화 |
| 56 | PaymentController IDOR | 소유권 검증 + `orderRepository.findUserIdById()` |
| 82 | 회원 탈퇴 시 Access Token 미무효화 (최대 24시간 접근 가능) | `deactivate()` 시 현재 Access Token `jwtBlacklist.revoke()` 등록 |
| 97 | `AuthController.logout()` 인증 없이 호출 가능 | `/api/auth/logout`을 `authenticated()` 규칙 적용, SecurityConfig 분리 |
| 85 | `CacheConfig LaissezFaireSubTypeValidator` — RCE 가능 | `BasicPolymorphicTypeValidator` 화이트리스트 (`com.stockmanagement.`, `java.util.`, `java.time.`) |
| 84 | `SignupRequest.username` 패턴 검증 없음 — Redis 키 오염 | `@Pattern(regexp = "^[a-zA-Z0-9_-]+$")` 추가 |
| 89 | `PresignedUrlRequest.fileExtension` 문자 검증 없음 | `@Pattern(regexp = "^[a-zA-Z0-9]{1,10}$")` 추가 |
| 64 | `CouponValidateRequest @Size` 누락 | `@Size(min=8, max=50)` 추가 |
| 57 | `TossWebhookVerifier` Mac 직렬화 위험 | 싱글턴 + `synchronized` → 호출마다 `Mac.getInstance()` |
| 100 | `RequestIdFilter` 외부 `X-Request-Id` 무검증 — 로그 인젝션 | 길이 64자 상한 + 영숫자/하이픈 패턴 검증, 초과 시 UUID 생성 |

---

### 동시성·안정성 개선

| # | 항목 | 조치 |
|---|------|------|
| 66 | `PaymentService.confirm()` catch — `release()` 미실행 가능 | `try-finally`로 `release()` 보장 |
| 72 | `CouponService.releaseCoupon()` — `usageCount` lost update | `findByIdWithLock()` 비관적 락으로 교체 |
| 73 | `CouponService.claim()/issueToUser()` TOCTOU — 제네릭 409 | `DataIntegrityViolationException` catch → `BusinessException(COUPON_ALREADY_ISSUED)` 재발행 |
| 77 | Outbox `POINT_EARN` 포인트 이중 적립 가능 | `earn()` 내부 `existsByOrderIdAndType()` 멱등성 체크 추가 |
| 91 | `PointService.getOrCreate()` 동시 요청 Duplicate Key | `DataIntegrityViolationException` catch 후 재조회 retry 패턴 적용 |
| 94 | `RefundService.requestRefund()` 동시 요청 Duplicate Key → 500 | `DataIntegrityViolationException` catch 후 재조회 방어 |
| 102 | `applyConfirmResult()` non-DONE 응답 시 `fail()` 롤백 가능 | `markPaymentFailed()` `REQUIRES_NEW` 트랜잭션으로 분리 |
| 52 | Toss 승인 완료 후 주문 만료 경합 | `PAYMENT_IN_PROGRESS` 상태 도입, `findExpiredPendingOrderIds()` PENDING만 조회 |

---

### 캐시·인프라 개선

| # | 항목 | 조치 |
|---|------|------|
| 87 | `ProductService.update()` `@CachePut` 이미지 없는 응답 캐시 | `@CachePut` → `@CacheEvict` 전환 |
| 88 | `ProductImageService` 이미지 변경 시 products 캐시 미무효화 | `saveImage()/deleteImage()/updateImageOrder()`에 `@CacheEvict(products, key=#productId)` 추가 |
| 86 | `StorageConfig S3Presigner` `forcePathStyle` 누락 | `pathStyleAccessEnabled(true)` 추가 |
| 98 | `RefreshTokenStore` 역색인 Set TTL 없음 — Redis 메모리 누수 | `issue()` 시 Set에 35일 TTL 설정 |
| 99 | `AsyncConfig RejectedExecutionHandler` 미설정 — 이벤트 소실 | `CallerRunsPolicy` 적용 |
| 112 | `RedisConfig RedissonClient` `destroyMethod` 미설정 | `@Bean(destroyMethod = "shutdown")` 명시 |

---

### 성능 최적화

| # | 항목 | 조치 |
|---|------|------|
| 7 | `InventoryRepository` N+1 | `@EntityGraph({"product"})` 추가 |
| 29 | `ReviewService·WishlistService` DB round-trip | `resolveUserId()` 헬퍼 패턴, `findByUsername()` 제거 |
| 30 | `OutboxEventRelayScheduler` 지수 백오프 없음 | `nextRetryAt` 컬럼(V34) + 최대 1시간 백오프 |
| 32 | `ReviewService.create()` Product 전체 로드 | `findById()` → `existsById()` |
| 49 | `ShipmentService` userId 프로젝션 | `orderRepository.findUserIdById()` 스칼라 쿼리 |
| 50 | `CouponService` 검증 중복 | `applyCoupon()` → `validateConditions()` 통합 |
| 48 | `CartService` N+1 | `CartRepository.findByUserId()` `@EntityGraph(product)` 확인 완료 |

---

### DB·마이그레이션·코드 품질 개선

| # | 항목 | 조치 |
|---|------|------|
| 10 | `applyConfirmResult()` 이중 DB 조회 | `confirm()` → `Order` 반환, 재사용 |
| 11 | Outbox 배치 크기 하드코딩 | `@Value("${outbox.relay.batch-size:100}")` + Prometheus counter |
| 12 | DB 인덱스 누락 | V23/V25/V27 마이그레이션 |
| 14 | `RefundService` 이중 쿼리 | `userId` 비정규화, `validateOwnership()` orders 조회 제거 |
| 17 | P6Spy 운영 오버헤드 | `implementation` → `runtimeOnly` |
| 18 | Zipkin 샘플링 100% | 1.0 → 0.1 |
| 19 | `OrderSearchRequest` mutable DTO | `of(request, forceUserId)` 오버로드 |
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
| 92 | `CouponCreateRequest` 날짜 교차 검증 없음 + PERCENTAGE 100% 초과 | `validFrom >= validUntil` 검증 + `discountValue <= 100` 제약 추가 |
| 93 | `WishlistService.getList()` 필터링 후 `totalElements` 불일치 | 필터링 후 content 크기 기반 totalElements 재계산 |

---

## 테스트 커버리지 추이

| 시점 | 테스트 수 |
|------|-----------|
| 기본 도메인 완성 (Product / Inventory / Order / Payment / User) | ~100개 |
| 리뷰 / 위시리스트 / 포인트 / 환불 + 통합 테스트 | 465개 |
| 전 컨트롤러 단위 테스트 완비 | 529개 |
| 배치 / Elasticsearch / 쿠폰 / 카테고리 추가 | ~580개 |
| 성능 최적화 (#7/#8/#9) 이후 | 605개 |
| 코드 리뷰 개선 항목 (#10) + Wave 2/3 프론트엔드 API 개선 | **623개** |
