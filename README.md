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
- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Build Tool**: Gradle 8.x

### Database & Cache
- **Database**: MySQL 8.x (InnoDB)
- **ORM**: Spring Data JPA (Hibernate)
- **Migration**: Flyway
- **Cache/Queue**: Redis

### Security & Auth
- **Security**: Spring Security 6.x
- **Auth**: JWT (jjwt 라이브러리)

### Container & DevOps
- **Container**: Docker, Docker Compose
- **Monitoring**: Spring Actuator + Prometheus

### Testing
- **Unit Test**: JUnit 5, Mockito
- **Integration Test**: Testcontainers (MySQL, Redis)

---

## 📁 프로젝트 구조

```
stock-management-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── stockmanagement/
│   │   │           ├── StockManagementApplication.java
│   │   │           ├── domain/
│   │   │           │   ├── inventory/
│   │   │           │   │   ├── entity/
│   │   │           │   │   │   ├── Product.java
│   │   │           │   │   │   ├── Inventory.java
│   │   │           │   │   │   └── InventoryTransaction.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   ├── ProductRepository.java
│   │   │           │   │   │   └── InventoryRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   └── InventoryService.java
│   │   │           │   │   ├── controller/
│   │   │           │   │   │   └── InventoryController.java
│   │   │           │   │   └── dto/
│   │   │           │   │       ├── InventoryResponse.java
│   │   │           │   │       └── ReserveStockRequest.java
│   │   │           │   ├── order/
│   │   │           │   │   ├── entity/
│   │   │           │   │   │   ├── Order.java
│   │   │           │   │   │   └── OrderItem.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   ├── service/
│   │   │           │   │   ├── controller/
│   │   │           │   │   └── dto/
│   │   │           │   ├── payment/
│   │   │           │   │   ├── entity/
│   │   │           │   │   │   └── Payment.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   ├── service/
│   │   │           │   │   ├── controller/
│   │   │           │   │   └── dto/
│   │   │           │   └── user/
│   │   │           │       ├── entity/
│   │   │           │       │   └── User.java
│   │   │           │       ├── repository/
│   │   │           │       ├── service/
│   │   │           │       ├── controller/
│   │   │           │       └── dto/
│   │   │           ├── common/
│   │   │           │   ├── config/
│   │   │           │   │   ├── JpaConfig.java
│   │   │           │   │   ├── RedisConfig.java
│   │   │           │   │   └── SecurityConfig.java
│   │   │           │   ├── exception/
│   │   │           │   │   ├── GlobalExceptionHandler.java
│   │   │           │   │   ├── InsufficientStockException.java
│   │   │           │   │   └── BusinessException.java
│   │   │           │   └── dto/
│   │   │           │       └── ApiResponse.java
│   │   │           └── security/
│   │   │               ├── JwtTokenProvider.java
│   │   │               └── JwtAuthenticationFilter.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__init_schema.sql
│   │               ├── V2__create_order_tables.sql
│   │               └── V3__create_payment_tables.sql
│   └── test/
│       └── java/
│           └── com/
│               └── stockmanagement/
│                   ├── inventory/
│                   │   └── InventoryServiceTest.java
│                   └── integration/
│                       └── InventoryIntegrationTest.java
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── build.gradle
└── README.md
```

---

## 📌 개발 단계별 로드맵

### 1단계: 기본 뼈대 ✅
**목표**: Spring Boot 프로젝트 초기 설정 및 기본 도메인 모델 구현

#### 작업 내용
- [x] Spring Initializer로 프로젝트 생성
  - Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Lombok, Validation
- [x] MySQL 연동 및 Flyway 설정
- [x] 도메인 모델 설계
  - `Product` Entity 생성
  - `Inventory` Entity 생성
  - Repository, Service, Controller 기본 구조
- [x] 기본 CRUD API 구현
  - 상품 등록/조회/수정/삭제
  - 재고 조회

#### 주요 파일
```
src/main/resources/db/migration/
└── V1__init_schema.sql

src/main/java/.../inventory/
├── entity/
│   ├── Product.java
│   └── Inventory.java
├── repository/
│   ├── ProductRepository.java
│   └── InventoryRepository.java
├── service/
│   └── InventoryService.java
└── controller/
    └── InventoryController.java
```

#### API 예시
```
GET    /api/products          # 상품 목록 조회
POST   /api/products          # 상품 등록
GET    /api/products/{id}     # 상품 상세 조회
PUT    /api/products/{id}     # 상품 수정
DELETE /api/products/{id}     # 상품 삭제

GET    /api/inventory/products/{productId}/warehouses/{warehouseId}  # 재고 조회
```

---

### 2단계: 주문 & 재고 예약 🔄
**목표**: 주문 생성 시 재고 예약 및 동시성 제어 구현

#### 작업 내용
- [ ] 주문 도메인 구현
  - `Order`, `OrderItem` Entity 설계
  - OrderStatus Enum (PENDING, CONFIRMED, CANCELLED)
- [ ] 재고 예약 로직
  - 주문 생성 시 `reserved` 수량 증가
  - `available = onHand - reserved - allocated` 계산
- [ ] 동시성 제어
  - **비관적 락**: JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` 적용
  - **낙관적 락**: `@Version` 필드로 버전 관리
  - **분산 락**: Redisson으로 분산 환경 대응
- [ ] 재고 부족 예외 처리
  - `InsufficientStockException` 커스텀 예외
  - `@ControllerAdvice`로 전역 예외 처리

#### 핵심 코드 예시

**Inventory Entity (낙관적 락)**
```java
@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long productId;
    private Long warehouseId;
    private Integer onHand;      // 실제 보유 수량
    private Integer reserved;    // 예약된 수량
    private Integer allocated;   // 할당된 수량
    
    @Version
    private Long version;  // 낙관적 락 버전
    
    public Integer getAvailable() {
        return onHand - reserved - allocated;
    }
    
    public void reserve(Integer quantity) {
        if (getAvailable() < quantity) {
            throw new InsufficientStockException(
                String.format("재고 부족. 요청: %d, 가용: %d", quantity, getAvailable())
            );
        }
        this.reserved += quantity;
    }
}
```

**InventoryRepository (비관적 락)**
```java
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.warehouseId = :warehouseId")
    Optional<Inventory> findByProductIdAndWarehouseIdForUpdate(
        @Param("productId") Long productId,
        @Param("warehouseId") Long warehouseId
    );
}
```

**InventoryService**
```java
@Service
@Transactional(readOnly = true)
public class InventoryService {
    
    private final InventoryRepository inventoryRepository;
    
    @Transactional
    public void reserveStock(Long productId, Long warehouseId, int quantity, String orderId) {
        Inventory inventory = inventoryRepository
            .findByProductIdAndWarehouseIdForUpdate(productId, warehouseId)
            .orElseThrow(() -> new InventoryNotFoundException());
        
        inventory.reserve(quantity);
        inventoryRepository.save(inventory);
    }
}
```

#### API 예시
```
POST /api/orders                      # 주문 생성 (재고 예약)
GET  /api/orders/{id}                 # 주문 조회
GET  /api/orders                      # 주문 목록 조회
POST /api/orders/{id}/cancel          # 주문 취소 (재고 예약 해제)
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

```
┌─────────────┐       ┌──────────────┐       ┌─────────────┐
│  products   │       │  inventory   │       │ warehouses  │
├─────────────┤       ├──────────────┤       ├─────────────┤
│ id (PK)     │◄──────│ product_id   │       │ id (PK)     │
│ name        │       │ warehouse_id │──────►│ name        │
│ sku         │       │ sku          │       │ location    │
│ category    │       │ on_hand      │       │ is_active   │
│ price       │       │ reserved     │       └─────────────┘
│ created_at  │       │ allocated    │
│ updated_at  │       │ version      │
└─────────────┘       │ created_at   │
                      │ updated_at   │
                      └──────────────┘
                             │
                             │
                      ┌──────▼──────────────────┐
                      │ inventory_transactions  │
                      ├─────────────────────────┤
                      │ id (PK)                 │
                      │ inventory_id (FK)       │
                      │ order_id                │
                      │ transaction_type        │
                      │ quantity                │
                      │ before_on_hand          │
                      │ after_on_hand           │
                      │ created_at              │
                      └─────────────────────────┘

┌─────────────┐       ┌──────────────┐       ┌──────────────┐
│   users     │       │   orders     │       │ order_items  │
├─────────────┤       ├──────────────┤       ├──────────────┤
│ id (PK)     │◄──────│ user_id      │◄──────│ order_id     │
│ username    │       │ order_number │       │ product_id   │
│ password    │       │ status       │       │ quantity     │
│ email       │       │ total_amount │       │ unit_price   │
│ role        │       │ idempotency  │       │ subtotal     │
│ created_at  │       │ created_at   │       └──────────────┘
└─────────────┘       │ updated_at   │
                      └──────────────┘
                             │
                             │
                      ┌──────▼──────────┐
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
```

### Flyway 마이그레이션 파일

**V1__init_schema.sql**
```sql
-- 상품 테이블
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100),
    price DECIMAL(15, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sku (sku),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 창고 테이블
CREATE TABLE warehouses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 재고 테이블
CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    on_hand INT NOT NULL DEFAULT 0,
    reserved INT NOT NULL DEFAULT 0,
    allocated INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_warehouse (product_id, warehouse_id),
    INDEX idx_product_warehouse (product_id, warehouse_id),
    INDEX idx_sku (sku),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 재고 트랜잭션 이력
CREATE TABLE inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    order_id VARCHAR(50),
    transaction_type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    before_on_hand INT,
    after_on_hand INT,
    before_reserved INT,
    after_reserved INT,
    before_allocated INT,
    after_allocated INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_inventory_id (inventory_id),
    INDEX idx_order_id (order_id),
    FOREIGN KEY (inventory_id) REFERENCES inventory(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**V2__create_order_tables.sql**
```sql
-- 사용자 테이블
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 주문 테이블
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_order_number (order_number),
    INDEX idx_status (status),
    INDEX idx_idempotency (idempotency_key),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 주문 항목 테이블
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL,
    subtotal DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**V3__create_payment_tables.sql**
```sql
-- 결제 테이블
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pg_transaction_id VARCHAR(100),
    failure_reason VARCHAR(255),
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_pg_transaction_id (pg_transaction_id),
    INDEX idx_status (status),
    FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 📊 API 엔드포인트

### 인증 (Authentication)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/signup` | 회원가입 | No |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | No |
| GET | `/api/users/me` | 내 정보 조회 | Yes |
| GET | `/api/users/me/orders` | 내 주문 목록 | Yes |

### 상품 관리 (Products)

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| GET | `/api/products` | 상품 목록 조회 | Yes | USER |
| POST | `/api/products` | 상품 등록 | Yes | ADMIN |
| GET | `/api/products/{id}` | 상품 상세 조회 | Yes | USER |
| PUT | `/api/products/{id}` | 상품 수정 | Yes | ADMIN |
| DELETE | `/api/products/{id}` | 상품 삭제 | Yes | ADMIN |

### 재고 관리 (Inventory)

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| GET | `/api/inventory/products/{productId}/warehouses/{warehouseId}` | 재고 조회 | Yes | USER |
| POST | `/api/inventory/reserve` | 재고 예약 | Yes | USER |
| POST | `/api/inventory/release` | 재고 예약 해제 | Yes | USER |
| POST | `/api/inventory/receive` | 재고 입고 | Yes | ADMIN |
| POST | `/api/inventory/adjust` | 재고 조정 | Yes | ADMIN |
| GET | `/api/inventory/transactions` | 재고 트랜잭션 이력 | Yes | ADMIN |

### 주문 관리 (Orders)

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/orders` | 주문 생성 | Yes | USER |
| GET | `/api/orders/{id}` | 주문 조회 | Yes | USER |
| GET | `/api/orders` | 주문 목록 조회 | Yes | USER |
| POST | `/api/orders/{id}/cancel` | 주문 취소 | Yes | USER |
| GET | `/api/admin/orders` | 전체 주문 조회 | Yes | ADMIN |

### 결제 (Payments)

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/payments` | 결제 요청 | Yes | USER |
| POST | `/api/payments/webhook` | 결제 웹훅 (PG사) | No | - |
| GET | `/api/payments/orders/{orderId}` | 결제 조회 | Yes | USER |

---

## 🔧 API 요청/응답 예시

### 1. 회원가입
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

### 2. 로그인
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

### 3. 상품 등록 (관리자)
```http
POST /api/products
Authorization: Bearer {admin-token}
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
    "price": 1200000,
    "createdAt": "2026-01-31T11:00:00"
  }
}
```

### 4. 재고 조회
```http
GET /api/inventory/products/1/warehouses/1
Authorization: Bearer {token}
```

**Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "inventoryId": 1,
    "productId": 1,
    "warehouseId": 1,
    "sku": "PHONE-GS24-128-BLK",
    "onHand": 100,
    "reserved": 10,
    "allocated": 5,
    "available": 85,
    "updatedAt": "2026-01-31T12:00:00"
  }
}
```

### 5. 주문 생성
```http
POST /api/orders
Authorization: Bearer {token}
Content-Type: application/json

{
  "idempotencyKey": "unique-key-12345",
  "items": [
    {
      "productId": 1,
      "warehouseId": 1,
      "quantity": 2,
      "unitPrice": 1200000
    }
  ]
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "orderNumber": "ORD20260131120000ABC123",
    "status": "PENDING",
    "totalAmount": 2400000,
    "items": [
      {
        "productId": 1,
        "productName": "스마트폰 Galaxy S24",
        "quantity": 2,
        "unitPrice": 1200000,
        "subtotal": 2400000
      }
    ],
    "createdAt": "2026-01-31T12:00:00"
  }
}
```

### 6. 결제 요청
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

### 7. 결제 웹훅 (PG사 → 서버)
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

---

## 🚀 실행 방법

### 사전 요구사항
- JDK 17 이상
- Docker & Docker Compose
- Gradle 8.x

### 로컬 환경 실행

#### 1. MySQL & Redis 실행
```bash
# MySQL 실행
docker run -d --name stock-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=stock_management \
  -p 3306:3306 \
  mysql:8

# Redis 실행
docker run -d --name stock-redis \
  -p 6379:6379 \
  redis:7-alpine
```

#### 2. 애플리케이션 실행
```bash
# Gradle Wrapper 사용
./gradlew bootRun

# 또는 JAR 빌드 후 실행
./gradlew clean build
java -jar build/libs/stock-management-api-0.0.1-SNAPSHOT.jar
```

#### 3. 접속 확인
```bash
curl http://localhost:8080/actuator/health
```

### Docker Compose로 전체 스택 실행

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

## 🔒 보안 가이드

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
