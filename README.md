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
| 인증 | Spring Security 6 + JWT (jjwt 0.12.6) |
| 결제 | TossPayments Core API v1 |
| 테스트 | JUnit 5, Mockito, Testcontainers (MySQL + Redis) |
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

Flyway가 기동 시 V1~V6 마이그레이션을 자동 실행합니다.

### 환경 변수 (선택)

```bash
export JWT_SECRET=your-secret-key-at-least-32-characters-long
export TOSS_SECRET_KEY=test_sk_your_actual_key
export TOSS_CLIENT_KEY=test_ck_your_actual_key
```

---

## 🧪 테스트

```bash
# 전체 테스트 (Docker 필요 — Testcontainers가 MySQL·Redis 컨테이너를 자동으로 띄움)
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
| 통합 | `integration/` | Testcontainers MySQL+Redis, Flyway 실행 |
| 동시성 | `integration/InventoryConcurrencyTest` | 동시 입고 lost update · 예약 overselling 검증 |

---

## 📁 프로젝트 구조

```
com.stockmanagement/
├── common/
│   ├── config/          # SecurityConfig, JpaConfig, RedisConfig, CacheConfig, TossPaymentsConfig
│   ├── dto/             # ApiResponse<T> — 전 엔드포인트 통합 응답 래퍼
│   ├── exception/       # BusinessException, InsufficientStockException, ErrorCode, GlobalExceptionHandler
│   └── lock/            # @DistributedLock (어노테이션), DistributedLockAspect (AOP)
├── domain/
│   ├── product/         # 상품 CRUD, ACTIVE/DISCONTINUED 상태
│   ├── inventory/       # 재고 4-state 모델 + 변동 이력(InventoryTransaction)
│   ├── order/           # 주문 생성·취소, 멱등성 키
│   ├── payment/         # TossPayments 연동, 결제 준비·확인·취소
│   └── user/            # 회원가입·로그인, ADMIN/USER 역할
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
                                              └─ 실패: Payment FAILED

결제 후 취소(/cancel): Payment CANCELLED, Order CANCELLED, allocated 해제
```

---

## 📌 DB 마이그레이션

| 버전 | 테이블 |
|---|---|
| V1 | products |
| V2 | inventory |
| V3 | orders, order_items |
| V4 | payments |
| V5 | users |
| V6 | inventory_transactions |

---

## 📊 API 엔드포인트

### 인증

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | 공개 |
| POST | `/api/auth/login` | 로그인 → JWT | 공개 |
| GET | `/api/users/me` | 내 정보 조회 | USER |

### 상품

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/products` | 상품 등록 | ADMIN |
| GET | `/api/products` | 상품 목록 (페이징) | USER |
| GET | `/api/products/{id}` | 상품 단건 조회 | USER |
| PUT | `/api/products/{id}` | 상품 수정 | ADMIN |
| DELETE | `/api/products/{id}` | 상품 삭제 (soft delete) | ADMIN |

### 재고

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/inventory/{productId}` | 재고 현황 조회 | USER |
| GET | `/api/inventory/{productId}/transactions` | 재고 변동 이력 (최신순) | USER |
| POST | `/api/inventory/{productId}/receive` | 입고 처리 | ADMIN |

### 주문

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/orders` | 주문 생성 (재고 예약, 멱등성) | USER |
| GET | `/api/orders/{id}` | 주문 단건 조회 | USER |
| POST | `/api/orders/{id}/cancel` | 주문 취소 (재고 예약 해제) | USER |

### 결제

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/payments/prepare` | 결제 준비 | USER |
| POST | `/api/payments/confirm` | 결제 승인 | USER |
| POST | `/api/payments/{paymentKey}/cancel` | 결제 취소/환불 | USER |
| GET | `/api/payments/{paymentKey}` | 결제 조회 | USER |
| POST | `/api/payments/webhook` | TossPayments 웹훅 | 공개 |

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
  "data": { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 86400 }
}
```

### 입고 처리

```http
POST /api/inventory/1/receive
Authorization: Bearer <token>
{ "quantity": 100 }
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

- 미인증 요청 → 403 (Spring Security 6 기본)
- ADMIN 전용: `POST/PUT/DELETE /api/products/**`, `POST /api/inventory/**`
- 공개: `/api/auth/**`, `/actuator/**`, `/api/payments/webhook`
- JWT Secret은 반드시 환경 변수(`JWT_SECRET`)로 교체 (기본값은 개발용)

---

## 📝 라이선스

MIT License
