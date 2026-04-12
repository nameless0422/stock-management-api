# TODO — 기술적 개선 항목

> 시니어 백엔드 관점에서 발견한 보안 취약점, 성능 결함, 코드 품질 문제, 운영 리스크를 우선순위별로 정리한다.

---

## 🔴 우선순위 높음 — ✅ 모두 완료

### ~~1. PaymentService의 JWT userId 미적용 (DB 라운드트립 잔존)~~

**위치**: `PaymentService.prepare()`, `PaymentTransactionHelper.loadAndValidateForConfirm()`, `loadAndValidateForCancel()`, `getByOrderId()`

**문제**: `OrderService`에는 JWT claim에서 `userId`를 꺼내는 `resolveUserId()` 패턴(SecurityContext details)이 적용됐으나, Payment 도메인은 여전히 `userRepository.findByUsername(username)` DB 조회를 수행한다. 결제 확정 경로는 트래픽이 집중되는 핵심 경로임에도 인증 단계에서 매 요청마다 `users` 테이블 SELECT가 발생한다.

**개선**: `PaymentService`와 `PaymentTransactionHelper`에 동일한 `resolveUserId(username)` 패턴 적용.

---

### ~~2. Elasticsearch 상품 동기화 누락~~

**위치**: `ProductService.update()`, `ProductService.delete()`, `InventoryService.receive()` / `adjust()`

**문제**: MySQL에서 상품 가격·이름·카테고리·상태가 변경되거나 재고가 입고돼도 ES `ProductDocument`가 갱신되지 않는다. 상품 검색 결과에 구 가격·삭제된 상품·재고 0 상품이 계속 노출된다.

**개선**: 상품 write 메서드에서 `ProductSearchService.save(ProductDocument)` / `delete(id)` 호출. 또는 `@TransactionalEventListener(AFTER_COMMIT)` + `ProductUpdatedEvent`로 비동기 동기화.

---

### ~~3. CSP `unsafe-eval` 제거~~

**위치**: `SecurityConfig.java` L134–142

**문제**: CSP에 `script-src 'unsafe-eval'`이 허용돼 있어 `eval()`, `Function()` 기반 XSS가 가능하다. 관리자 SPA나 Swagger UI가 `eval`을 실제로 필요로 하는지 확인 후 제거. Swagger UI는 nonce 기반 CSP로 전환 가능하다.

---

### ~~4. 가상계좌 Webhook 미처리 (TODO 상태)~~

**위치**: `PaymentService.handleWebhook()` L260–265

**문제**: 가상계좌 입금 완료(`WAITING_FOR_DEPOSIT → DONE`) 이벤트가 도달해도 주문 확정·배송 생성이 이루어지지 않는다. 코드에 `// TODO: implement full webhook-driven confirmation for virtual accounts` 주석만 존재한다. 가상계좌를 결제 수단으로 노출한다면 결제는 됐는데 주문이 처리되지 않는 상황이 발생한다.

**개선**: `DONE` 웹훅 수신 시 `transactionHelper.applyConfirmResult()` 호출로 confirm 흐름 재사용.

---

### ~~5. JWT 시크릿 기본값 위험 (운영 배포 사고 예방)~~

**위치**: `application.properties` L37

**문제**: `JWT_SECRET` 환경변수 미설정 시 `stock-management-secret-key-for-development-only`가 사용된다. 배포 파이프라인에서 환경변수 누락 시 개발키로 운영 JWT가 서명되어 공격자가 토큰을 위조할 수 있다.

**개선**: Spring Boot Actuator health check에 JWT secret 검증 로직 추가, 또는 `@PostConstruct`에서 `JWT_SECRET`이 환경변수로 주입됐는지 assert.

---

## 🟠 우선순위 중간 — 스프린트 내 처리 권장

### 6. P6Spy 운영 환경 오버헤드

**위치**: `build.gradle` L64

**문제**: `p6spy-spring-boot-starter`가 `implementation` 스코프로 포함돼 운영 JVM에서도 모든 JDBC 호출을 프록시로 래핑한다. SQL 로깅이 필요 없는 운영에서는 불필요한 CPU·메모리 오버헤드.

**개선**: `runtimeOnly` 스코프로 이동 후 `application-production.properties`에서 `decorator.datasource.p6spy.enabled=false` 설정, 또는 profile별 조건부 포함.

---

### 7. Zipkin 샘플링 100% (운영 비용)

**위치**: `application.properties` L78

**문제**: `management.tracing.sampling.probability=1.0`으로 설정돼 피크 TPS 1,000 환경에서 초당 1,000건의 트레이스가 Zipkin으로 전송된다. 디스크·네트워크·메모리 부담이 크다.

**개선**: 운영 profile에서 `0.01~0.1`로 조정. `application-production.properties`에 별도 설정.

---

### 8. `applyConfirmResult()` 내 이중 DB 조회

**위치**: `PaymentTransactionHelper.java` L162

**문제**: `orderService.confirm(orderId)` 호출 후 포인트 계산을 위해 `orderRepository.findById(orderId)`를 한 번 더 호출한다. 같은 트랜잭션 내에서 동일 엔티티를 두 번 SELECT하는 중복 쿼리다.

**개선**: `confirm()` 메서드가 `Order`를 반환하도록 변경하거나, `PaymentTransactionHelper` 내에서 직접 `orderRepository.findByIdWithItems()`로 단일 조회.

---

### 9. Outbox 릴레이 배치 크기 하드코딩

**위치**: `OutboxEventRelayScheduler.java` L56, `OutboxEventRepository`

**문제**: `findTop100By...`로 처리 건수가 100건으로 고정돼 있다. 앱 다운 후 재기동 시 적체된 이벤트가 많아도 5초마다 최대 100건만 처리된다. 반대로 이벤트 발생이 드물면 불필요하게 전체 배치를 가져온다.

**개선**: `outbox.relay.batch-size=100` 프로퍼티화, 처리 완료 건수를 Prometheus counter로 노출.

---

### 10. DB 인덱스 감사

**예상 누락 인덱스**:

| 테이블 | 컬럼 | 조회 패턴 |
|--------|------|----------|
| `orders` | `user_id, status` | 사용자 주문 목록 필터 |
| `orders` | `created_at` | 기간 검색, 통계 집계 |
| `cart_items` | `user_id` | 장바구니 조회 |
| `outbox_events` | `published_at, retry_count` | 5초 폴링 |
| `user_coupons` | `user_id, coupon_id` | 내 쿠폰 목록 |
| `point_transactions` | `user_id, order_id` | 포인트 환불 |
| `reviews` | `product_id` | 상품별 리뷰 조회 |

**개선**: `EXPLAIN ANALYZE`로 슬로우 쿼리 확인 후 V27+ 마이그레이션으로 누락 인덱스 추가.

---

### 11. `OrderSearchRequest.setUserId()` mutable DTO 패턴

**위치**: `OrderService.getList()` L248

**문제**: `request.setUserId(userId)` 호출로 메서드 파라미터로 들어온 DTO를 직접 변경한다. mutable한 요청 DTO를 서비스가 변형하는 것은 가독성·테스트 가독성을 낮추고, 향후 멀티스레드 재사용 시 사이드이펙트 위험이 있다.

**개선**: `OrderSearchRequest`를 immutable하게 만들거나, `userId`를 별도 파라미터로 받아 `OrderSpecification.of(request, userId)`로 처리.

---

### 12. 리드 레플리카 라우팅 미적용

**위치**: 전체 `readOnly = true` 쿼리

**문제**: `@Transactional(readOnly = true)` 메서드들이 모두 MySQL Primary에 연결된다. 운영 아키텍처에 Replica가 포함돼 있으나 실제 읽기 부하 분산이 이루어지지 않는다.

**개선**: Spring의 `AbstractRoutingDataSource` 또는 `LazyConnectionDataSourceProxy`로 `readOnly` 트랜잭션을 Replica로 라우팅. 또는 AWS Aurora Reader Endpoint 사용.

---

### 13. 오프셋 페이지네이션 성능 한계

**위치**: 주문 목록, 재고 이력, 리뷰 등 `Pageable` 사용 엔드포인트

**문제**: `LIMIT ? OFFSET ?` 방식은 페이지 번호가 클수록 DB가 앞 레코드를 모두 스캔 후 버린다. 주문 이력이 수십만 건인 사용자가 마지막 페이지를 조회하면 풀스캔에 가까운 쿼리가 실행된다.

**개선**: 잦은 스크롤 조회(주문 이력, 재고 이력)는 `WHERE id < :lastId LIMIT ?` 커서 기반 페이지네이션으로 전환.

---

## 🟡 우선순위 낮음 — 기술 부채 관리

### 14. 주석 언어 불일치

**위치**: `PaymentService.java`, `PaymentTransactionHelper.java`

**문제**: `OrderService`·`InventoryService`는 한국어 주석, `PaymentService`·`PaymentTransactionHelper`는 영어 Javadoc. 팀 컨벤션 미통일.

**개선**: 전체를 한국어(키워드 영문) 통일. `OrderService.confirm()` / `refund()` 메서드도 Javadoc 한국어 전환.

---

### 15. API 버전 관리 부재

**위치**: 모든 컨트롤러 (`/api/products`, `/api/orders` 등)

**문제**: 버전 prefix 없이 `/api/` 직접 사용. 클라이언트가 존재하는 상황에서 응답 스키마 변경 시 하위 호환성 관리 불가.

**개선**: `/api/v1/` prefix 도입. Spring MVC `ApiVersionRequestMappingHandlerMapping` 또는 단순 prefix 방식 중 선택.

---

### 16. 동시성 통합 테스트 부재

**위치**: `src/test/java/.../integration/`

**문제**: k6 부하 테스트는 외부 도구이고, JVM 레벨에서 실제 `reserve()` 동시 호출이 재고 초과 없이 처리되는지 검증하는 테스트가 없다. 분산 락·비관적 락 2중 전략이 실제로 동작하는지 자동 회귀 테스트로 보장되지 않는다.

**개선**: `ExecutorService`와 `CountDownLatch`로 동일 상품에 N개 스레드가 동시 `reserve()` 호출 → 재고 이하 성공 / 초과분 실패 검증하는 통합 테스트 추가.

---

### 17. 뮤테이션 테스트 미도입

**문제**: JaCoCo 라인 커버리지 70%는 코드 실행 여부만 측정한다. `inventory.reserve()` 내 `<` 를 `<=`로 바꿔도 라인 커버리지는 동일하다. 실제 비즈니스 로직 정확성은 검증되지 않는다.

**개선**: PIT (Pitest) 뮤테이션 테스트 도입. 재고·결제·쿠폰 등 핵심 비즈니스 로직 대상으로 mutation score 60% 이상 목표.

---

### 18. `storage.enabled` 프로퍼티 문서화 누락

**위치**: `application.properties`

**문제**: `StorageConfig`의 `@ConditionalOnProperty(name = "storage.enabled")` 에 대응하는 설정이 `application.properties`에 존재하지 않는다. 파일 업로드를 사용하려면 이 값을 `true`로 설정해야 한다는 것이 명시되지 않아 배포 누락 가능성이 있다.

**개선**: `application.properties`에 `storage.enabled=false` + 주석 추가. docker-compose, README 환경변수 목록에도 반영.

---

### 19. Dockerfile HEALTHCHECK 미정의

**문제**: `Dockerfile`에 `HEALTHCHECK` 지시자가 없어 컨테이너 오케스트레이터(Docker Compose의 `depends_on: condition: service_healthy`, ECS 등)가 앱 준비 상태를 알 수 없다.

**개선**:
```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

---

### 20. 쿠폰 코드 엔트로피 검증 없음

**위치**: 쿠폰 생성 API, `CouponService`

**문제**: 쿠폰 코드 길이·형식에 대한 서버 사이드 검증이 없다. 관리자가 `"A"` 같은 단순 코드를 생성하면 무차별 대입으로 쿠폰 탈취가 가능하다.

**개선**: 쿠폰 코드 최소 8자 이상, 영문+숫자 혼합 검증(`@Pattern`). 생성 시 충분한 랜덤 코드 자동 생성 유틸리티 제공.

---

### 21. 특정 사용자 전체 Refresh Token 무효화 불가

**위치**: `RefreshTokenStore.java`

**문제**: 로그아웃 시 해당 세션의 Refresh Token만 revoke된다. 계정 탈취·비밀번호 변경 시 해당 사용자의 **모든 기기** Refresh Token을 일괄 무효화하는 "전체 로그아웃" 기능이 없다.

**개선**: Redis에 `refresh:user:{userId}` 집합(Set)으로 해당 사용자의 모든 토큰 ID 관리. 비밀번호 변경·계정 잠금 시 집합 전체 삭제.

---

## ✅ 완료된 항목 (이전 TODO)

| 기능 | 상태 |
|------|------|
| 인증 (로그인/로그아웃/토큰 재발급) | ✅ |
| 상품 목록/검색 (Elasticsearch) | ✅ |
| 상품 상세 (재고 상태·별점 통합) | ✅ |
| 카테고리 트리 | ✅ |
| 장바구니 (품절 상태 포함) | ✅ |
| 주문 생성/취소/목록/이력 | ✅ |
| 주문 상세 통합 응답 (주문+결제+배송) | ✅ |
| TossPayments 결제/취소 | ✅ |
| 배송 조회 | ✅ |
| 환불 | ✅ |
| 리뷰 작성/수정/조회/삭제 | ✅ |
| 위시리스트 추가/삭제/목록/단건 상태 | ✅ |
| 포인트 잔액/이력 | ✅ |
| 배송지 관리 | ✅ |
| 내 쿠폰 목록 / 공개 쿠폰 claim | ✅ |
| 프로필 수정 / 비밀번호 변경 | ✅ |
| 주문 아이템 리뷰 작성 여부 | ✅ |
| 상품 이미지 상세 포함 | ✅ |
