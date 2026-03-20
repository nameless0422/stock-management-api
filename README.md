# stock-management-api

Spring Boot 기반 쇼핑몰 재고 관리 API.
**재고 → 주문 → 결제 → 유저** 전 흐름을 구현하며, Redis 분산 락과 Testcontainers 통합 테스트를 포함합니다.

---

## ⚙️ 기술 스택

| 분류 | 기술 |
|---|---|
| 프레임워크 | Spring Boot 3.5.11, Spring Security 6 |
| 언어 / 빌드 | Java 17, Gradle 8 |
| DB / ORM | MySQL 8, Spring Data JPA (Hibernate 6), Flyway |
| 캐시 / 락 | Redis 7, Redisson 3.27.2 (분산 락), Spring Cache |
| 검색 | Elasticsearch 8.18 (Spring Data Elasticsearch) |
| 인증 | Spring Security 6 + JWT (jjwt 0.12.6), Refresh Token |
| 결제 | TossPayments Core API v1 |
| 회복탄력성 | Resilience4j (Circuit Breaker) |
| API 문서 | springdoc-openapi 2.8.4 (Swagger UI) |
| 관리자 | Spring Boot Admin 3.4.3 |
| 테스트 | JUnit 5, Mockito, Testcontainers (MySQL + Redis + Elasticsearch) |
| 인프라 | Docker Compose |

---

## 🚀 로컬 실행

### 사전 요구사항
- JDK 17+, Docker

### 인프라 기동

```bash
docker compose -f docker/docker-compose.yml up -d
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

Flyway가 기동 시 V1~V12 마이그레이션을 자동 실행합니다.

### 환경 변수 (선택)

```bash
export JWT_SECRET=your-secret-key-at-least-32-characters-long
export TOSS_SECRET_KEY=test_sk_your_actual_key
export TOSS_CLIENT_KEY=test_ck_your_actual_key
export ADMIN_USERNAME=admin          # Spring Boot Admin UI 계정 (기본: admin)
export ADMIN_PASSWORD=your-password  # Spring Boot Admin UI 비밀번호 (기본: changeme)
```

### 접속 URL

| URL | 설명 |
|---|---|
| `http://localhost:8080/swagger-ui/index.html` | Swagger API 문서 |
| `http://localhost:8080/admin-ui` | Spring Boot Admin (인프라 모니터링) |

---

## 🧪 테스트

```bash
# 전체 테스트 (Docker 필요 — Testcontainers가 MySQL·Redis·Elasticsearch 컨테이너를 자동으로 띄움)
./gradlew test

# 커버리지 리포트
./gradlew jacocoTestReport
# build/reports/jacoco/test/html/index.html
```

### 테스트 구조

| 종류 | 위치 | 비고 |
|---|---|---|
| 단위 | `domain/*/service/`, `entity/`, `common/lock/`, `security/` | Mockito |
| 컨트롤러 | `domain/*/controller/` | `@WebMvcTest` |
| 통합 | `integration/` | Testcontainers MySQL+Redis+Elasticsearch, Flyway 실행 |
| 동시성 | `integration/InventoryConcurrencyTest` | 동시 입고 lost update · 예약 overselling 검증 |

현재 총 **318개** 테스트 전체 통과.

---

## 📁 프로젝트 구조

```
com.stockmanagement/
├── common/
│   ├── config/          # SecurityConfig, AdminSecurityConfig, SpringBootAdminConfig
│   │                    # JpaConfig, RedisConfig, CacheConfig, OpenApiConfig, TossPaymentsConfig
│   ├── dto/             # ApiResponse<T> — 전 엔드포인트 통합 응답 래퍼
│   ├── exception/       # BusinessException, InsufficientStockException, ErrorCode, GlobalExceptionHandler
│   ├── filter/          # RequestIdFilter (MDC requestId 주입)
│   ├── lock/            # @DistributedLock (어노테이션), DistributedLockAspect (AOP)
│   ├── ratelimit/       # @RateLimit (어노테이션), RateLimitAspect (AOP, Redis)
│   └── security/        # LoginRateLimiter, JwtBlacklist, RefreshTokenStore
├── domain/
│   ├── product/         # 상품 CRUD + Elasticsearch 검색 (document/, service/ProductSearchService)
│   ├── inventory/       # 재고 4-state 모델 + 변동 이력(InventoryTransaction) + 필터 검색
│   ├── order/           # 주문 생성·취소, 멱등성 키, 상태 이력, 만료 자동 취소, 필터 조회
│   ├── payment/         # TossPayments 연동, 결제 준비·확인·취소, Circuit Breaker
│   ├── cart/            # 장바구니 담기·수정·삭제·체크아웃
│   ├── shipment/        # 배송 상태 관리 (PREPARING→SHIPPED→DELIVERED/RETURNED)
│   ├── deliveryaddress/ # 배송지 관리 (기본 배송지, 주문 연동)
│   ├── user/            # 회원가입·로그인, ADMIN/USER 역할, Refresh Token
│   └── admin/           # 관리자 대시보드, 사용자 관리, 전체 주문 조회
└── security/            # JwtTokenProvider, JwtAuthenticationFilter
```

각 도메인: `entity / repository / service / controller / dto`

---

## 🗂 핵심 도메인

### Inventory — 재고 4-state 모델

```
available = onHand - reserved - allocated
```

| 이벤트 | 변화 |
|---|---|
| 입고 | `onHand++` |
| 주문 생성 | `reserved++` |
| 결제 완료 | `reserved--, allocated++` |
| 주문 취소 (결제 전) | `reserved--` |
| 결제 취소 (환불) | `allocated--` |

모든 변동은 `InventoryTransaction`에 이력으로 기록됩니다.

### 동시성 제어

재고 뮤테이션 메서드에 2중 락이 적용됩니다.

```
요청 → @DistributedLock (Redis, waitTime 5s) → @Lock(PESSIMISTIC_WRITE) (DB) → 재고 변경
```

- **분산 락**: 멀티 인스턴스 환경에서 Redis를 통해 직렬화
- **비관적 락**: DB 레벨 lost update 방지 (`SELECT ... FOR UPDATE`)

### Order — 멱등성 보장

`idempotencyKey` DB UNIQUE 제약. 같은 키로 재요청 시 기존 주문 반환.

### Payment — TossPayments 2-step

```
준비(/prepare) → [프론트에서 결제창] → 확인(/confirm)
                                              ├─ 성공: Payment DONE, Order CONFIRMED, reserved→allocated
                                              │        Shipment 자동 생성 (PREPARING)
                                              └─ 실패: Payment FAILED

결제 후 취소(/cancel): Payment CANCELLED, Order CANCELLED, allocated 해제
```

#### 멱등성 보장

| 레이어 | 전략 |
|---|---|
| `prepare()` | `payments.order_id` DB UNIQUE 제약 — 동시 요청 시 하나만 저장 |
| `confirm()` / `cancel()` | Redis SETNX로 PROCESSING 상태 원자적 선점, 완료 결과 24h 캐싱 |
| Toss API 호출 | `Idempotency-Key: {tossOrderId}` 헤더 — 네트워크 재시도 시 Toss 측 중복 방지 |

#### Circuit Breaker

TossPayments API 호출에 Resilience4j Circuit Breaker 적용. 연속 실패 시 회로 차단 → 빠른 실패 응답.

### Elasticsearch 상품 검색

`GET /api/products` 쿼리 파라미터로 검색 조건을 조합합니다.

| 파라미터 | 설명 |
|---|---|
| `q` | 키워드 (name · sku · category · description multi_match) |
| `minPrice` / `maxPrice` | 가격 범위 필터 |
| `category` | 카테고리 정확 일치 |
| `sort` | `price_asc` / `price_desc` / `newest` / `relevance` (기본) |

검색 조건이 없으면 MySQL 조회, ES 장애 시 MySQL fallback.

---

## 📌 DB 마이그레이션

| 버전 | 내용 |
|---|---|
| V1 | products |
| V2 | inventory |
| V3 | orders, order_items |
| V4 | payments |
| V5 | users |
| V6 | inventory_transactions |
| V7 | inventory_transactions.note 컬럼 추가 |
| V8 | payments.order_id UNIQUE 제약 추가 |
| V9 | order_status_history |
| V10 | cart_items |
| V11 | shipments |
| V12 | delivery_addresses, orders.delivery_address_id FK 추가 |

---

## 🔑 관리자 페이지

### Spring Boot Admin — 인프라 모니터링

`http://localhost:8080/admin-ui` 접속 후 form login (계정: `admin` / `changeme`).

| 기능 | 설명 |
|---|---|
| 애플리케이션 상태 | Health, 메모리/힙/GC, 스레드 |
| HTTP 트레이스 | 최근 요청/응답 내역 |
| 로그 레벨 | 런타임에서 패키지별 레벨 즉시 변경 |
| 빈 목록 | Spring 컨텍스트의 전체 빈 확인 |
| 환경변수 | application.properties 설정값 조회 |

### Admin REST API

모두 **ADMIN JWT** 인증 필요.

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/admin/dashboard` | 주문 통계, 매출, 사용자 수, 저재고 목록 |
| GET | `/api/admin/users` | 전체 사용자 목록 (페이징) |
| PATCH | `/api/admin/users/{id}/role` | 사용자 권한 변경 (USER ↔ ADMIN) |
| GET | `/api/admin/orders` | 전체 주문 목록 (`?status=` 필터 가능) |

---

## 📊 API 엔드포인트

> Swagger UI에서 직접 테스트 가능: `http://localhost:8080/swagger-ui/index.html`

### 인증 / 사용자

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | 공개 |
| POST | `/api/auth/login` | 로그인 → JWT + Refresh Token | 공개 |
| POST | `/api/auth/logout` | 로그아웃 (JWT 블랙리스트 + Refresh Token revoke) | 공개 |
| POST | `/api/auth/refresh` | Access Token 재발급 (Refresh Token rotation) | 공개 |
| GET | `/api/users/me` | 내 정보 조회 | USER |
| GET | `/api/users/me/orders` | 내 주문 목록 | USER |

### 상품

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/products` | 상품 등록 | ADMIN |
| GET | `/api/products` | 상품 목록 (ES 검색 or MySQL, `?q=&minPrice=&maxPrice=&category=&sort=`) | 공개 |
| GET | `/api/products/{id}` | 상품 단건 조회 | 공개 |
| PUT | `/api/products/{id}` | 상품 수정 | ADMIN |
| DELETE | `/api/products/{id}` | 상품 삭제 (soft delete) | ADMIN |

### 재고

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/inventory` | 재고 목록 (`?status=&productId=` 필터) | USER |
| GET | `/api/inventory/{productId}` | 재고 현황 조회 | USER |
| GET | `/api/inventory/{productId}/transactions` | 재고 변동 이력 (페이징) | USER |
| POST | `/api/inventory/{productId}/receive` | 입고 처리 | ADMIN |
| POST | `/api/inventory/{productId}/adjust` | 재고 조정 | ADMIN |

### 주문

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 (재고 예약, 멱등성) | USER |
| GET | `/api/orders` | 주문 목록 (USER=본인, ADMIN=전체, `?status=&userId=&startDate=&endDate=`) | USER |
| GET | `/api/orders/{id}` | 주문 단건 조회 | USER |
| GET | `/api/orders/{id}/history` | 주문 상태 변경 이력 | USER |
| POST | `/api/orders/{id}/cancel` | 주문 취소 (재고 예약 해제) | USER |

### 결제

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments/prepare` | 결제 준비 | USER |
| POST | `/api/payments/confirm` | 결제 승인 (배송 자동 생성) | USER |
| POST | `/api/payments/{paymentKey}/cancel` | 결제 취소/환불 | USER |
| GET | `/api/payments/{paymentKey}` | 결제 조회 | USER |
| GET | `/api/payments/order/{orderId}` | 주문 ID로 결제 조회 | USER |
| POST | `/api/payments/webhook` | TossPayments 웹훅 | 공개 |

### 장바구니

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/cart` | 장바구니 조회 | USER |
| POST | `/api/cart/items` | 상품 담기 / 수량 변경 | USER |
| DELETE | `/api/cart/items/{productId}` | 상품 제거 | USER |
| DELETE | `/api/cart` | 장바구니 비우기 | USER |
| POST | `/api/cart/checkout` | 장바구니 주문 전환 | USER |

### 배송

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/shipments/orders/{orderId}` | 주문 배송 조회 | USER |
| PATCH | `/api/shipments/{id}/ship` | 배송 시작 (PREPARING → SHIPPED) | ADMIN |
| PATCH | `/api/shipments/{id}/deliver` | 배송 완료 (SHIPPED → DELIVERED) | ADMIN |
| PATCH | `/api/shipments/{id}/return` | 반품 처리 (SHIPPED → RETURNED) | ADMIN |

### 배송지

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/delivery-addresses` | 배송지 등록 | USER |
| GET | `/api/delivery-addresses` | 배송지 목록 | USER |
| GET | `/api/delivery-addresses/{id}` | 배송지 단건 조회 | USER |
| PUT | `/api/delivery-addresses/{id}` | 배송지 수정 | USER |
| DELETE | `/api/delivery-addresses/{id}` | 배송지 삭제 | USER |
| POST | `/api/delivery-addresses/{id}/default` | 기본 배송지 설정 | USER |

---

## 🔧 요청/응답 예시

모든 응답은 `ApiResponse<T>` 래퍼로 반환됩니다.

```json
{ "success": true, "data": { ... } }
{ "success": false, "message": "재고가 부족합니다." }
```

### 로그인

```http
POST /api/auth/login
{ "username": "user123", "password": "password123!" }
```

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "refreshToken": "uuid-..."
  }
}
```

### 상품 검색

```http
GET /api/products?q=노트북&minPrice=500000&maxPrice=2000000&category=전자&sort=price_asc
```

### 입고 처리

```http
POST /api/inventory/1/receive
Authorization: Bearer <token>
{ "quantity": 100, "note": "초도 입고" }
```

```json
{
  "success": true,
  "data": { "productId": 1, "onHand": 100, "reserved": 0, "allocated": 0, "available": 100 }
}
```

### 주문 생성

```http
POST /api/orders
Authorization: Bearer <token>
{
  "userId": 1,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "items": [{ "productId": 1, "quantity": 2, "unitPrice": 1200000 }]
}
```

```json
{
  "success": true,
  "data": { "id": 1, "status": "PENDING", "totalAmount": 2400000, "items": [...] }
}
```

---

## 🔒 보안

| 체인 | 우선순위 | 경로 | 인증 방식 |
|---|---|---|---|
| `AdminSecurityConfig` | @Order(1) | `/admin-ui/**`, `/instances/**` | Form Login + 세션 |
| `SecurityConfig` | @Order(2) | 나머지 전체 | JWT (stateless) |

- 미인증 → 403
- 공개: `/api/auth/**`, `/api/products/**`, `/actuator/health`, `/actuator/info`, `/api/payments/webhook`, `/swagger-ui/**`
- ADMIN 전용: `/actuator/**` (health/info 제외), 상품·재고 write, 배송 상태 변경
- JWT Secret은 반드시 환경 변수(`JWT_SECRET`)로 교체

### 추가 보안 기능

| 기능 | 구현 |
|---|---|
| 로그인 Rate Limiting | Redis 기반, 15분 내 5회 초과 시 429 |
| API Rate Limiting | 주문 생성 10회/분, 결제 확정 5회/분 (AOP, Redis) |
| JWT 블랙리스트 | 로그아웃 시 jti로 Redis에 등록, 토큰 만료까지 유효 |
| Refresh Token Rotation | 발급 시 이전 토큰 자동 revoke (Redis, TTL 30일) |
| Toss 웹훅 서명 검증 | `Toss-Signature` 헤더 HMAC-SHA256 검증 |
| CORS | `cors.allowed-origins` 환경 변수로 허용 도메인 제어 |
| MDC Logging | 요청별 `requestId` 자동 주입 (RequestIdFilter) |

---

## 📝 라이선스

MIT License

---

## 장기 과제

| 항목 | 설명 |
|---|---|
| **이벤트 기반 통신 (Kafka)** | Order → Payment, Payment → Inventory 간 현재 동기 직접 호출 → 비동기 이벤트로 분리 |
| **카테고리 엔티티 분리** | 현재 Product.category가 String — Category 테이블 분리 및 계층 구조 |
| **쿠폰/프로모션 시스템** | 할인 코드, 기간 프로모션, 주문 금액 할인 |
| **배치 처리** | 대량 재고 업데이트, 월별 정산, 판매 통계 집계 |
| **마이크로서비스 분리** | 현재 모놀리식 → 도메인별 서비스 분리 (Service Mesh, API Gateway) |
| **CQRS / Event Sourcing** | 재고 변동 이력을 이벤트 소싱으로 재구성, 읽기/쓰기 모델 분리 |
| **멀티 테넌시** | 복수 쇼핑몰/판매자 지원 |
| **글로벌 배포** | 멀티 리전 DB 복제, CDN 연동 |
