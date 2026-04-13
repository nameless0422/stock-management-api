# 개선 작업 내역

> Spring Boot 3.5.11 + Java 17 기반 쇼핑몰 재고 관리 API에서 발견·해결한 기술적 문제 목록.
> 각 항목은 **문제 인식 → 해결 방법 → Trade-off → 결과** 순으로 기술한다.

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

```java
@DistributedLock(key = "'lock:inventory:' + #productId", waitTime = 3, leaseTime = 5)
@Transactional
public void reserve(Long productId, int quantity) {
    Inventory inv = inventoryRepository.findByProductIdWithLock(productId);
    inv.reserve(quantity);  // available < 0이면 InsufficientStockException
}
```

### Trade-off

비관적 락과 분산 락을 동시에 사용하면 처리량(Throughput)이 저하된다. 이를 감안해 재고 차감·반환 핵심 경로에만 선별 적용하고, 단순 조회 API에는 락을 사용하지 않았다.

### 결과

- 동시 100건 주문 시나리오(k6)에서 재고 초과 예약 0건
- 분산 락 획득 실패 시 `LOCK_ACQUISITION_FAILED` 즉시 반환 (무한 대기 없음)

---

## 2. 결제 취소 이중 처리 경쟁 조건

### 문제 인식

사용자가 취소 버튼을 빠르게 두 번 클릭하거나, 네트워크 재시도로 동일 요청이 두 번 도달하면:

```
결제 status = CONFIRMED

Thread 1: SELECT payment WHERE key=X → CONFIRMED (취소 가능 판단)
Thread 2: SELECT payment WHERE key=X → CONFIRMED (취소 가능 판단)
Thread 1: PG사 취소 API 호출 → 성공 → status = CANCELLED
Thread 2: PG사 취소 API 호출 → ??? (이미 취소된 결제에 재요청)
Thread 2: UPDATE status = CANCELLED  ← 중복 기록
```

이 패턴은 TOCTOU(Time-of-Check to Time-of-Use) 취약점의 전형적인 사례다. 상태 확인 시점과 상태 변경 시점 사이에 다른 스레드가 끼어들 수 있다.

프로덕션에서 이 버그가 발생했다면, 결제는 1번 취소됐는데 환불이 2번 지급되는 **금전적 손실**로 이어진다. PG사 API 특성에 따라 중복 취소 자체가 에러 없이 성공하는 경우도 있어 감지가 어렵다.

### 해결 방법

`findByPaymentKeyWithLock()` — 조회 시점에 비관적 락으로 결제 행을 잠금.
상태 확인과 변경이 하나의 락 범위 안에서 이루어진다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Payment> findByPaymentKeyWithLock(String paymentKey);
```

- `loadAndValidateForCancel()`: `readOnly=true` → `@Transactional` + locked query로 변경

### Trade-off

비관적 락은 동일 결제 행에 대한 동시 접근을 직렬화하므로 취소 처리 중 다른 조회도 대기해야 한다. 취소 요청은 전체 결제 건수 대비 빈도가 낮아 실제 처리량 영향은 미미하다.

### 결과

- 취소 요청이 동시에 들어와도 먼저 락을 획득한 스레드만 PG사 API를 호출
- 이후 스레드는 DB에서 이미 `CANCELLED` 상태를 읽어 즉시 반환 — 환불 이중 지급 차단

---

## 3. Redis Rate Limit 원자성 결함

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
TTL 없는 키가 Redis에 남아 **해당 계정이 영구 잠금** 상태가 된다.
사용자 지원팀이 직접 Redis에서 키를 삭제하거나 Redis를 재시작하기 전까지 로그인 불가 상태가 지속된다.

멀티 인스턴스 환경에서는 두 인스턴스가 거의 동시에 `incrementAndGet()`을 호출하면 둘 다 `count=1`로 읽어 TTL을 누락할 수도 있다.

### 해결 방법

INCR + EXPIRE를 **Lua 스크립트로 원자화**하여 단일 연산으로 처리한다.

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
```

Redis는 단일 스레드로 Lua 스크립트를 실행하므로 두 명령 사이에 다른 명령이 끼어들 수 없다.

### Trade-off

Lua 스크립트 실행 중에는 Redis가 다른 명령을 처리하지 않는다. 다만 이 스크립트는 2개 명령으로 구성되어 실행 시간이 수 μs에 불과해 실질적 영향은 없다. Redis Cluster 환경에서는 `KEYS[1]`이 단일 슬롯에 속해야 하는 제약이 있다.

### 결과

- 앱 크래시 시에도 TTL이 보장되어 영구 잠금 버그 제거
- 멀티 인스턴스 환경에서도 INCR과 EXPIRE가 항상 함께 실행

---

## 4. 결제 트랜잭션 정합성

### 문제 인식

결제 확정 후에는 배송 생성, 포인트 적립 같은 부수 효과가 연쇄 실행된다.
초기 구조는 이 모든 로직이 하나의 트랜잭션 안에 묶여 있었다.

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

`createForOrder()`가 예외를 던지면 Spring이 공유 트랜잭션을 **rollback-only**로 표시한다.
catch로 잡아도 이미 늦었다. 바깥 트랜잭션이 commit을 시도하는 순간 `UnexpectedRollbackException`이 터진다.

이 버그가 프로덕션에서 발생했다면:

- **Toss Payments**: 결제 완료 처리됨 (돈 빠져나감)
- **DB**: `payment.status = PENDING` 유지 (rollback됨)
- 사용자가 재시도해도 idempotency key로 차단 → **결제 유실, 지불 후 주문 미처리 상태 지속**

### 해결 방법

```java
// 변경 후: 부수 효과를 독립 트랜잭션으로 분리
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void createForOrder(Long orderId) {
    // 결제 트랜잭션과 별개로 커밋/롤백 — 실패해도 결제 확정에 영향 없음
}
```

- `PointService.use()` / `refundByOrder()`는 주문과 원자성이 필요하므로 `REQUIRED` 유지

### Trade-off

`REQUIRES_NEW`는 부모 트랜잭션과 별개로 새 DB 커넥션을 획득하므로 커넥션 풀 사용량이 증가한다. 또한 배송·포인트 실패 시 결제만 성공하는 불일치 상태가 발생하므로, 이를 감지하고 보상 처리하는 운영 프로세스가 별도로 필요하다.

보상 처리 흐름은 다음 두 가지를 고려할 수 있다:

- **수동 재시도 API**: 운영 툴에서 `POST /api/admin/orders/{orderId}/retry-side-effects` 호출 → 배송·포인트를 개별 재시도
- **미처리 건 배치 스캔**: 매 시간 `payment.status = CONFIRMED AND shipment IS NULL` 조건으로 누락 건 자동 감지 후 재처리

### 결과

- 배송 생성이 실패해도 결제 확정 커밋은 보장됨
- Dead Letter 알람(`outbox.dead_letters`) → 운영 툴 재시도 또는 배치 자동 보상으로 불일치 해소
- 사용자 관점에서 "돈은 나갔는데 주문이 없다"는 상황 차단

---

## 5. 결제 멱등성

### 문제 인식

네트워크 타임아웃이나 클라이언트 재시도로 동일한 결제 확정 요청이 두 번 도달하면:
- 동일 `orderId`로 두 번의 결제가 생성될 수 있었다.
- Toss Payments에도 중복 API 호출이 발생해 이중 청구 위험이 있었다.

서버 응답이 반환되기 전에 클라이언트가 타임아웃으로 재시도하는 상황은 운영 환경에서 빈번하게 발생한다. "3초 응답 없음 → 자동 재시도" 설정 하나로 이 문제가 실제로 터진다.

단일 방어선만으로는 부족하다:
- Redis만 있으면: Redis 장애 시 완전 무방비
- DB UNIQUE만 있으면: 두 요청이 동시에 도달해 트랜잭션이 커밋 전이면 두 번 모두 PG사 API를 호출하게 됨

### 해결 방법

3중 방어를 적용해 각 레이어가 독립적으로 기능하도록 한다.

```
1. Redis SETNX (PaymentIdempotencyManager)
   — 동일 orderId 최초 요청만 통과, 이미 처리 중이면 즉시 차단
   — 빠른 선제 차단, DB 부하 없음

2. Toss Payments Idempotency-Key 헤더
   — PG사 레벨에서 중복 API 호출 차단
   — 서버 내부 처리 결과와 관계없이 이중 청구 자체를 PG사가 막음

3. payments.order_id UNIQUE 제약 (V8 migration)
   — DB 레벨 최후 방어선
   — Redis 장애, 코드 버그, 예상치 못한 경로 모두 차단
```

**레이어 간 장애 상호작용:**

```
[Redis 장애 시]
  SETNX 실패 → 두 요청 모두 진행
  → PG사 Idempotency-Key 헤더로 이중 청구 차단 (PG사 레벨)
  → DB INSERT 시 order_id UNIQUE 제약으로 두 번째 INSERT 실패 (DB 레벨)

[DB 부하 / 느린 응답 시]
  Redis SETNX가 첫 요청만 통과 → PG사 API 호출 1회로 제한
  → DB 응답 지연과 무관하게 이중 호출 자체를 선제 차단 (Redis 레벨)
```

### Trade-off

3중 방어는 구현·운영 복잡도를 높인다. 특히 Redis SETNX의 TTL 설계에 주의가 필요하다 — TTL이 결제 처리 시간보다 짧으면 키가 만료된 사이에 재시도 요청이 통과될 수 있다.

**Redis SPOF 문제**: 현재 구성은 ElastiCache 단일 인스턴스이므로 Redis 자체가 단일 장애 지점(SPOF)이다. Redis가 죽으면 SETNX 레이어가 통째로 무력화되어 모든 중복 요청이 DB까지 도달한다. 피크 TPS 1,000 환경에서 이 상황이 발생하면 `payments` 테이블에 중복 INSERT 시도가 집중되어 RDS 부하가 급증할 수 있다.

대응 전략으로 고려할 수 있는 옵션:

| 전략 | 내용 | 비고 |
|------|------|------|
| ElastiCache Multi-AZ | 스탠바이 노드 + 자동 failover | Redis SPOF 제거, 비용 증가 |
| Circuit Breaker (Resilience4j) | Redis 연결 실패율 임계치 초과 시 SETNX 건너뜀 → DB UNIQUE에만 의존하는 Degraded Mode로 전환 | 장애 전파 차단, 이미 프로젝트에 Resilience4j 적용됨 |
| ALB 요청 제한 | Redis 장애와 무관하게 ALB 수준에서 초당 요청 수 cap | 근본 해결은 아님 |

현재 구현은 Circuit Breaker 없이 3중 방어 레이어 설계에 의존하며, 운영 환경이라면 **ElastiCache Multi-AZ 또는 Circuit Breaker** 도입이 권장된다.

### 결과

- 네트워크 재시도로 인한 이중 결제 차단
- Redis 장애·코드 결함·PG사 오동작 각각의 시나리오에서 독립적으로 방어
- Redis SPOF 리스크는 인지하고 있으며 Multi-AZ 또는 Circuit Breaker로 완화 가능

---

## 6. 이벤트 유실 — Transactional Outbox

### 문제 인식

`ApplicationEventPublisher`로 도메인 이벤트를 발행하는 구조의 치명적 약점이 있다.

```
1. 주문 생성 트랜잭션 커밋
                              ← 여기서 JVM 크래시 발생 시 이벤트 영구 유실
2. publishEvent(OrderCreatedEvent)
3. 이메일 발송 / 재고 알림
```

커밋 이후 이벤트 발행 전에 크래시가 발생하면 이벤트는 흔적 없이 사라진다.
`@TransactionalEventListener(AFTER_COMMIT)`을 사용해도 동일하다. AFTER_COMMIT 단계에서 실패한 이벤트는 재시도나 보상 트랜잭션이 불가능하다.

### 해결 방법

**Transactional Outbox 패턴** 적용. 비즈니스 로직과 이벤트 저장을 **동일 트랜잭션**으로 묶는다.

```
[주문 생성 트랜잭션]
  OrderService.create()
  OutboxEventStore.save(OrderCreatedEvent)  ← 같은 트랜잭션, 커밋되면 반드시 저장됨

[OutboxEventRelayScheduler — 5초 폴링]
  PENDING 이벤트 조회 → 발행 → PROCESSED 표시

[OutboxEventPurgeScheduler — 매일 새벽 3시]
  7일 이상 처리 완료 이벤트 정리
```

```java
@Transactional(propagation = Propagation.MANDATORY)
public void save(DomainEvent event) {
    // 트랜잭션 없이 호출 시 IllegalTransactionStateException
    // → 이벤트 저장 누락 경로를 런타임에 즉시 감지
}
```

Prometheus 게이지 `outbox.dead_letters`로 재시도 한계를 넘긴 이벤트 수를 실시간 모니터링.

### Trade-off

폴링 방식은 이벤트 처리에 최대 5초의 지연을 허용한다. 실시간성보다 발행 보장을 택한 트레이드오프로, 이메일·알림처럼 수 초 지연이 허용되는 용도에 적합하다. 재고 부족 즉시 경보처럼 실시간성이 중요한 경우라면 Kafka 등 메시지 브로커 도입이 더 적합하다.

### 결과

- DB에 저장된 이벤트는 반드시 발행됨 (JVM 크래시 후 재기동 시 PENDING 이벤트 자동 재처리)
- Dead letter 알람으로 처리 실패 이벤트 즉시 감지 가능

---

## 7. N+1 쿼리 및 배치 처리 최적화

### 문제 인식

여러 곳에서 N+1 패턴이 발생하고 있었다.

**`getMyCoupons()` — 2중 N+1**

```
SELECT * FROM user_coupons WHERE user_id = ?                             → 1번
SELECT * FROM coupons WHERE id = ?                                       → N번 (Lazy)
SELECT COUNT(*) FROM coupon_usages WHERE coupon_id = ? AND user_id = ?  → N번
합계: 2N + 1번  →  쿠폰 50개 보유 시 쿼리 101번 발생
```

**`InventorySnapshotScheduler`**

```java
// 기존: 재고 1000건이면 1000번 INSERT
inventories.forEach(inv -> snapshotRepository.save(new DailyInventorySnapshot(...)));
```

자정 스케줄러 실행 시 DB connection을 길게 점유하고 슬로우 쿼리 알람 발생 가능.

**`ReviewRepository`**

```java
// 기존: 상품별 2번의 별도 집계 쿼리 → 상품 20개 기준 40번
double avgRating   = reviewRepository.avgRatingByProductId(productId);
long   reviewCount = reviewRepository.countByProductId(productId);

// 변경 후: 단일 JPQL로 통합
@Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.productId = :id")
ReviewStats findReviewStatsByProductId(Long id);
```

**`@ManyToOne` Lazy Loading 전반**

`OrderItem → Product`, `CartItem → Product` 등 연관 엔티티를 개별 접근할 때마다 SELECT 1건씩 추가.
기본 설정에서는 배치 없이 1건씩만 로딩.

**`InventoryRepository` — product fetch join 누락 (N+1)**

`findByProductId()`, `findAllByProductIdIn()`이 `@EntityGraph` 없이 `Inventory`를 Lazy 로딩한다.
`ProductService.enrichPage()`가 페이지 20건을 처리하며 각 `inventory.product`에 접근하면 product SELECT가 20번 추가 발생한다.

```
상품 목록 20건 조회 시:
  SELECT * FROM inventory WHERE product_id = ?   →  20번 (N+1)
  (Inventory 20개 × product Lazy 로딩)
```

**`InventorySnapshotScheduler` — 단일 트랜잭션 전체 로드**

자정 스케줄러가 `inventoryRepository.findAll()`로 전체 재고를 한 번에 메모리에 올린다.
재고 10,000건 기준:
- JVM 힙에 수백 MB 객체 적재 → GC 압박
- `findAll()` 부터 `saveAll()` 완료까지 트랜잭션이 유지되어 `inventory` 테이블 shared lock이 장시간 점유됨
- 단일 커밋 실패 시 전체 스냅샷이 롤백되어 재시도 비용이 크다

### 해결 방법

| 위치 | 변경 전 | 변경 후 |
|------|---------|---------|
| `UserCouponRepository` | Lazy join | `@EntityGraph(coupon)` fetch join |
| `CouponUsageRepository` | N번 count | `countByCouponIdsAndUserId()` IN 절 배치 |
| `InventorySnapshotScheduler` | 개별 `save()` N번 | `saveAll()` 배치 INSERT |
| `ReviewRepository` | 집계 쿼리 2번 | avgRating + reviewCount 통합 단일 JPQL |
| 전체 `@ManyToOne` | 1건씩 Lazy | `hibernate.default_batch_fetch_size=50` |
| `inventory_transactions` | `inventory_id` 단일 인덱스 | `(inventory_id, created_at)` 복합 인덱스 |
| `InventoryRepository` | `product` Lazy 로딩 (N+1) | `@EntityGraph({"product"})` — `findByProductId`, `findAllByProductIdIn` |
| `InventorySnapshotScheduler` | `findAll()` 단일 트랜잭션 전체 로드 | `findAll(PageRequest.of(page, 1000))` 페이지 루프 + `InventorySnapshotProcessor`(@Component 분리)로 배치마다 독립 트랜잭션 커밋 |

> **주의**: `@BatchSize`는 `@OneToMany`/`@ManyToMany` 컬렉션에만 적용 가능.
> `@ManyToOne`에 붙이면 `AnnotationException` 발생 → `default_batch_fetch_size` 사용.

### Trade-off

`default_batch_fetch_size`는 전역 설정이므로 배치 로딩이 불필요한 단건 조회에도 적용되어 미세한 메모리 오버헤드가 발생한다. `@EntityGraph` fetch join은 페이징(`Pageable`)과 함께 사용 시 `HibernateJpaDialect` 경고가 발생하므로, 컬렉션이 아닌 단일 연관 엔티티(`@ManyToOne`)에만 적용했다.

`InventorySnapshotProcessor`를 별도 `@Component`로 분리한 이유: Spring AOP는 프록시 기반으로 동작하므로 동일 클래스 내 self-call은 프록시를 거치지 않아 `@Transactional`이 적용되지 않는다. 스케줄러 내부에 `@Transactional processBatch()` 메서드를 두어도 배치마다 독립 커밋이 되지 않기 때문에 외부 빈으로 분리해 호출해야 한다. 클래스가 하나 늘어나지만 `@Transactional` 전파 범위를 명시적으로 제어하기 위한 불가피한 설계다.

페이지 단위 처리는 `findAll()` 한 번으로 전체를 보는 것보다 부분 실패 시 롤백 범위가 1페이지(1,000건)로 제한되는 장점도 있다. 단, 첫 페이지 조회 후 다른 인스턴스가 동일 날짜 스냅샷을 삽입하는 레이스 컨디션은 `DataIntegrityViolationException` catch로 방어하며 해당 배치를 스킵한다.

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

`unitPrice`를 클라이언트가 1원으로 보내면 그대로 주문이 생성됐다.
DB의 실제 가격을 검증하지 않았기 때문이다. 정가 100,000원짜리 상품을 1원에 구매할 수 있는 상태였다.

**JWT userId DB round-trip**

모든 인증 요청마다 `username → userId` 변환을 위해 DB SELECT가 1회씩 발생했다.
초당 1,000 req 기준, 비즈니스 로직과 무관한 인증 처리만으로 초당 1,000번의 `users` 테이블 조회가 발생한다 (req:select = 1:1).

**TossPayments Webhook 위조**

Webhook 수신 시 서명 검증이 없어 외부 공격자가 임의 결제 완료 이벤트를 위조해 보낼 수 있었다.

### 해결 방법

| 취약점 | 해결 |
|--------|------|
| IDOR | `validateOwnership()` 소유권 검증 4개 엔드포인트 추가 |
| 가격 조작 | `OrderService.create()`에서 DB canonical price 강제 적용, 클라이언트 `unitPrice` 무시 |
| JWT round-trip | JWT 생성 시 `userId` 클레임 포함, SecurityContext에서 바로 추출 (DB fallback 유지) |
| Webhook 위조 | HMAC-SHA256 서명 검증 추가 |

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

### Trade-off

JWT에 `userId`를 포함하면 토큰 크기가 소폭 증가한다. 또한 `userId`는 변경되지 않는 값이지만, 기존 발급 토큰이 만료되기 전까지 새 클레임이 반영되지 않으므로 DB fallback 경로를 유지했다. 이 fallback은 JWT 파싱 실패나 레거시 토큰 호환을 위한 안전망이기도 하다.

### 결과

- 가격 조작: 클라이언트가 어떤 값을 보내도 DB 가격이 강제 적용됨
- JWT round-trip: 초당 1,000 req 기준 인증 단계 `users` SELECT 1,000회 → 0회 (req:select = 1:1 → 1:0)
- IDOR: 타인 주문·결제·배송 접근 차단

---

## 9. CategoryService Redis 캐시

### 문제 인식

`CategoryService.getList()`와 `getTree()`는 호출할 때마다 `categoryRepository.findAll()`을 실행하고, `getTree()`는 추가로 전체 카테고리를 순회해 부모-자식 트리를 in-memory에서 조립한다.

카테고리는 관리자가 생성·수정·삭제할 때만 변경되는 **거의 정적인 데이터**다. 그런데 상품 목록 API(`GET /api/products`)는 카테고리 정보를 참조하기 위해 매 요청마다 이 메서드를 호출한다.

```
피크 TPS 1,000 기준:
  상품 목록 API 1,000회/s
  → CategoryService.getList() 1,000회/s
  → SELECT * FROM categories 1,000회/s  (비즈니스 로직 없이 인증 오버헤드와 동급)
```

`getTree()`는 조립 과정에서 카테고리 수에 비례하는 O(n) 연산이 매 호출마다 반복된다. 카테고리가 수백 개 규모로 증가하면 CPU 부하로 이어진다.

### 해결 방법

`getList()`, `getTree()`에 `@Cacheable`, 모든 write 메서드에 `@CacheEvict(allEntries = true)` 적용.

```java
@Cacheable(cacheNames = "categories", key = "'list'")
public List<CategoryResponse> getList() { ... }

@Cacheable(cacheNames = "categories", key = "'tree'")
public List<CategoryResponse> getTree() { ... }

@CacheEvict(cacheNames = "categories", allEntries = true)
@Transactional
public CategoryResponse create(CategoryCreateRequest request) { ... }
// update(), delete() 동일
```

`CacheConfig`에 `categories` 전용 TTL(30분) 추가:

```java
"categories", base.entryTtl(Duration.ofMinutes(30))
```

**구현 과정에서 Redis 직렬화 호환성 문제 3가지를 추가 해결했다.**

| # | 증상 | 원인 | 해결 |
|---|------|------|------|
| 1 | `List<CategoryResponse>` 캐시 HIT 시 역직렬화 실패 | `As.PROPERTY` 방식은 JSON 배열에 `@class` 프로퍼티를 내장할 수 없어 `List` 타입 래퍼가 누락됨. 읽을 때 Jackson이 타입 정보를 찾지 못함 | `CacheConfig` 전역 직렬화 방식을 `As.WRAPPER_ARRAY`로 변경 → `["java.util.ArrayList", [...]]` 형식으로 타입 래퍼 보장 |
| 2 | 캐시에 저장된 리스트 역직렬화 불가 | `Stream.toList()`의 반환 타입이 JDK 내부 패키지-프라이빗 클래스(`ImmutableCollections$ListN`)라 Jackson이 인스턴스화 불가 | `Collectors.toList()`로 교체 → 공개 타입 `ArrayList` 반환, 직렬화 포맷도 `java.util.ArrayList`로 확정 |
| 3 | `CategoryResponse` 역직렬화 불가 | Lombok `@Builder`만 있으면 Jackson이 역직렬화 시 사용할 생성자(`no-args` 또는 `@JsonCreator`)를 찾지 못함 | `@Jacksonized` 추가 → Lombok이 `@JsonDeserialize(builder = ...)` + `@JsonPOJOBuilder(withPrefix = "")` 자동 생성. `children` 필드도 `List.of()` → `new ArrayList<>()`로 교체 |

> **교훈**: 기존 캐시(`products`, `inventory`, `orders`)는 단일 객체를 캐싱해 `As.PROPERTY`에서 문제가 없었다. `List<T>`를 직접 캐싱하면 컬렉션 타입 래퍼 방식이 달라지므로 통합 테스트에서 반드시 캐시 HIT 경로까지 검증해야 한다.

### Trade-off

| 항목 | 내용 |
|------|------|
| 최대 30분 Stale | write API가 누락되면 TTL 만료까지 구버전 카테고리 노출. 현재는 `create / update / delete` 모두 `@CacheEvict` 적용 |
| Write 경로 관리 부담 | 향후 카테고리 변경 로직 추가 시 `@CacheEvict` 누락이 silent bug가 됨 — 코드 리뷰에서 반드시 확인 필요 |
| `As.WRAPPER_ARRAY` 전환 영향 | 기존 캐시 키(`products::*`, `inventory::*`, `orders::*`)의 직렬화 포맷이 변경됨. 운영 배포 시 기존 캐시 키 무효화 필요 (`FLUSHDB` 또는 자연 TTL 만료 대기) |
| 직렬화 제약 추가 | 캐시 대상 DTO에 `@Jacksonized`와 가변 `List` 사용을 강제해야 함. 누락 시 캐시 HIT에서만 역직렬화 에러가 발생해 원인 파악이 어려움 |
| Cold Start | 재배포 직후 또는 TTL 만료 직후 첫 요청은 DB 조회 + 트리 조립이 발생 |

### 결과

| 지표 | 변경 전 | 변경 후 |
|------|---------|---------|
| 상품 목록 API 1회당 `categories` SELECT | 1번 | **0번** (캐시 HIT) |
| `getTree()` in-memory 트리 조립 | 매 호출 O(n) | **0** (캐시 HIT, TTL 내 재사용) |
| `categories` DB 쿼리 (피크 TPS 1,000 기준) | 초당 **1,000번** | 캐시 갱신 시만 1번 (**99.9% 감소**) |

---

## 테스트 커버리지 추이

| 시점 | 테스트 수 |
|------|-----------|
| 기본 도메인 완성 (Product / Inventory / Order / Payment / User) | ~100개 |
| 리뷰 / 위시리스트 / 포인트 / 환불 + 통합 테스트 | 465개 |
| 전 컨트롤러 단위 테스트 완비 | 529개 |
| 배치 / Elasticsearch / 쿠폰 / 카테고리 추가 | ~580개 |
| 성능 최적화 (#7/#8/#9) 이후 | **605개** |
