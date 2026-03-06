# stock-management-api
재고관리 시스템 구현
Spring Boot + MySQL

쇼핑몰 환경을 가정하여 **재고 → 주문 → 결제 → 유저 관리** 순으로 확장하며, 추후 Redis, Docker를 통한 운영 환경까지 포함할 예정입니다.

---

## 🎯 프로젝트 목표
- 안정적인 **재고 관리 API** 구현 (재고 예약, 출고, 롤백)
- 쇼핑몰 워크플로우(주문/결제/회원) 반영
- **동시성 제어** 및 **재고 초과 판매 방지**
- Docker, Redis 등 운영 환경 반영
- 확장 가능한 아키텍처 설계

---

## 🗂 주요 도메인

### 1. **재고(Inventory)**
- 상품 SKU, 창고 단위 관리
- `onHand`, `reserved`, `allocated` 수량 관리
- 예약/출고/취소 트랜잭션 보장

### 2. **주문(Order)**
- 주문 생성 시 재고 예약
- 결제 대기/성공/실패 상태 전이
- 중복 주문 방지 (idempotency key)

### 3. **결제(Payment)** ✅
- TossPayments Core API v1 연동 (준비 → 승인 → 취소/환불)
- 금액 이중 검증으로 변조 방지
- 재고 확정(allocated) 또는 할당 해제(releaseAllocation)

### 4. **유저(User)** ✅
- 회원가입/로그인 (BCrypt + JWT 인증)
- 내 정보 조회, 내 주문 목록 조회

---

## ⚙️ 기술 스택

### Backend
- **Framework**: Spring Boot 3.5.11
- **Language**: Java 17
- **Build Tool**: Gradle 8.x

### Database & Cache
- **Database**: MySQL 8.x (InnoDB)
- **ORM**: Spring Data JPA (Hibernate)
- **Migration**: Flyway
- **Cache/Queue**: Redis (예정)

### Security & Auth
- **Security**: Spring Security 6.x
- **Auth**: JWT / jjwt 0.12.6

### Container & DevOps
- **Container**: Docker, Docker Compose (예정)
- **Monitoring**: Spring Actuator + Prometheus (예정)

### Testing
- **Unit Test**: JUnit 5, Mockito
- **Integration Test**: Testcontainers / MySQL, Redis (예정)

---

## 📁 프로젝트 구조

```
stock-management-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/stockmanagement/
│   │   │       ├── StockManagementApiApplication.java
│   │   │       ├── common/
│   │   │       │   ├── dto/
│   │   │       │   │   └── ApiResponse.java              # 통합 응답 래퍼 { success, data, message }
│   │   │       │   └── exception/
│   │   │       │       ├── ErrorCode.java                # HTTP 상태 + 메시지 enum
│   │   │       │       ├── BusinessException.java        # 커스텀 예외 기반 클래스
│   │   │       │       ├── InsufficientStockException.java
│   │   │       │       └── GlobalExceptionHandler.java   # @RestControllerAdvice
│   │   │       └── domain/
│   │   │           ├── product/                          # ✅ 구현 완료
│   │   │           │   ├── entity/
│   │   │           │   │   ├── Product.java
│   │   │           │   │   └── ProductStatus.java        # ACTIVE / INACTIVE / DISCONTINUED
│   │   │           │   ├── repository/ProductRepository.java
│   │   │           │   ├── service/ProductService.java
│   │   │           │   ├── controller/ProductController.java
│   │   │           │   └── dto/
│   │   │           │       ├── ProductCreateRequest.java
│   │   │           │       ├── ProductUpdateRequest.java
│   │   │           │       └── ProductResponse.java
│   │   │           ├── inventory/                        # ✅ 구현 완료
│   │   │           │   ├── entity/Inventory.java         # @Version 낙관적 락
│   │   │           │   ├── repository/InventoryRepository.java  # 비관적 락 쿼리 포함
│   │   │           │   ├── service/InventoryService.java
│   │   │           │   ├── controller/InventoryController.java
│   │   │           │   └── dto/
│   │   │           │       ├── InventoryReceiveRequest.java
│   │   │           │       └── InventoryResponse.java
│   │   │           ├── order/                            # ✅ 구현 완료 (2단계)
│   │   │           │   ├── entity/
│   │   │           │   │   ├── Order.java                # @OneToMany OrderItems, idempotencyKey
│   │   │           │   │   ├── OrderItem.java            # unitPrice 보존, subtotal 계산 저장
│   │   │           │   │   └── OrderStatus.java          # PENDING / CONFIRMED / CANCELLED
│   │   │           │   ├── repository/
│   │   │           │   │   ├── OrderRepository.java      # findByIdempotencyKey, fetch join
│   │   │           │   │   └── OrderItemRepository.java
│   │   │           │   ├── service/OrderService.java
│   │   │           │   ├── controller/OrderController.java
│   │   │           │   └── dto/
│   │   │           │       ├── OrderCreateRequest.java
│   │   │           │       ├── OrderItemRequest.java
│   │   │           │       ├── OrderResponse.java
│   │   │           │       └── OrderItemResponse.java
│   │   │           ├── payment/                          # ✅ 구현 완료
│   │   │           │   ├── entity/
│   │   │           │   │   ├── Payment.java
│   │   │           │   │   └── PaymentStatus.java        # PENDING/DONE/CANCELLED/FAILED
│   │   │           │   ├── repository/PaymentRepository.java
│   │   │           │   ├── service/PaymentService.java
│   │   │           │   ├── controller/PaymentController.java
│   │   │           │   ├── dto/
│   │   │           │   │   ├── PaymentPrepareRequest/Response.java
│   │   │           │   │   ├── PaymentConfirmRequest.java
│   │   │           │   │   ├── PaymentCancelRequest.java
│   │   │           │   │   └── PaymentResponse.java
│   │   │           │   └── infrastructure/               # TossPayments 외부 연동
│   │   │           │       ├── TossPaymentsClient.java
│   │   │           │       └── dto/                      # Toss API 전용 DTO
│   │   │           └── user/                             # ✅ 구현 완료
│   │   │               ├── entity/
│   │   │               │   ├── User.java
│   │   │               │   └── UserRole.java             # USER / ADMIN
│   │   │               ├── repository/UserRepository.java
│   │   │               ├── service/UserService.java
│   │   │               ├── controller/
│   │   │               │   ├── AuthController.java       # /api/auth/**
│   │   │               │   └── UserController.java       # /api/users/**
│   │   │               └── dto/
│   │   │                   ├── SignupRequest.java
│   │   │                   ├── LoginRequest.java
│   │   │                   ├── LoginResponse.java
│   │   │                   └── UserResponse.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql               # ✅ products 테이블
│   │           ├── V2__create_inventory_tables.sql   # ✅ inventory 테이블
│   │           ├── V3__create_order_tables.sql       # ✅ orders, order_items 테이블
│   │           ├── V4__create_payment_tables.sql     # ✅ payments 테이블
│   │           └── V5__create_user_tables.sql        # ✅ users 테이블
│   └── test/
│       └── java/com/stockmanagement/
│           └── StockManagementApiApplicationTests.java
├── build.gradle
└── README.md
```

---

## 📌 개발 단계별 로드맵

### 1단계: Product & Inventory 기반 구현 ✅
**목표**: Spring Boot 프로젝트 초기 설정 및 상품/재고 도메인 구현

#### 작업 내용
- [x] Spring Initializer로 프로젝트 생성
  - Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Lombok, Validation, Flyway
- [x] MySQL 연동 및 Flyway 설정
- [x] 공통 인프라 구현
  - `ApiResponse<T>` 통합 응답 래퍼
  - `BusinessException` / `InsufficientStockException` 커스텀 예외
  - `ErrorCode` enum (HTTP 상태 + 메시지)
  - `GlobalExceptionHandler` (`@RestControllerAdvice`)
- [x] Product 도메인 구현
  - `Product` Entity (Builder 패턴, 소프트 삭제)
  - `ProductStatus` Enum (ACTIVE / INACTIVE / DISCONTINUED)
  - 상품 CRUD API
- [x] Inventory 도메인 구현
  - `Inventory` Entity (`@Version` 낙관적 락)
  - 재고 조회 + 입고(receive) API
  - 비관적 락(`PESSIMISTIC_WRITE`) 쿼리
  - 내부 전용 메서드: `reserve` / `releaseReservation` / `confirmAllocation`

#### 주요 파일
```
src/main/resources/db/migration/
├── V1__init_schema.sql               # products 테이블
└── V2__create_inventory_tables.sql   # inventory 테이블

src/main/java/.../domain/
├── product/
│   ├── entity/Product.java, ProductStatus.java
│   ├── repository/ProductRepository.java
│   ├── service/ProductService.java
│   ├── controller/ProductController.java
│   └── dto/ProductCreateRequest, ProductUpdateRequest, ProductResponse
└── inventory/
    ├── entity/Inventory.java
    ├── repository/InventoryRepository.java
    ├── service/InventoryService.java
    ├── controller/InventoryController.java
    └── dto/InventoryReceiveRequest, InventoryResponse
```

#### API 엔드포인트
```
# 상품
POST   /api/products          상품 등록 (201)
GET    /api/products          상품 목록 조회 - 페이징 (200)
GET    /api/products/{id}     상품 단건 조회 (200)
PUT    /api/products/{id}     상품 수정 (200)
DELETE /api/products/{id}     상품 삭제 - soft delete → DISCONTINUED (204)

# 재고
GET    /api/inventory/{productId}          재고 현황 조회 (200)
POST   /api/inventory/{productId}/receive  입고 처리 (200)
```

#### 재고 수량 모델
```
available = onHand - reserved - allocated

onHand    : 창고 실물 재고
reserved  : 주문 생성 후 결제 대기 중인 재고
allocated : 결제 완료 후 출고 확정된 재고
available : 현재 주문 가능한 수량 (계산값, DB 미저장)
```

#### 동시성 제어
| 전략 | 적용 위치 | 설명 |
|------|-----------|------|
| 낙관적 락 (`@Version`) | `Inventory` 엔티티 | 동시 수정 충돌 감지 |
| 비관적 락 (`PESSIMISTIC_WRITE`) | `InventoryRepository` | 입고/예약 시 행 잠금 |

---

### 2단계: 주문 & 재고 예약 ✅
**목표**: 주문 생성 시 재고 예약 및 상태 관리 구현

#### 작업 내용
- [x] 주문 도메인 구현
  - `Order` Entity (userId Long, totalAmount, idempotencyKey, @OneToMany items)
  - `OrderItem` Entity (unitPrice 주문 당시 단가 보존, subtotal 계산 저장)
  - `OrderStatus` Enum (PENDING, CONFIRMED, CANCELLED)
- [x] 재고 예약 연동
  - 주문 생성 시 각 항목에 대해 `InventoryService.reserve()` 호출 → `reserved` 수량 증가
  - 주문 취소 시 `InventoryService.releaseReservation()` 호출 → `reserved` 수량 감소
  - 결제 완료 시 `InventoryService.confirmAllocation()` 호출 → `reserved` → `allocated` 이전
- [x] 멱등성 보장
  - `idempotencyKey` DB UNIQUE 제약
  - 중복 요청 시 기존 주문을 그대로 반환 (새 주문 미생성)
- [x] 단가 검증
  - 요청의 `unitPrice`를 `Product.price`와 비교 검증
  - 불일치 시 400 에러 반환

#### 주요 파일
```
src/main/resources/db/migration/
└── V3__create_order_tables.sql        # orders, order_items 테이블

src/main/java/.../domain/order/
├── entity/Order.java, OrderItem.java, OrderStatus.java
├── repository/OrderRepository.java    # findByIdempotencyKey, findByIdWithItems (fetch join)
├── repository/OrderItemRepository.java
├── service/OrderService.java          # InventoryService 의존
├── controller/OrderController.java
└── dto/OrderCreateRequest, OrderItemRequest, OrderResponse, OrderItemResponse
```

#### API 엔드포인트
```
POST   /api/orders              주문 생성 (재고 예약)       → 201
GET    /api/orders/{id}         주문 단건 조회 (items 포함) → 200
GET    /api/orders              주문 목록 조회 (페이징)     → 200
POST   /api/orders/{id}/cancel  주문 취소 (재고 예약 해제)  → 200
```

#### 재고 상태 전이
```
주문 생성 → reserved += quantity
주문 취소 → reserved -= quantity
결제 완료 → reserved -= quantity, allocated += quantity
```

---

### 3단계: 결제 연동 ✅
**목표**: TossPayments Core API v1 연동 및 재고 상태 전이 관리

#### 작업 내용
- [x] 결제 도메인 구현
  - `Payment` Entity (`tossOrderId` UNIQUE, `paymentKey` null until approved)
  - `PaymentStatus` Enum (PENDING, DONE, CANCELLED, FAILED, PARTIAL_CANCELLED)
- [x] 결제 준비 API (`/prepare`)
  - 금액 사전 검증 (서버의 `Order.totalAmount`와 비교)
  - PENDING Payment 레코드 생성, `tossOrderId` 반환
- [x] 결제 승인 API (`/confirm`)
  - 금액 이중 검증 (클라이언트 변조 방지)
  - TossPayments `/v1/payments/confirm` 호출
  - 성공: Payment DONE, Order CONFIRMED, `reserved → allocated`
  - 실패: Payment FAILED
- [x] 결제 취소/환불 API (`/{paymentKey}/cancel`)
  - TossPayments 취소 API 호출 (전액/부분 취소)
  - Payment CANCELLED, Order CANCELLED, `allocated` 해제
- [x] 웹훅 수신 (`/webhook`)
  - `PAYMENT_STATUS_CHANGED` 이벤트 처리
  - 10초 내 200 응답 (TossPayments 재전송 정책)
- [x] 멱등성 처리
  - prepare: 동일 orderId 재요청 시 기존 PENDING Payment 반환
  - confirm/cancel: 이미 처리된 상태면 기존 결과 반환
- [x] TossPayments 설정 (`TossPaymentsConfig`)
  - `RestClient` Bean (Base64 Basic Auth 헤더 사전 주입)
  - `toss.*` properties (환경 변수 오버라이드 지원)

#### 결제 프로세스 흐름

```
[주문 생성] → Inventory: reserved +N
    ↓
[POST /api/payments/prepare]
→ Payment(PENDING) 생성, tossOrderId 반환
    ↓
[TossPayments 결제창 (클라이언트)]
→ paymentKey 발급
    ↓
[POST /api/payments/confirm]
    ↓
    ├─ [성공] → Payment(DONE)
    │           Order(CONFIRMED)
    │           Inventory: reserved-N, allocated+N
    │
    └─ [실패] → Payment(FAILED)
                Order은 PENDING 유지 (재시도 가능)

[POST /api/payments/{paymentKey}/cancel]
→ Payment(CANCELLED)
   Order(CANCELLED)
   Inventory: allocated-N
```

#### 취소 시 재고 처리 구분

| 취소 시점 | 메서드 | Order 전이 | 재고 처리 |
|---|---|---|---|
| 결제 전 (`/orders/{id}/cancel`) | `OrderService.cancel()` | PENDING → CANCELLED | `reserved` 감소 |
| 결제 후 (`/payments/{key}/cancel`) | `OrderService.refund()` | CONFIRMED → CANCELLED | `allocated` 감소 |

#### 주요 파일
```
src/main/resources/db/migration/
└── V4__create_payment_tables.sql      # payments 테이블

src/main/java/.../
├── common/config/TossPaymentsConfig.java
└── domain/payment/
    ├── entity/Payment.java, PaymentStatus.java
    ├── repository/PaymentRepository.java
    ├── service/PaymentService.java
    ├── controller/PaymentController.java
    ├── dto/PaymentPrepareRequest/Response, PaymentConfirmRequest,
    │   PaymentCancelRequest, PaymentResponse
    └── infrastructure/TossPaymentsClient.java
        └── dto/TossConfirmRequest/Response, TossCancelRequest, TossWebhookEvent
```

#### API 엔드포인트
```
POST /api/payments/prepare              # 결제 준비 (결제창 초기화 전)
POST /api/payments/confirm              # 결제 승인 (결제창 완료 후)
POST /api/payments/{paymentKey}/cancel  # 결제 취소/환불
POST /api/payments/webhook              # TossPayments 웹훅 수신 (public)
GET  /api/payments/{paymentKey}         # 결제 조회
```

---

### 4단계: 사용자 관리 ✅
**목표**: JWT 기반 인증/인가 시스템 구현

#### 작업 내용
- [x] User 도메인 구현
  - `User` Entity (username, password, email, role)
  - `UserRole` Enum (USER, ADMIN)
- [x] Spring Security + JWT 인증
  - `/api/auth/signup`, `/api/auth/login` 엔드포인트
  - `JwtTokenProvider`: jjwt 0.12.6으로 토큰 생성/검증
  - `JwtAuthenticationFilter`: `Authorization: Bearer <token>` 헤더 파싱
  - `SecurityConfig`: Stateless, CSRF 비활성화
- [x] 사용자별 주문 목록 조회
  - JWT에서 username 추출 → userId 조회 → 주문 목록 반환

#### 인가 규칙

| 엔드포인트 | 허용 |
|---|---|
| `POST /api/auth/**` | 모두 허용 (인증 불필요) |
| `GET /actuator/**` | 모두 허용 |
| `POST /api/payments/webhook` | 모두 허용 |
| `POST/PUT/DELETE /api/products/**` | ADMIN 전용 |
| `POST /api/inventory/**` | ADMIN 전용 |
| 나머지 전체 | 인증 필요 |

#### 주요 파일
```
src/main/java/.../
├── security/
│   ├── JwtTokenProvider.java         # 토큰 생성/검증 (jjwt 0.12.6)
│   └── JwtAuthenticationFilter.java  # Bearer 토큰 추출 및 SecurityContext 설정
├── common/config/
│   └── SecurityConfig.java           # FilterChain, PasswordEncoder Bean
└── domain/user/
    ├── entity/User.java, UserRole.java
    ├── repository/UserRepository.java
    ├── service/UserService.java       # signup, login, getMe, getMyOrders
    ├── controller/AuthController.java # /api/auth/**
    ├── controller/UserController.java # /api/users/**
    └── dto/SignupRequest, LoginRequest, LoginResponse, UserResponse
```

#### API 엔드포인트
```
POST /api/auth/signup       # 회원가입 → 201
POST /api/auth/login        # 로그인 (JWT 발급) → 200
GET  /api/users/me          # 내 정보 조회 → 200
GET  /api/users/me/orders   # 내 주문 목록 (페이징) → 200
```

---

### 5단계: 운영 환경 확장 🐳
**목표**: Docker, Redis 통합 및 운영 환경 구축

#### 작업 내용
- [ ] **Docker & Docker Compose**
  - Dockerfile 작성 (Multi-stage build)
  - docker-compose.yml (MySQL, Redis, Spring Boot)
  - 환경별 설정 분리 (dev, prod)

- [ ] **Redis 통합**
  - Spring Data Redis 설정
  - 재고 예약 TTL 관리
    - 예약 후 30분 내 미결제 시 자동 해제
  - 분산 락 구현 (Redisson)

- [ ] **백그라운드 작업**
  - `@Scheduled`로 예약 만료 체크
  - 만료된 예약 자동 해제
  - 일일 재고 정산 배치

- [ ] **모니터링**
  - Spring Actuator 활성화
  - Prometheus + Grafana 연동
  - 헬스 체크 엔드포인트

#### Redis 재고 예약 TTL

```java
@Service
public class InventoryReservationService {

    private final StringRedisTemplate redisTemplate;
    private static final String RESERVATION_KEY = "inventory:reservation:";
    private static final long RESERVATION_TTL_MINUTES = 30;

    public void reserveWithTTL(Long productId, Long warehouseId, int quantity, String orderId) {
        // 1. 실제 재고 예약
        inventoryService.reserveStock(productId, warehouseId, quantity, orderId);

        // 2. Redis에 예약 정보 저장 (30분 TTL)
        String key = RESERVATION_KEY + orderId;
        ReservationInfo info = new ReservationInfo(productId, warehouseId, quantity, orderId);

        redisTemplate.opsForValue().set(
            key,
            JsonUtil.toJson(info),
            RESERVATION_TTL_MINUTES,
            TimeUnit.MINUTES
        );
    }

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void releaseExpiredReservations() {
        // 만료된 예약 감지 및 자동 해제
        // Redis Keyspace Notification 활용
    }
}
```

#### Docker Compose 예시
```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: stock_management
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  app:
    build: .
    depends_on:
      - mysql
      - redis
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: mysql
      REDIS_HOST: redis
    ports:
      - "8080:8080"

volumes:
  mysql_data:
```

---

### 6단계: 성능 최적화 및 고도화 ⚡
**목표**: 시스템 성능 개선 및 안정성 강화

#### 작업 내용
- [ ] **캐싱 전략**
  - 상품 정보 Redis 캐싱
  - 재고 조회 성능 최적화
  - Cache-Aside 패턴 적용

- [ ] **배치 처리**
  - Spring Batch로 대량 재고 업데이트
  - 일일 재고 정산 작업
  - 재고 트랜잭션 이력 아카이빙

- [ ] **API 성능 개선**
  - N+1 문제 해결 (Fetch Join, @EntityGraph)
  - 쿼리 최적화
  - 인덱스 튜닝
  - 페이징 성능 개선

- [ ] **로깅 및 모니터링**
  - 슬로우 쿼리 모니터링
  - API 응답 시간 추적
  - 에러 로그 집계

#### 성능 최적화 예시

**N+1 문제 해결**
```java
// ❌ N+1 문제 발생
@Query("SELECT o FROM Order o WHERE o.userId = :userId")
List<Order> findByUserId(@Param("userId") Long userId);
// → orderItems 조회 시 매번 쿼리 발생

// ✅ Fetch Join으로 해결
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.orderItems oi " +
       "WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
// → 한 번의 쿼리로 조회
```

**Redis 캐싱**
```java
@Service
public class ProductService {

    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException());
    }

    @CacheEvict(value = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }
}
```

**데이터베이스 인덱스**
```sql
-- 재고 조회 최적화
CREATE INDEX idx_inventory_product_warehouse ON inventory(product_id, warehouse_id);
CREATE INDEX idx_inventory_sku ON inventory(sku);

-- 주문 조회 최적화
CREATE INDEX idx_order_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_order_status ON orders(status);

-- 결제 조회 최적화
CREATE INDEX idx_payment_order ON payments(order_id);
```

---

## 📊 데이터베이스 스키마

### ERD (Entity Relationship Diagram)

현재 구현된 테이블 (✅), 향후 추가 예정 테이블 (🔄)

```
✅ 구현 완료
┌─────────────┐       ┌──────────────┐
│  products   │       │  inventory   │
├─────────────┤       ├──────────────┤
│ id (PK)     │◄──────│ product_id   │
│ name        │       │ on_hand      │
│ description │       │ reserved     │
│ price       │       │ allocated    │
│ sku (UNIQUE)│       │ version      │  ← 낙관적 락
│ category    │       │ created_at   │
│ status      │       │ updated_at   │
│ created_at  │       └──────────────┘
│ updated_at  │              ▲
└──────┬──────┘              │
       │       ┌─────────────┘
       │       │
       │  ┌────┴─────────┐       ┌──────────────────┐
       │  │   orders     │◄──────│   order_items    │
       │  ├──────────────┤       ├──────────────────┤
       │  │ id (PK)      │       │ id (PK)          │
       │  │ user_id      │       │ order_id (FK)    │
       │  │ status       │       │ product_id (FK)  │◄──┘
       │  │ total_amount │       │ quantity         │
       │  │ idempotency_ │       │ unit_price       │  ← 주문 당시 단가 보존
       │  │ key (UNIQUE) │       │ subtotal         │
       │  │ created_at   │       │ created_at       │
       │  │ updated_at   │       └──────────────────┘
       │  └──────────────┘
       └─────────────────────────────────┘

✅ 구현 완료 (3단계)
                      ┌──────────────────────┐
                      │       payments       │
                      ├──────────────────────┤
                      │ id (PK)              │
                      │ order_id (FK)        │
                      │ payment_key          │  ← Toss 부여 (승인 후 확정)
                      │ toss_order_id (UNIQ) │  ← 우리가 Toss에 전달한 orderId
                      │ amount               │
                      │ status               │  ← PENDING/DONE/CANCELLED/FAILED
                      │ method               │  ← 카드, 가상계좌 등
                      │ requested_at         │
                      │ approved_at          │
                      │ cancel_reason        │
                      │ failure_code         │
                      │ failure_message      │
                      │ created_at           │
                      │ updated_at           │
                      └──────────────────────┘

✅ 구현 완료 (4단계)
┌─────────────┐
│   users     │
├─────────────┤
│ id (PK)     │
│ username    │  ← UNIQUE
│ password    │  ← BCrypt 암호화
│ email       │  ← UNIQUE
│ role        │  ← USER / ADMIN
│ created_at  │
│ updated_at  │
└─────────────┘
       ↑
       │ orders.user_id FK (V5 마이그레이션에서 추가)
```

### Flyway 마이그레이션 파일

**V1__init_schema.sql** ✅
```sql
CREATE TABLE products
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       DECIMAL(12, 2) NOT NULL,
    sku         VARCHAR(100)   NOT NULL,
    category    VARCHAR(100),
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_sku (sku)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

**V2__create_inventory_tables.sql** ✅
```sql
CREATE TABLE inventory
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL,
    on_hand    INT         NOT NULL DEFAULT 0,
    reserved   INT         NOT NULL DEFAULT 0,
    allocated  INT         NOT NULL DEFAULT 0,
    version    INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_product (product_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

**V3__create_order_tables.sql** ✅
```sql
CREATE TABLE orders
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    total_amount    DECIMAL(12, 2) NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_idempotency_key (idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_items
(
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    subtotal   DECIMAL(12, 2) NOT NULL,
    created_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

**V5__create_user_tables.sql** ✅
```sql
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    email      VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
);

ALTER TABLE orders ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id);
```

**V4__create_payment_tables.sql** ✅
```sql
CREATE TABLE payments
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    order_id        BIGINT         NOT NULL,
    payment_key     VARCHAR(200),
    toss_order_id   VARCHAR(64)    NOT NULL UNIQUE,
    amount          DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    method          VARCHAR(50),
    requested_at    DATETIME(6),
    approved_at     DATETIME(6),
    cancel_reason   VARCHAR(200),
    failure_code    VARCHAR(50),
    failure_message VARCHAR(200),
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_toss_order_id (toss_order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
```

---

## 📊 API 엔드포인트

### 인증 (Authentication) ✅

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/signup` | 회원가입 | No |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | No |
| GET | `/api/users/me` | 내 정보 조회 | Yes |
| GET | `/api/users/me/orders` | 내 주문 목록 | Yes |

### 상품 관리 (Products) ✅

| Method | Endpoint | Description | 응답 |
|--------|----------|-------------|------|
| POST | `/api/products` | 상품 등록 | 201 |
| GET | `/api/products` | 상품 목록 조회 (페이징) | 200 |
| GET | `/api/products/{id}` | 상품 단건 조회 | 200 |
| PUT | `/api/products/{id}` | 상품 수정 | 200 |
| DELETE | `/api/products/{id}` | 상품 삭제 (soft delete) | 204 |

### 재고 관리 (Inventory) ✅

| Method | Endpoint | Description | 응답 |
|--------|----------|-------------|------|
| GET | `/api/inventory/{productId}` | 재고 현황 조회 | 200 |
| POST | `/api/inventory/{productId}/receive` | 입고 처리 | 200 |

> `reserve` / `releaseReservation` / `confirmAllocation` 은 Order·Payment 연동 시 내부 호출 예정.

### 주문 관리 (Orders) ✅

| Method | Endpoint | Description | 응답 |
|--------|----------|-------------|------|
| POST | `/api/orders` | 주문 생성 (재고 예약, 멱등성 지원) | 201 |
| GET | `/api/orders/{id}` | 주문 단건 조회 (items 포함) | 200 |
| GET | `/api/orders` | 주문 목록 조회 (페이징) | 200 |
| POST | `/api/orders/{id}/cancel` | 주문 취소 (재고 예약 해제) | 200 |

> `confirm`은 Payment 도메인에서 내부 호출 예정 — 외부 API 미노출.

### 결제 (Payments) ✅

| Method | Endpoint | Description | 응답 |
|--------|----------|-------------|------|
| POST | `/api/payments/prepare` | 결제 준비 (결제창 초기화 전 사전 검증) | 200 |
| POST | `/api/payments/confirm` | 결제 승인 (결제창 완료 후 서버 확정) | 200 |
| POST | `/api/payments/{paymentKey}/cancel` | 결제 취소/환불 (전액·부분) | 200 |
| POST | `/api/payments/webhook` | TossPayments 웹훅 수신 (인증 불필요) | 200 |
| GET | `/api/payments/{paymentKey}` | 결제 상세 조회 | 200 |

---

## 🔧 API 요청/응답 예시

### 1. 회원가입 ✅
```http
POST /api/auth/signup
Content-Type: application/json

{
  "username": "user123",
  "password": "password123!",
  "email": "user@example.com"
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "user123",
    "email": "user@example.com",
    "createdAt": "2026-01-31T10:00:00"
  }
}
```

### 2. 로그인 ✅
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "user123",
  "password": "password123!"
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

### 3. 상품 등록 ✅
```http
POST /api/products
Content-Type: application/json

{
  "name": "스마트폰 Galaxy S24",
  "sku": "PHONE-GS24-128-BLK",
  "category": "Electronics",
  "price": 1200000,
  "description": "최신 갤럭시 S24 스마트폰"
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "스마트폰 Galaxy S24",
    "sku": "PHONE-GS24-128-BLK",
    "category": "Electronics",
    "price": 1200000.00,
    "description": "최신 갤럭시 S24 스마트폰",
    "status": "ACTIVE",
    "createdAt": "2026-03-05T10:00:00",
    "updatedAt": "2026-03-05T10:00:00"
  }
}
```

### 4. 입고 처리 ✅
```http
POST /api/inventory/1/receive
Content-Type: application/json

{
  "quantity": 100,
  "note": "3월 1차 입고"
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "productId": 1,
    "onHand": 100,
    "reserved": 0,
    "allocated": 0,
    "available": 100,
    "updatedAt": "2026-03-05T10:05:00"
  }
}
```

### 5. 재고 조회 ✅
```http
GET /api/inventory/1
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "productId": 1,
    "onHand": 100,
    "reserved": 10,
    "allocated": 5,
    "available": 85,
    "updatedAt": "2026-03-05T10:10:00"
  }
}
```

### 6. 주문 생성 ✅
```http
POST /api/orders
Content-Type: application/json

{
  "userId": 1,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "unitPrice": 1200000.00
    }
  ]
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1,
    "status": "PENDING",
    "totalAmount": 2400000.00,
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "id": 1,
        "productId": 1,
        "productName": "스마트폰 Galaxy S24",
        "quantity": 2,
        "unitPrice": 1200000.00,
        "subtotal": 2400000.00
      }
    ],
    "createdAt": "2026-03-05T10:30:00",
    "updatedAt": "2026-03-05T10:30:00"
  }
}
```

### 6-1. 주문 취소 ✅
```http
POST /api/orders/1/cancel
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1,
    "status": "CANCELLED",
    "totalAmount": 2400000.00,
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "items": [...],
    "createdAt": "2026-03-05T10:30:00",
    "updatedAt": "2026-03-05T10:35:00"
  }
}
```

### 7. 결제 준비 ✅
```http
POST /api/payments/prepare
Content-Type: application/json

{
  "orderId": 1,
  "amount": 2400000
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "tossOrderId": "order-1-a1b2c3d4",
    "amount": 2400000.00,
    "orderName": "스마트폰 Galaxy S24 외 1건"
  }
}
```

> 클라이언트는 `tossOrderId`와 `amount`를 TossPayments 결제창에 전달합니다.

### 8. 결제 승인 ✅
```http
POST /api/payments/confirm
Content-Type: application/json

{
  "paymentKey": "tviva20240101abc...",
  "tossOrderId": "order-1-a1b2c3d4",
  "amount": 2400000
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "orderId": 1,
    "paymentKey": "tviva20240101abc...",
    "tossOrderId": "order-1-a1b2c3d4",
    "amount": 2400000.00,
    "status": "DONE",
    "method": "카드",
    "requestedAt": "2026-03-06T10:00:00",
    "approvedAt": "2026-03-06T10:00:01"
  }
}
```

### 오류 응답 형식
```json
{
  "success": false,
  "message": "재고가 부족합니다. (요청: 20, 가용: 5)"
}
```

---

## 🚀 실행 방법

### 사전 요구사항
- JDK 17 이상
- Docker

### 로컬 환경 실행

#### 1. MySQL 실행
```bash
docker run -d --name stock-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=stock_management \
  -p 3306:3306 \
  mysql:8
```

#### 2. 애플리케이션 실행
```bash
# Gradle Wrapper 사용
./gradlew bootRun

# 또는 JAR 빌드 후 실행
./gradlew clean build
java -jar build/libs/stock-management-api-0.0.1-SNAPSHOT.jar
```

Flyway가 기동 시 V1~V5 마이그레이션을 자동 실행합니다.

#### 3. JWT Secret 설정 (선택, 기본값은 개발용 placeholder)
```bash
export JWT_SECRET=your-secret-key-at-least-32-characters-long
```

#### 4. TossPayments 키 설정 (결제 기능 사용 시)
```bash
export TOSS_SECRET_KEY=test_sk_your_actual_key
export TOSS_CLIENT_KEY=test_ck_your_actual_key
```
테스트 키는 [TossPayments 개발자센터](https://developers.tosspayments.com)에서 발급받습니다.

### Docker Compose로 전체 스택 실행 🔄 예정 (5단계)

```bash
# 전체 서비스 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f app

# 서비스 중지
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v
```

---

## 🧪 테스트

### 단위 테스트
```bash
./gradlew test
```

### 통합 테스트
```bash
./gradlew integrationTest
```

### 테스트 커버리지
```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## 🔒 보안 가이드 ✅

### 1. 비밀번호 암호화
BCryptPasswordEncoder 사용. 회원가입 시 자동 암호화, 로그인 시 matches()로 검증.

### 2. JWT Secret 설정
```bash
export JWT_SECRET=your-secret-key-at-least-32-characters-long
```
`application.properties`의 기본값(`stock-management-secret-key-for-development-only`)은 개발용 전용이며, 운영 환경에서는 반드시 환경 변수로 교체해야 한다.

### 3. HTTPS 설정 (프로덕션)
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEY_PASSWORD}
```

---

## 🚀 최종 목표
- 확장 가능한 **쇼핑몰 백엔드 구조**
- 안정적인 **재고 및 주문/결제 관리**
- **동시성 제어**를 통한 데이터 정합성 보장
- **MSA 전환 가능한 모듈 구조**

---

## 📝 라이선스
MIT License

---
