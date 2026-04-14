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

## 🔴 보안 취약점 진단 (5/10 완료, #60 조치 불필요)

> 전체 코드베이스 보안 분석. IDOR·인증 누락·설정 오류 중심.

---

### ✅ 56. `PaymentController.getByPaymentKey()` — 인증·소유권 검증 없음 (IDOR)

**위치**: `domain/payment/controller/PaymentController.java` L87-91

**문제**: `GET /api/payments/{paymentKey}` 엔드포인트에 `@AuthenticationPrincipal`이 없고 결제 소유권을 검증하지 않음. 인증된 사용자라면 누구나 paymentKey를 알거나 추측해 타인의 결제 정보(금액·주문 ID·카드 정보)를 조회할 수 있음.

```java
// 현재 — 소유권 검증 없음
@GetMapping("/{paymentKey}")
public ApiResponse<PaymentResponse> getByPaymentKey(@PathVariable String paymentKey) {
    return ApiResponse.ok(paymentService.getByPaymentKey(paymentKey));
}
```

**개선**: `@AuthenticationPrincipal String username`에서 userId를 추출해 결제 소유자와 일치하는지 검증하거나, ADMIN 권한일 경우에만 허용한다.

---

### ✅ 57. `TossWebhookVerifier` — `Mac` 인스턴스 `synchronized` 직렬화로 처리량 저하

**위치**: `common/security/TossWebhookVerifier.java` L34-58

**문제**: `Mac` 객체를 싱글턴으로 유지하고 `synchronized(mac)` 블록에서 사용. Webhook 트래픽이 몰릴 때 MAC 연산이 직렬화되어 병목 발생. 복잡한 동시성 관리보다 매 호출마다 새 인스턴스를 생성하는 것이 더 단순하고 안전하다.

**개선**: `@PostConstruct`에서 Mac 초기화를 제거하고, `verify()` 호출 시 `Mac.getInstance(ALGORITHM)` + `mac.init(key)`를 수행한 뒤 사용 후 버린다.

---

### ✅ 58. Admin 기본 자격증명 (`admin/changeme`) — 운영 배포 시 변경 누락 위험

**위치**: `common/config/AdminSecurityConfig.java`, `application.properties`

**문제**: `ADMIN_USERNAME` / `ADMIN_PASSWORD` 환경변수 미설정 시 `admin/changeme`로 동작. Spring Boot Admin UI(`/admin-ui`)에 로그인 가능 → 모든 actuator 엔드포인트(힙 덤프·스레드 덤프·환경변수) 접근 허용.

**개선**: #46(JWT Secret)과 동일한 패턴 — `@PostConstruct`에서 기본값 사용 감지 시 `IllegalStateException`으로 시작 실패 처리.

---

### 59. MySQL `useSSL=false` + `allowPublicKeyRetrieval=true` — 평문 DB 통신

**위치**: `src/main/resources/application.properties` datasource URL

**문제**: `useSSL=false`로 DB 연결이 평문 전송. 같은 네트워크에서 패킷 스니핑 시 쿼리·결과·자격증명 노출 가능. `allowPublicKeyRetrieval=true`는 MITM 공격자가 MySQL 서버로 위장해 공개키를 제공하고 비밀번호를 탈취할 수 있는 추가 벡터가 됨.

**개선**: 운영 환경에서는 `useSSL=true&requireSSL=true`로 변경하고, 환경변수(`DB_SSL_ENABLED`)로 개발/운영을 분리한다.

---

### ~~60. Toss Webhook — 타임스탬프 검증 없음 (Replay Attack 가능)~~ → 조치 불필요

**위치**: `domain/payment/controller/PaymentController.java` `/webhook` 핸들러, `common/security/TossWebhookVerifier.java`

**조사 결과 (2026-04-15)**: Toss 공식 문서 확인 결과, `tosspayments-webhook-signature` / `tosspayments-webhook-transmission-time` 헤더는 **`payout.changed`·`seller.changed` 이벤트 전용**이다. 우리 코드에서 처리하는 `PAYMENT_COMPLETED`, `VIRTUAL_ACCOUNT_DEPOSIT` 등 **일반 결제 웹훅에는 타임스탬프 헤더가 포함되지 않는다**. 따라서 헤더 기반 타임스탬프 검증은 적용 불가.

**현황으로 충분한 이유**:
- `PaymentIdempotencyManager` (Redis SETNX) — 동일 paymentKey 중복 처리 차단
- `Payment.status` 상태 전이 검증 — 이미 CONFIRMED 상태인 결제는 재처리 거부
- 멱등성 키 만료 이후 재전송 시나리오는 결제 완료 후 장기간이 지난 상태이므로 실질적 위협 낮음

**참고**: [토스페이먼츠 웹훅 이벤트 문서](https://docs.tosspayments.com/reference/using-api/webhook-events)

---

### ~~61. CSP `unsafe-inline` script-src — XSS 완화 효과 반감~~ → 실질 위험도 낮음, 조치 보류

**위치**: `common/config/SecurityConfig.java` `contentSecurityPolicy()`

**현황**: `script-src 'self' 'unsafe-inline'` — Spring Boot Admin UI(`/admin-ui`)가 인라인 스크립트를 포함하고 있어 제거 시 어드민 UI가 깨짐.

**실질 위험도가 낮은 이유**:
- 이 서버는 **순수 REST API** — 브라우저가 렌더링할 HTML을 직접 서빙하지 않음
- XSS 공격은 "서버가 사용자 입력을 escape 없이 HTML로 출력"해야 성립하는데, JSON API 응답에는 해당 없음
- CSP 헤더는 `/admin-ui` HTML 응답에 적용되는 것이 실질적 범위이며, 어드민은 인증된 사용자만 접근

**근본 해결책**: nonce 기반 CSP — Spring MVC 인터셉터로 매 요청마다 랜덤 nonce를 생성하고 SBA 정적 HTML에 주입해야 하나, 외부 라이브러리(SBA) 내부 HTML 커스터마이징이 필요해 구현 복잡도가 높음.

**결론**: 프론트엔드 SPA 서버(HTML 직접 렌더링)에서 중요한 항목. 이 API 서버 범위에서는 우선순위 낮음.

---

### 62. DB 자격증명 — 환경변수 미설정 시 하드코딩 평문 사용

**위치**: `src/main/resources/application.properties` `spring.datasource.username/password`

**문제**: `${DB_USERNAME:root}` / `${DB_PASSWORD:root}` 형식으로 환경변수 미설정 시 `root/root` 사용. 애플리케이션 JAR 또는 설정 파일이 유출되면 DB 자격증명이 노출됨. 프로덕션 설정 검토 누락 시 root 계정으로 DB 직접 접근 가능.

**개선**: 기본값 제거(`${DB_USERNAME}` + `${DB_PASSWORD}`). 미설정 시 Spring Boot 시작 실패 → 운영 배포 전 누락 즉시 감지. Vault, AWS Secrets Manager, 또는 Kubernetes Secret 연동 권장.

---

### ✅ 63. Actuator — `/env`, `/beans`, `/loggers` 엔드포인트 외부 노출

**위치**: `src/main/resources/application.properties` `management.endpoints.web.exposure.include`

**문제**: `health,info,prometheus,metrics,loggers,env,beans,mappings,threaddump,heapdump` 전체 노출. `env` 엔드포인트는 환경변수(JWT Secret, DB 비밀번호 등)를 마스킹된 형태로 표시하지만 일부 값은 노출됨. `heapdump`는 힙 메모리 덤프를 통해 평문 비밀번호·토큰을 추출 가능.

**개선**:
```properties
# 공개 허용
management.endpoints.web.exposure.include=health,info,prometheus,metrics
# ADMIN 전용 (AdminSecurityConfig에서 인증 처리)
management.endpoint.env.enabled=false
management.endpoint.heapdump.enabled=false
```

---

### ✅ 64. `CouponValidateRequest.couponCode` — `@Size` 검증 누락

**위치**: `domain/coupon/dto/CouponValidateRequest.java`

**문제**: `@NotBlank`만 있고 `@Size(max=50)` 가 없음. 악의적 사용자가 수백 KB의 couponCode를 전송하면 `LOWER(code) = LOWER(:code)` 쿼리 파라미터로 그대로 전달됨. 쿼리 실행 오버헤드 외에도 파라미터 바인딩 시 DB 드라이버가 대형 문자열을 처리함.

**개선**: `@Size(min=8, max=50)` 추가. `CouponCreateRequest.code`의 `@Pattern`과 동일한 길이 제약을 검증에도 적용한다.

---

### 65. `GlobalExceptionHandler` — 예외 메시지 클라이언트 노출

**위치**: `common/exception/GlobalExceptionHandler.java`

**문제**: `handleException(Exception e)` 폴백 핸들러가 `e.getMessage()`를 응답 본문에 포함할 경우 내부 스택 정보(클래스명, SQL 오류 등)가 외부에 노출될 수 있음. 특히 JPA `DataIntegrityViolationException`, `ConstraintViolationException` 등은 테이블명·컬럼명을 메시지에 포함함.

**개선**: 폴백 핸들러는 `ErrorCode.INTERNAL_SERVER_ERROR`의 고정 메시지만 반환하고, 실제 예외 메시지는 서버 로그에만 기록한다.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("예상치 못한 오류 발생", e);
    return ResponseEntity.status(INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR)); // e.getMessage() 제거
}
```

---

## 🔴 코드리뷰 — 구조·성능·보안 (6/10 완료)

> 182개 Java 파일 전수 분석. 우선순위 순으로 정렬.

---

### ✅ 46. JWT Secret 기본값 — 로그 경고만 출력, 시작 실패 없음

**위치**: `common/security/JwtTokenProvider.java` `@PostConstruct`

**문제**: `JWT_SECRET` 환경변수 미설정 시 개발용 기본값으로 계속 실행됨. ERROR 로그를 출력하지만 아무도 로그를 보지 않으면 운영 배포에서 취약한 시크릿이 그대로 사용됨.

**개선**: 기본값 감지 시 `IllegalStateException`으로 애플리케이션 시작 자체를 실패시킨다.

```java
if (DEV_DEFAULT_SECRET.equals(secretKeyStr)) {
    throw new IllegalStateException(
        "JWT_SECRET 환경변수가 설정되지 않았습니다. 운영 환경에서는 반드시 설정하세요.");
}
```

---

### 47. `X-Forwarded-For` 마지막 IP 추출 — Rate Limit 우회 가능

**위치**: `common/ratelimit/RateLimitAspect.java` `extractClientIp()`

**문제**: `X-Forwarded-For: client, proxy1, proxy2`에서 마지막 IP(`proxy2`)를 추출하는데, 공격자가 헤더를 `X-Forwarded-For: real_ip, attacker_ip`로 조작하면 `attacker_ip`로 Rate Limit 버킷이 생성되어 실질적인 IP 기반 제한이 무력화됨.

**개선**: 프록시 수(`rate-limit.trusted-proxy-count`)를 설정하고, 헤더 끝에서 N번째 IP를 추출한다.

```java
// X-Forwarded-For에서 신뢰할 수 있는 클라이언트 IP 추출
String[] ips = forwarded.split(",");
int idx = Math.max(0, ips.length - 1 - trustedProxyCount);
return ips[idx].trim();
```

---

### ✅ 48. `CartService.getCart()` — Product 지연로딩 N+1

**위치**: `domain/order/cart/service/CartService.java` `getCart()`

**문제**: `cartRepository.findByUserId(userId)`로 CartItem을 조회한 뒤 루프에서 `item.getProduct().getId()`를 호출 → 각 CartItem마다 Product 지연로딩 쿼리 발생 (N+1). Inventory는 배치 조회되지만 Product 조회가 N번 실행됨.

**개선**: CartItem 조회 시 Product를 fetch join으로 함께 로드한다.

```java
@Query("SELECT c FROM CartItem c JOIN FETCH c.product WHERE c.userId = :userId ORDER BY c.createdAt DESC")
List<CartItem> findByUserIdWithProduct(@Param("userId") Long userId);
```

---

### ✅ 49. `ShipmentService.getByOrderId()` — 소유권 검증에 Order 전체 엔티티 로드

**위치**: `domain/shipment/service/ShipmentService.java` `getByOrderId()`

**문제**: 배송 조회 시 소유권 검증을 위해 `orderRepository.findById(orderId)`로 Order 전체(+ 컬렉션 Lazy) 를 로드하지만 실제 필요한 건 `userId` 필드 하나뿐.

**개선**: `userId`만 프로젝션으로 조회한다.

```java
// OrderRepository에 추가
@Query("SELECT o.userId FROM Order o WHERE o.id = :orderId")
Optional<Long> findUserIdById(@Param("orderId") Long orderId);
```

---

### ✅ 50. `CouponService` — 동일 조건 중복 검증 (3곳)

**위치**: `domain/coupon/service/CouponService.java`

**문제**: 쿠폰 유효성 검증 로직(`active`, `validFrom`, `validUntil`, `maxUsageCount`, 사용자 중복 여부)이 `validate()`, `applyCoupon()`, `issueCoupon()` 세 곳에 분산·중복됨. 정책 변경 시 3곳을 모두 수정해야 해 누락 위험 있음.

**개선**: `validateCouponConditions(Coupon, Long userId)` private 메서드로 추출하고 세 곳에서 호출한다.

---

### 51. `OrderService` God Object — 단일 서비스에 책임 과다

**위치**: `domain/order/service/OrderService.java` (430줄)

**문제**: 주문 생성·조회·취소·상태 이력·페이지네이션·쿠폰 적용·포인트 차감·재고 검증·배송지 검증을 단일 클래스가 담당. 테스트 시 Mock이 10개 이상 필요하며, 기능 추가마다 클래스가 비대해짐.

**개선 방향**:
- `OrderQueryService` — 조회 전용 (readOnly)
- `OrderCommandService` — 생성·취소 (write)
- `OrderValidationService` — 배송지·쿠폰·포인트 사전 검증 추출

---

### 52. Toss 승인 완료 후 주문 만료 경합 — 수동 환불 필요 시나리오 무처리

**위치**: `domain/payment/service/PaymentTransactionHelper.java` `applyConfirmResult()` L175-187

**문제**: Toss API 승인 요청 중(`callTossConfirmApi()` 대기) 만료 스케줄러가 Order를 CANCELLED로 변경 가능. Toss는 결제를 완료했으나 주문은 취소된 상태 → CRITICAL 로그만 출력하고 사용자는 청구됨.

**현황**: `log.error("[Payment] CRITICAL: …수동 환불 필요…")` 에 그침.

**개선 방향**:
- CRITICAL 상황 감지 시 운영팀 알림 발송 (Slack Webhook, PagerDuty 등)
- `dead_letter_payments` 테이블에 레코드 삽입해 대시보드에서 추적
- 결제 승인 직전 Order 상태를 `PAYMENT_IN_PROGRESS`로 선점해 만료 스케줄러가 건드리지 못하게 차단

---

### 53. `ProductService` ES Fallback — MySQL LIKE 검색으로 결과 불일치

**위치**: `domain/product/service/ProductService.java` `getList()` L98-116

**문제**: ES 장애 시 MySQL `LIKE '%keyword%'` 검색으로 Fallback하는데, ES의 형태소 분석·관련성 점수 기반 정렬과 전혀 다른 결과를 반환함. 사용자 입장에서 검색 결과가 갑자기 바뀌는 UX 문제 발생.

**개선 옵션**:
- ES 장애 시 빈 결과 반환 + "검색 서비스 일시 장애" 메시지 표시 (결과 불일치 방지)
- 또는 MySQL 전문 검색(`MATCH … AGAINST`)으로 대체해 품질 차이를 최소화

---

### ✅ 54. `DistributedLock` + Pessimistic Lock 이중 제어 — 단일 인스턴스에서 불필요한 오버헤드

**위치**: `domain/inventory/service/InventoryService.java` `reserve()` / `releaseReservation()` 등

**문제**: Redis 분산 락(`@DistributedLock`) + DB `SELECT FOR UPDATE`(비관적 락)을 동시에 사용. 단일 인스턴스 환경에서는 분산 락이 불필요하고, 멀티 인스턴스 환경에서는 DB 비관적 락만으로도 충분한 경우가 많음. 두 락을 모두 획득해야 하므로 레이턴시 증가.

**현황**: 의도적 이중 방어선이라면 문서화 필요. 설계 판단이 필요한 항목.

---

### ✅ 55. `RefundService.requestRefund()` — `noRollbackFor + throw` 패턴 불명확

**위치**: `domain/refund/service/RefundService.java` `requestRefund()` L58-107

**문제**: `@Transactional(noRollbackFor = Exception.class)`와 `catch (Exception e) { refund.fail(); throw e; }`를 함께 사용. 예외를 던지면서도 트랜잭션을 커밋하려는 의도인데, 클라이언트는 HTTP 500을 받고 Refund 상태만 FAILED로 변경된 채로 유지됨. 의도가 불명확해 유지보수 시 오해 유발.

**개선**: 예외를 던지지 않고 FAILED 상태를 반환하거나, 명시적인 주석으로 패턴 의도를 문서화한다.

```java
// 현재: throw e (클라이언트 500 수신, refund는 FAILED 커밋)
// 개선: FAILED 상태를 정상 응답으로 반환 (클라이언트가 재시도 가능)
refund.fail(reason);
return RefundResponse.from(refund); // HTTP 200, status=FAILED
```

---

## 🟠 미처리 — 성능·아키텍처 판단 필요

---

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

## 🟡 미처리 기술 부채

---

### 25. API 버전 관리 부재

**위치**: 모든 컨트롤러 (`/api/products`, `/api/orders` 등)

**문제**: 버전 prefix 없이 `/api/` 직접 사용. 응답 스키마 변경 시 하위 호환성 관리 불가.

**개선**: `/api/v1/` prefix 도입.

---

## 🟡 DBA 스키마 미처리 — 아키텍처 판단 필요

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

### 45. `refunds.payment_id UNIQUE` — `PARTIAL_CANCELLED`(부분 취소) 여러 건 이력 불가

**위치**: `V21__create_refunds_table.sql`, `payments.status = PARTIAL_CANCELLED`

**문제**: `payments.status`에 `PARTIAL_CANCELLED`(부분 취소) 상태가 정의되어 있어 한 결제에 대해 여러 차례 부분 환불이 가능해야 하지만, `refunds.payment_id UNIQUE` 제약이 결제당 단 1건의 환불 레코드만 허용. 부분 취소 두 번째 시도 시 DB에서 `Duplicate entry` 에러 발생.

**개선**: 부분 취소 이력이 필요하면 UNIQUE 제약 제거 + `(payment_id, created_at)` 복합 인덱스 전환. 현재는 `reset()` 재사용으로 FAILED 재시도만 지원하나, 부분 취소 누적 이력은 불가.

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

### ✅ 성능 병목 완료 (3/3)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 7 | InventoryRepository fetch join 누락 (N+1) | `InventoryRepository` | `findByProductId`, `findAllByProductIdIn`에 `@EntityGraph({"product"})` 추가 |
| 8 | CategoryService.getTree() 캐시 미적용 | `CategoryService`, `CacheConfig`, `CategoryResponse` | `getList/getTree` `@Cacheable`, `create/update/delete` `@CacheEvict(allEntries=true)`, categories 30분 TTL, `@Jacksonized` + `ArrayList` 적용 |
| 9 | InventorySnapshotScheduler 전체 로드 | `InventorySnapshotScheduler`, `InventorySnapshotProcessor` | 1,000건 페이지 루프 + 배치마다 독립 트랜잭션 커밋 (`@Component` 분리) |

---

### ✅ 신규 발굴 성능 병목 완료 (4/4)

| # | 항목 | 조치 |
|---|------|------|
| 29 | ReviewService · WishlistService `findByUsername()` DB round-trip | 컨트롤러 `resolveUserId()` 헬퍼 패턴 전환, `userRepository.findByUsername()` 제거 |
| 30 | OutboxEventRelayScheduler 지수 백오프 없음 | `nextRetryAt` 컬럼(V34) + `findPendingEvents()` 조건 추가, 최대 1시간 백오프 |
| ~~31~~ | `outbox_events` 쿼리 인덱스 누락 | V18에 이미 `idx_outbox_unpublished` 포함 — 조치 불필요 |
| 32 | ReviewService.create() 불필요한 Product 전체 엔티티 로드 | `productRepository.findById()` → `existsById()` 전환 |

---

### ✅ 스프린트 완료 (8/11)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 10 | applyConfirmResult() 이중 DB 조회 | `PaymentTransactionHelper` | `confirm()` → `Order` 반환, 재사용 |
| 11 | Outbox 배치 크기 하드코딩 | `OutboxEventRelayScheduler` | `@Value("${outbox.relay.batch-size:100}")` + Prometheus counter |
| 12 | DB 인덱스 누락 | V23/V25/V27 마이그레이션 | 모든 항목 기존 마이그레이션으로 커버 |
| 14 | RefundService 이중 쿼리 | `Refund` 엔티티 + `RefundService` | `userId` 비정규화 저장 → `validateOwnership()` orders 조회 제거, V28 마이그레이션 |
| 17 | P6Spy 운영 오버헤드 | `build.gradle` | `implementation` → `runtimeOnly` |
| 18 | Zipkin 샘플링 100% | `application.properties` | 1.0 → 0.1 (운영: 0.01~0.1) |
| 19 | OrderSearchRequest mutable DTO | `OrderSpecification` + `OrderService` | `of(request, forceUserId)` 오버로드, DTO 변경 제거 |

---

### ✅ 기술 부채 완료 (7/8)

| # | 항목 | 파일 | 조치 |
|---|------|------|------|
| 21 | 동시성 통합 테스트 부재 | `ConcurrencyIntegrationTest`, `InventoryConcurrencyTest` | 이미 구현 완료 (ExecutorService + CountDownLatch, reserve() 재고 불변 조건 검증) |
| 22 | Pitest targetClasses 제한적 | `build.gradle` | 6개 → 15개로 확장 (shipment·product·review·wishlist·category·user·address·cart 추가) |
| 23 | 전체 Refresh Token 무효화 불가 | `RefreshTokenStore`, `UserService` | 이미 구현 완료 (`revokeAll()` + 비밀번호 변경 시 자동 호출) |
| 24 | 쿠폰 코드 엔트로피 검증 없음 | `CouponCreateRequest` | 이미 구현 완료 (`@Pattern` 영문+숫자 혼합 8자 이상 강제) |
| 26 | Dockerfile HEALTHCHECK 미정의 | `Dockerfile` | 이미 구현 완료 (`--interval=30s --retries=3`) |
| 27 | `storage.enabled` 문서화 누락 | `application.properties` | 이미 구현 완료 (`storage.enabled=false` + 주석) |
| 28 | Payment 도메인 주석 언어 불일치 | `PaymentService`, `PaymentTransactionHelper` | 영어 inline 주석·Javadoc·log 메시지 → 한국어(키워드 영문) 통일 |

---

### ✅ DBA 진단 완료 (9/13)

| # | 항목 | 조치 |
|---|------|------|
| 33 | `refunds.user_id DEFAULT 0` — 소유권 검증 영구 실패 | V29 백필 마이그레이션 + FK 제약 추가 |
| 34 | `payments` 테이블 CHARSET/COLLATE 미정의 | V29 `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci` |
| 35 | `orders` `(status, created_at)` 복합 인덱스 누락 | V29 `ADD INDEX idx_orders_status_created` |
| 36 | `coupons` 복합 인덱스 누락 + 만료 처리 N UPDATE | V29 인덱스 추가 + `@Modifying` 벌크 UPDATE 전환 |
| 37 | `products.status` 인덱스 누락 | V29 `ADD INDEX idx_products_status` |
| 38 | `cart_items.user_id` FK 누락 | V29 `fk_cart_user` FK 제약 추가 |
| 39 | `point_transactions.order_id` FK 누락 | V29 `fk_pt_order` FK (ON DELETE SET NULL) 추가 |
| 40 | `created_at` DEFAULT CURRENT_TIMESTAMP 없는 테이블 6개 | V33 각 컬럼 ALTER |
| 44 | 일부 테이블 COLLATE 미정의 | V30 3개 테이블 charset/collation 통일 |

---

### ✅ 보안·버그·기능 완료

| 항목 | 비고 |
|------|------|
| #46 JWT Secret 기본값 → 시작 실패 | `JwtTokenProvider @PostConstruct` `IllegalStateException` throw |
| #48 CartService N+1 | `CartRepository.findByUserId()` 이미 `@EntityGraph(product)` 적용 |
| #49 ShipmentService userId 프로젝션 | `orderRepository.findUserIdById()` — Order 전체 로드 불필요 |
| #50 CouponService 검증 중복 제거 | `applyCoupon()` → `validateConditions()` 통합 호출 |
| #54 InventoryService 이중 락 문서화 | `reserve()` Javadoc: Redis 장애 시 DB 비관적 락이 최후 방어선 |
| #55 RefundService noRollbackFor 문서화 | 이미 주석 존재 (confirmed) |
| #56 PaymentController IDOR 수정 | `getByPaymentKey()` 소유권 검증 + `orderRepository.findUserIdById()` |
| #57 TossWebhookVerifier Mac per-call | 싱글턴 + `synchronized` → 호출마다 `Mac.getInstance()` |
| #58 Admin 기본 자격증명 → 시작 실패 | `AdminSecurityConfig @PostConstruct` `IllegalStateException` throw |
| #63 Actuator 엔드포인트 최소화 | `env/heapdump/threaddump/beans/mappings/loggers` 비활성화 |
| #64 CouponValidateRequest @Size 추가 | `@Size(min=8, max=50)` 입력 크기 제한 |
| PaymentService JWT userId 적용 | DB 라운드트립 제거, `resolveUserId()` 패턴 |
| ES 상품 동기화 | `@TransactionalEventListener(AFTER_COMMIT)` + `ProductEventListener` |
| CSP `unsafe-eval` 제거 | `SecurityConfig` script-src 정책 강화 |
| 가상계좌 Webhook 처리 | `applyWebhookConfirmResult()` confirm 흐름 재사용 |
| RefundService 영구 재시도 불가 버그 | FAILED 상태 환불 `reset()` 후 재사용 |
| LoginRateLimiter Lua 원자화 | `RAtomicLong` → Lua INCR+EXPIRE 원자적 처리 |
| ShipmentService / PointService `REQUIRES_NEW` | `UnexpectedRollbackException` 방지 |
| DeliveryAddressRepository `findFirstBy...` | 기본 배송지 삭제 후 1건만 조회 |

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
