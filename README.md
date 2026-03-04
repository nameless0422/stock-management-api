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

### 3. **결제(Payment)**
- 결제 요청 → 승인/실패 → 웹훅 처리
- 재고 확정(할당) 또는 예약 해제

### 4. **유저(User)**
- 회원가입/로그인 (JWT 인증)
- 주문/결제 이력 조회

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
- **Security**: Spring Security 6.x (예정)
- **Auth**: JWT / jjwt 라이브러리 (예정)

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
│   │   │           ├── payment/                          # 🔄 예정 (3단계)
│   │   │           │   ├── entity/
│   │   │           │   ├── repository/
│   │   │           │   ├── service/
│   │   │           │   ├── controller/
│   │   │           │   └── dto/
│   │   │           └── user/                             # 🔄 예정 (4단계)
│   │   │               ├── entity/
│   │   │               ├── repository/
│   │   │               ├── service/
│   │   │               ├── controller/
│   │   │               └── dto/
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql               # ✅ products 테이블
│   │           ├── V2__create_inventory_tables.sql   # ✅ inventory 테이블
│   │           ├── V3__create_order_tables.sql       # ✅ orders, order_items 테이블
│   │           └── V4__create_payment_tables.sql     # 🔄 예정 (3단계)
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

### 3단계: 결제 연동 💳
**목표**: 결제 프로세스 구현 및 재고 상태 전이 관리

#### 작업 내용
- [ ] 결제 도메인 구현
  - `Payment` Entity 설계
  - PaymentStatus Enum (PENDING, SUCCESS, FAILED, CANCELLED)
- [ ] 결제 요청 API
  - 결제 요청 → PG사 연동 (Mock)
  - 결제 결과 대기 상태로 주문 상태 변경
- [ ] 웹훅 처리
  - **결제 성공** → 재고 확정 (`reserved` → `allocated`)
  - **결제 실패** → 재고 예약 해제 (`reserved` 감소)
- [ ] 트랜잭션 관리
  - `@Transactional`로 재고/주문/결제 일관성 보장
  - 실패 시 롤백 처리

#### 결제 프로세스 흐름

```
[주문 생성] → reserved +10
    ↓
[결제 요청] → Payment(PENDING)
    ↓
[PG사 처리]
    ↓
    ├─ [성공] → Payment(SUCCESS)
    │           reserved -10, allocated +10
    │           Order(CONFIRMED)
    │
    └─ [실패] → Payment(FAILED)
                reserved -10
                Order(PAYMENT_FAILED)
```

#### 핵심 코드 예시

**PaymentService**
```java
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    @Transactional
    public PaymentResponse requestPayment(PaymentRequest request, String username) {
        Order order = orderService.getOrder(request.getOrderId(), username);

        Payment payment = Payment.builder()
            .orderId(request.getOrderId())
            .amount(order.getTotalAmount())
            .status(PaymentStatus.PENDING)
            .build();

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public void processPaymentWebhook(PaymentWebhookRequest webhook) {
        Payment payment = paymentRepository
            .findByPgTransactionId(webhook.getPgTransactionId())
            .orElseThrow();

        if (webhook.isSuccess()) {
            payment.complete();
            Order order = orderService.getOrderById(payment.getOrderId());
            order.confirmPayment();

            // 재고: 예약 → 할당
            for (OrderItem item : order.getOrderItems()) {
                inventoryService.allocateStock(
                    item.getProductId(),
                    item.getWarehouseId(),
                    item.getQuantity(),
                    order.getOrderNumber()
                );
            }
        } else {
            payment.fail();
            Order order = orderService.getOrderById(payment.getOrderId());

            // 재고 예약 해제
            for (OrderItem item : order.getOrderItems()) {
                inventoryService.releaseReservation(
                    item.getProductId(),
                    item.getWarehouseId(),
                    item.getQuantity(),
                    order.getOrderNumber()
                );
            }

            order.failPayment();
        }
    }
}
```

#### API 예시
```
POST /api/payments                    # 결제 요청
POST /api/payments/webhook            # 결제 웹훅 (PG사 → 서버)
GET  /api/payments/orders/{orderId}   # 결제 조회
```

---

### 4단계: 사용자 관리 👤
**목표**: JWT 기반 인증/인가 시스템 구현

#### 작업 내용
- [ ] User 도메인 구현
  - `User` Entity (username, password, email, role)
  - UserRole Enum (USER, ADMIN)
- [ ] Spring Security + JWT 인증
  - `/api/auth/signup`, `/api/auth/login` 엔드포인트
  - JwtTokenProvider로 토큰 생성/검증
  - SecurityFilterChain 설정
- [ ] 사용자별 주문/결제 이력 조회
  - 현재 로그인 사용자의 주문 목록
  - 주문 상세 정보 조회

#### 핵심 코드 예시

**JwtTokenProvider**
```java
@Component
public class JwtTokenProvider {

    @Value("${spring.security.jwt.secret}")
    private String secretKey;

    @Value("${spring.security.jwt.token-validity-in-seconds}")
    private long tokenValidityInSeconds;

    public String createToken(String username, List<String> roles) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("roles", roles);

        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInSeconds * 1000);

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

**SecurityConfig**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

#### API 예시
```
POST /api/auth/signup                 # 회원가입
POST /api/auth/login                  # 로그인 (JWT 발급)
GET  /api/users/me                    # 내 정보 조회
GET  /api/users/me/orders             # 내 주문 목록
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

🔄 예정 (3단계~)
                      ┌─────────────────┐
                      │    payments     │
                      ├─────────────────┤
                      │ id (PK)         │
                      │ order_id (FK)   │
                      │ amount          │
                      │ payment_method  │
                      │ status          │
                      │ pg_transaction  │
                      │ paid_at         │
                      │ created_at      │
                      └─────────────────┘

🔄 예정 (4단계~)
┌─────────────┐
│   users     │
├─────────────┤
│ id (PK)     │
│ username    │
│ password    │
│ email       │
│ role        │
│ created_at  │
└─────────────┘
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

**V4__create_payment_tables.sql** 🔄 (3단계 예정)
```sql
-- payments 테이블
```

---

## 📊 API 엔드포인트

### 인증 (Authentication) 🔄 예정 (4단계)

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

### 결제 (Payments) 🔄 예정 (3단계)

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/payments` | 결제 요청 | Yes | USER |
| POST | `/api/payments/webhook` | 결제 웹훅 (PG사) | No | - |
| GET | `/api/payments/orders/{orderId}` | 결제 조회 | Yes | USER |

---

## 🔧 API 요청/응답 예시

### 1. 회원가입 🔄 예정
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

### 2. 로그인 🔄 예정
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

### 7. 결제 요청 🔄 예정
```http
POST /api/payments
Authorization: Bearer {token}
Content-Type: application/json

{
  "orderId": 1,
  "paymentMethod": "CREDIT_CARD"
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "paymentId": 1,
    "orderId": 1,
    "amount": 2400000,
    "status": "PENDING",
    "pgTransactionId": "PG123456789",
    "createdAt": "2026-01-31T12:05:00"
  }
}
```

### 8. 결제 웹훅 (PG사 → 서버) 🔄 예정
```http
POST /api/payments/webhook
Content-Type: application/json
X-PG-Signature: {signature}

{
  "pgTransactionId": "PG123456789",
  "status": "SUCCESS",
  "paidAmount": 2400000,
  "paidAt": "2026-01-31T12:05:30"
}
```

**Response (200 OK)**
```json
{
  "success": true,
  "message": "결제 처리 완료"
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

Flyway가 기동 시 V1, V2, V3 마이그레이션을 자동 실행합니다.

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

## 🔒 보안 가이드 🔄 예정 (4단계)

### 1. 비밀번호 암호화
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

### 2. JWT Secret 설정
```yaml
spring:
  security:
    jwt:
      secret: ${JWT_SECRET}  # 환경 변수로 관리
```

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
