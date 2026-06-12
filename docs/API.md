# API 엔드포인트 레퍼런스

> 전체 엔드포인트는 Swagger UI에서 직접 테스트 가능: `http://localhost:8080/swagger-ui/index.html`

## 공통

### 응답 형식

모든 응답은 `ApiResponse<T>` 래퍼로 반환된다.

```json
{ "success": true,  "data": { ... } }
{ "success": false, "errorCode": "INSUFFICIENT_STOCK", "message": "에러 메시지" }
{ "success": false, "errorCode": "VALIDATION_FAILED", "errors": [
    { "field": "email", "message": "이메일 형식이 올바르지 않습니다." }
  ]}
```

### 인증

```http
Authorization: Bearer <accessToken>
```

미인증 요청 → `401 Unauthorized`, 권한 부족 → `403 Forbidden` (ApiResponse 형식 통일)

### 주요 에러 코드

| HTTP | 코드 | 설명 |
|------|------|------|
| 400 | `INVALID_INPUT` | 요청 파라미터 검증 실패 |
| 400 | `PRODUCT_NOT_AVAILABLE` | 비활성화된 상품/바리에이션 주문 시도 |
| 400 | `INSUFFICIENT_STOCK` | 재고 부족 |
| 400 | `INSUFFICIENT_POINTS` | 포인트 잔액 부족 |
| 400 | `COUPON_ALREADY_USED` | 이미 사용된 쿠폰 |
| 401 | `UNAUTHORIZED` | 미인증 (JWT 없음/만료) |
| 401 | `INVALID_RESET_TOKEN` | 비밀번호 재설정 토큰 무효/만료 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `PRODUCT_NOT_FOUND` 등 | 리소스 없음 |
| 409 | `DUPLICATE_ORDER` | 멱등성 키 중복 |
| 409 | `PAYMENT_PROCESSING_IN_PROGRESS` | 결제 처리 중 중복 요청 |
| 429 | `TOO_MANY_REQUESTS` | Rate Limit 초과 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

---

## 인증 / 사용자

### `POST /api/auth/signup`

> 권한: 공개

```json
// 요청
{ "username": "user1", "password": "Password1!", "email": "user1@example.com" }

// 응답
{ "id": 1, "username": "user1", "email": "user1@example.com", "role": "USER" }
```

### `POST /api/auth/login`

> 권한: 공개 | Rate Limit: 15분 내 5회 실패 시 잠금

```json
// 요청
{ "username": "user1", "password": "Password1!" }

// 응답
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "refreshToken": "uuid-..."
}
```

### `POST /api/auth/logout`

> 권한: 공개 | Access Token 블랙리스트 등록 + Refresh Token revoke

```json
// 요청 body (선택)
{ "refreshToken": "uuid-..." }
```

### `POST /api/auth/refresh`

> 권한: 공개 | Refresh Token rotation (호출마다 새 토큰 발급, 이전 토큰 즉시 폐기)

```json
// 요청
{ "refreshToken": "uuid-..." }
// 응답: LoginResponse (accessToken + 새 refreshToken)
```

### `POST /api/auth/forgot-password`

> 권한: 공개 | Rate Limit: 3회/시간 (IP)

```json
// 요청
{ "email": "user1@example.com" }
// 응답: 200 OK (이메일 존재 여부와 무관하게 동일 응답 — 계정 열거 방지)
```

### `POST /api/auth/reset-password`

> 권한: 공개 | Rate Limit: 5회/시간 (IP)

```json
// 요청
{ "token": "uuid-...", "newPassword": "NewPass1!" }
// 응답: 200 OK — 성공 시 해당 사용자 Refresh Token 일괄 폐기
```

### `GET /api/user/me`

> 권한: USER

```json
// 응답
{ "id": 1, "username": "user1", "email": "user1@example.com", "role": "USER", "phoneNumber": "010-1234-5678", "pointBalance": 3000 }
```

### `PUT /api/user/profile`

> 권한: USER | 프로필 수정 (이름, 전화번호 등)

```json
{ "phoneNumber": "010-9999-8888" }
```

### `POST /api/user/password/change`

> 권한: USER | 현재 비밀번호 확인 후 변경. 성공 시 Access Token 블랙리스트 등록 + Refresh Token 일괄 폐기

```json
{ "currentPassword": "OldPass1!", "newPassword": "NewPass1!" }
```

### `GET /api/user/orders`

> 권한: USER | 내 주문 목록 (커서 페이지네이션, `?lastId=&size=20`)

### `POST /api/user/deactivate`

> 권한: USER | Soft Delete (`deleted_at` 설정) + PENDING 주문 강제 취소 + 장바구니·위시리스트·배송지 일괄 삭제 + Refresh Token 일괄 폐기

---

## 홈 화면

### `GET /api/home`

> 권한: 공개 | Redis 캐시 5분 TTL

```json
// 응답
{
  "newArrivals": [ { "id": 1, "name": "...", "price": 39000, ... } ],
  "popularProducts": [ { "id": 5, "name": "...", "reviewCount": 42, ... } ],
  "featuredCategories": [ { "id": 1, "name": "전자기기", "productCount": 120, ... } ]
}
```

- `newArrivals`: 최신 등록 상품 8건 (ACTIVE, 생성일 내림차순)
- `popularProducts`: 리뷰 수 기반 인기 상품 8건 (LEFT JOIN Review)
- `featuredCategories`: 카테고리 트리 (상품 수 포함)

---

## 상품

### `GET /api/products`

> 권한: 공개 | 검색 조건 있으면 Elasticsearch, 없으면 MySQL. ES 장애 시 MySQL fallback

| 쿼리 파라미터 | 설명 |
|---|---|
| `q` | 키워드 (name·sku·category·description multi_match) |
| `minPrice` / `maxPrice` | 가격 범위 |
| `categoryId` | 카테고리 ID |
| `includeChildren` | `true` 시 하위 카테고리 포함 검색 |
| `sort` | `price_asc` \| `price_desc` \| `newest` \| `relevance` (기본) |
| `page` / `size` | 페이징 (관리자·카탈로그 검색용) |

### `GET /api/products/search/suggestions`

> 권한: 공개 | Elasticsearch prefix query 기반 자동완성

```
GET /api/products/search/suggestions?q=노트
→ { "suggestions": ["노트북", "노트북 파우치", ...] }
```

### `POST /api/products`

> 권한: ADMIN | 등록 시 기본 variant("기본") + inventory 자동 생성

```json
// 요청
{
  "name": "MacBook Pro 16",
  "price": 3990000,
  "sku": "MBP-16-M3",
  "status": "ACTIVE",
  "categoryId": 5,
  "description": "..."
}
```

### `PUT /api/products/{id}`

> 권한: ADMIN | 상품 수정. ES 문서 동기 업데이트

### `DELETE /api/products/{id}`

> 권한: ADMIN | Soft Delete — `status = DISCONTINUED` 로 변경

---

## 상품 바리에이션

### `GET /api/products/{id}/variants`

> 권한: 공개

### `POST /api/products/{id}/variants`

> 권한: ADMIN

```json
{ "optionName": "블랙 M", "sku": "MBP-16-M3-BLK-M", "price": 3990000, "status": "ACTIVE" }
```

### `PUT /api/products/{id}/variants/{variantId}`

> 권한: ADMIN | 바리에이션 정보 수정

### `DELETE /api/products/{id}/variants/{variantId}`

> 권한: ADMIN | 바리에이션 + 연관 inventory 삭제

---

## 카테고리

> Redis 캐시 적용 (30분 TTL). 생성·수정·삭제 시 자동 evict.

### `GET /api/categories`

> 권한: 공개 | flat 목록 (children 빈 리스트). 캐시키: `categories::list`

### `GET /api/categories/tree`

> 권한: 공개 | parent-child 2단계 트리 in-memory 조립. 캐시키: `categories::tree`

```json
// 응답 예시
[
  { "id": 1, "name": "전자기기", "children": [
      { "id": 2, "name": "노트북", "children": [] },
      { "id": 3, "name": "스마트폰", "children": [] }
  ]}
]
```

### `GET /api/categories/{id}`

> 권한: 공개 | 단건 조회. children(하위 카테고리) 포함

### `POST /api/categories`

> 권한: ADMIN

```json
{ "name": "노트북", "parentId": 1 }   // parentId null이면 최상위
```

### `PUT /api/categories/{id}`

> 권한: ADMIN | 이름·설명·부모 카테고리 수정

```json
{ "name": "노트북 PC", "description": "휴대용 개인 컴퓨터", "parentId": 1 }
```

### `DELETE /api/categories/{id}`

> 권한: ADMIN | 하위 카테고리 또는 연결 상품 존재 시 `CATEGORY_HAS_CHILDREN` / `CATEGORY_HAS_PRODUCTS` 에러

---

## 상품 이미지

### `POST /api/products/{id}/images/presigned`

> 권한: ADMIN | MinIO(S3 호환) Presigned URL 발급. 클라이언트가 URL로 직접 업로드

```json
// 요청
{ "imageType": "THUMBNAIL", "fileName": "cover.jpg", "contentType": "image/jpeg" }

// 응답
{ "presignedUrl": "https://...", "objectKey": "products/1/cover.jpg" }
```

### `GET /api/products/{id}/images`

> 권한: 공개

### `DELETE /api/products/{id}/images/{imageId}`

> 권한: ADMIN | S3 오브젝트 삭제 + DB 레코드 삭제

---

## 리뷰

### `GET /api/products/{id}/reviews`

> 권한: 공개 | 평균 별점 + 리뷰 수 포함

```json
// 응답
{
  "reviews": [ { "id": 1, "rating": 5, "title": "좋아요", "content": "..." } ],
  "avgRating": 4.5,
  "reviewCount": 20
}
```

### `POST /api/products/{id}/reviews`

> 권한: USER | 해당 상품 구매 이력 필수 (미구매 시 `REVIEW_NOT_PURCHASED` 400). 1인 1리뷰

```json
{ "rating": 5, "title": "훌륭합니다", "content": "..." }
```

### `DELETE /api/products/{id}/reviews/{reviewId}`

> 권한: USER | 본인 리뷰만 삭제 가능

---

## 위시리스트

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/api/wishlist/{productId}` | 찜 추가 |
| `DELETE` | `/api/wishlist/{productId}` | 찜 제거 |
| `GET` | `/api/wishlist` | 찜 목록 조회 (커서 페이지네이션, `?lastId=&size=20`) |

> 권한: USER | `WishlistResponse`에 `productName`, `productPrice`, `thumbnailUrl`, `availableQuantity`, `productStatus` 포함

---

## 상품 Q&A

### `GET /api/products/{id}/qna`

> 권한: 공개 | 커서 페이지네이션 (`?lastId=&size=20`)

### `POST /api/products/{id}/qna`

> 권한: USER

```json
{ "question": "사이즈가 작게 나오나요?" }
```

### `PUT /api/products/{id}/qna/{qnaId}/answer`

> 권한: ADMIN

```json
{ "answer": "정사이즈로 나옵니다." }
```

### `DELETE /api/products/{id}/qna/{qnaId}`

> 권한: USER(본인) / ADMIN

---

## 재입고 알림

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| `POST` | `/api/products/{id}/restock-notifications` | 알림 구독 | USER |
| `GET` | `/api/products/{id}/restock-notifications` | 구독 목록 | USER |
| `DELETE` | `/api/products/{id}/restock-notifications/{notificationId}` | 구독 취소 | USER |

---

## 재고

> 재고 모델: `available = onHand - reserved - allocated`
> 모든 write 연산은 분산 락(Redisson) + 비관적 락(DB) 2중 적용

### `GET /api/inventory`

> 권한: ADMIN | 필터 검색

| 쿼리 파라미터 | 설명 |
|---|---|
| `productId` | 상품 ID |
| `status` | `NORMAL` \| `LOW_STOCK` \| `OUT_OF_STOCK` |
| `page` / `size` | 페이징 |

### `GET /api/inventory/variants/{variantId}`

> 권한: ADMIN | 바리에이션 단건 재고 조회

### `GET /api/inventory/variants/{variantId}/transactions`

> 권한: ADMIN | 재고 변동 이력, 페이징 지원. `(inventory_id, created_at)` 복합 인덱스

### `POST /api/inventory/variants/{variantId}/receive`

> 권한: ADMIN | 입고 처리

```json
{ "quantity": 100, "note": "정기 입고" }
```

### `POST /api/inventory/variants/{variantId}/adjust`

> 권한: ADMIN | 실사 후 수량 조정

```json
{ "quantity": -5, "note": "파손 폐기" }
```

---

## 주문

> **소유권 검증**: 단건 조회·취소는 본인 주문만. ADMIN은 전체 접근 가능

### `POST /api/orders/preview`

> 권한: USER | DB 변경 없음. 최종 금액·할인·포인트 미리 계산

```json
// 요청
{
  "items": [{ "variantId": 1, "quantity": 2 }],
  "couponCode": "FIXED5000",
  "usePoints": 3000
}

// 응답
{ "totalAmount": 52000, "discountAmount": 5000, "usedPoints": 3000, "finalAmount": 44000 }
```

### `POST /api/orders`

> 권한: USER | Rate Limit: 10회/분

```json
// 요청
{
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "deliveryAddressId": 1,
  "couponCode": "FIXED5000",
  "usePoints": 3000,
  "items": [
    { "variantId": 1, "quantity": 2 }
  ]
}
```

- `unitPrice`를 보내도 **무시됨** — 서버가 DB 가격을 강제 적용 (가격 조작 방어)
- `idempotencyKey` 중복 시 기존 주문 반환 (멱등성)
- 바리에이션 `status = ACTIVE` 아니면 `PRODUCT_NOT_AVAILABLE` 에러

```json
// 응답
{
  "id": 1,
  "orderNumber": "ORD-20240701-000001",
  "status": "PENDING",
  "totalAmount": 52000,
  "discountAmount": 5000,
  "usedPoints": 3000,
  "couponId": 2,
  "items": [{ "variantId": 1, "optionName": "L / 블랙", "quantity": 2, ... }]
}
```

### `GET /api/orders`

> 권한: USER(본인) / ADMIN(전체) | 커서 페이지네이션

| 쿼리 파라미터 | 설명 |
|---|---|
| `status` | `PENDING` \| `PAYMENT_IN_PROGRESS` \| `CONFIRMED` \| `CANCELLED` |
| `userId` | ADMIN 전용 필터 |
| `startDate` / `endDate` | ISO 날짜 범위 (`2024-01-01`) |
| `lastId` / `size` | 커서 페이지네이션 (기본 20) |

### `GET /api/orders/{id}`

> 권한: USER(본인) | 주문 단건 상세 조회. `OrderDetailResponse` — items에 `thumbnailUrl`, `refundId` 포함

### `POST /api/orders/{id}/cancel`

> 권한: USER | `PENDING` 상태만 취소 가능. 재고 반환 + 쿠폰 반환 + 포인트 반환

```json
{ "reason": "단순 변심" }
```

### `POST /api/orders/{id}/cancel-items`

> 권한: USER | `CONFIRMED` 상태에서 특정 아이템만 부분 취소. 주문 `PARTIAL_CANCELLED` 전이

```json
{ "itemIds": [1, 2] }
```

### `GET /api/orders/{id}/history`

> 권한: USER | 주문 상태 변경 이력 (`order_status_history`)

---

## 쿠폰

### `POST /api/coupons/validate`

> 권한: USER | 읽기 전용 할인 금액 미리보기. DB 변경 없음

```json
// 요청
{ "couponCode": "FIXED5000", "orderAmount": 60000 }

// 응답
{ "couponCode": "FIXED5000", "discountAmount": 5000, "finalAmount": 55000 }
```

### `POST /api/coupons`

> 권한: ADMIN

```json
{
  "code": "SUMMER20",
  "name": "여름 20% 할인",
  "discountType": "PERCENTAGE",
  "discountValue": 20,
  "minimumOrderAmount": 30000,
  "maxDiscountAmount": 10000,
  "maxUsageCount": 1000,
  "maxUsagePerUser": 1,
  "validFrom": "2024-07-01T00:00:00",
  "validUntil": "2024-08-31T23:59:59",
  "isPublic": true
}
```

| 할인 타입 | 계산 |
|---|---|
| `FIXED_AMOUNT` | `min(discountValue, orderAmount)` |
| `PERCENTAGE` | `min(orderAmount × rate / 100, maxDiscountAmount)` |

- `isPublic = true`: 코드를 아는 누구나 claim 가능한 공개 프로모 코드
- `isPublic = false` (기본값): ADMIN이 특정 사용자에게 직접 발급해야 함

### `GET /api/coupons`

> 권한: ADMIN | 쿠폰 목록 (페이징)

### `GET /api/coupons/{id}`

> 권한: ADMIN | 쿠폰 단건 조회

### `PATCH /api/coupons/{id}/deactivate`

> 권한: ADMIN | 쿠폰 비활성화 (`active = false`). 이미 발급된 쿠폰은 사용 불가 처리

### `POST /api/coupons/{id}/issue`

> 권한: ADMIN | 특정 사용자에게 쿠폰 직접 발급 (`isPublic = false` 쿠폰 배포 시 사용)

```json
{ "userId": 42 }
```

### `GET /api/coupons/my`

> 권한: USER | 내 지갑의 쿠폰 목록 + 각 쿠폰별 사용 횟수 포함

### `POST /api/coupons/claim`

> 권한: USER | `isPublic = true` 쿠폰만 등록 가능. 중복 등록 불가

```json
{ "couponCode": "SUMMER20" }
```

---

## 결제

> TossPayments Core API v1 연동. Circuit Breaker 적용 (연속 실패 시 회로 차단)

### `POST /api/payments/prepare`

> 권한: USER | 소유권 검증 + 서버 금액 검증. Payment(PENDING) 생성

```json
// 요청
{ "orderId": 1, "amount": 52000 }

// 응답
{ "tossOrderId": "uuid-...", "amount": 52000 }
```

### `POST /api/payments/confirm`

> 권한: USER | Rate Limit: 5회/분 | **3중 멱등성 보장**

```json
// 요청
{ "paymentKey": "toss_pay_key_...", "tossOrderId": "uuid-...", "amount": 52000 }
```

처리 순서:
1. Redis SETNX로 중복 요청 선점 차단 (완료 캐시 HIT 시 Toss API 생략)
2. Toss confirmations API 호출 (`Idempotency-Key` 헤더 포함)
3. Order `CONFIRMED`, reserved → allocated
4. Shipment(PREPARING) 자동 생성 (`Propagation.REQUIRES_NEW`)
5. 포인트 1% 적립 (`Propagation.REQUIRES_NEW`)

> 4, 5번 실패해도 결제 커밋은 보장됨

### `POST /api/payments/{paymentKey}/cancel`

> 권한: USER | 소유권 검증. 비관적 락으로 이중 취소 방지 (TOCTOU)

```json
{ "cancelReason": "단순 변심" }
```

처리 순서:
1. `findByPaymentKeyWithLock()` — 행 수준 비관적 락
2. Toss cancels API 호출
3. Order `CANCELLED`, allocated 해제
4. 쿠폰 반환 + 포인트 반환 + 적립 포인트 회수

### `GET /api/payments/order/{orderId}`

> 권한: USER | 주문 ID로 결제 조회. 결제 전이면 `data: null` 반환

### `GET /api/payments/{paymentKey}`

> 권한: USER | 결제 단건 조회

### `POST /api/payments/webhook`

> 권한: 공개 | `Toss-Signature` 헤더 HMAC-SHA256 검증 후 처리
>
> 가상계좌 결제 DONE 이벤트 처리: 주문 CONFIRMED, 배송 PREPARING 자동 생성, 포인트 적립

---

## 장바구니

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/cart` | 장바구니 조회 (가격 변동 항목 `priceChanged: true` 표시) |
| `POST` | `/api/cart/items` | 상품 담기 / 수량 변경 (ACTIVE 바리에이션만) |
| `DELETE` | `/api/cart/items/variants/{variantId}` | 바리에이션 제거 |
| `DELETE` | `/api/cart` | 장바구니 전체 비우기 |
| `POST` | `/api/cart/checkout` | 장바구니 → 주문 전환 |

> 권한: USER

```json
// POST /api/cart/items 요청
{ "variantId": 1, "quantity": 2 }

// POST /api/cart/checkout 요청
{
  "idempotencyKey": "uuid-...",
  "deliveryAddressId": 1,
  "couponCode": "FIXED5000",
  "usePoints": 3000
}
```

---

## 배송

> 결제 확정 시 자동으로 `PREPARING` 상태 Shipment 생성
> `ShipmentResponse.trackingUrl` 포함 (CJ대한통운·한진택배·롯데택배·우체국택배 자동 매핑)

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| `GET` | `/api/shipments/my` | 내 배송 목록 (커서 페이지네이션, `?lastId=&size=20`) | USER |
| `GET` | `/api/shipments/{id}` | 배송 단건 조회 (본인 주문만) | USER |
| `POST` | `/api/shipments/{id}/ship` | 배송 시작 → `SHIPPED` | ADMIN |
| `POST` | `/api/shipments/{id}/deliver` | 배송 완료 → `DELIVERED` | ADMIN |
| `POST` | `/api/shipments/{id}/return` | 반품 → `RETURNED` | ADMIN |

```json
// POST ship 요청
{ "carrier": "CJ대한통운", "trackingNumber": "123456789012", "estimatedDeliveryAt": "2024-07-15" }
```

---

## 배송지

> 첫 번째 등록 배송지는 자동으로 기본 배송지 설정.
> 기본 배송지 삭제 시 최신 등록 배송지로 자동 승격.

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| `POST` | `/api/delivery-addresses` | 배송지 등록 | USER |
| `GET` | `/api/delivery-addresses` | 배송지 목록 | USER |
| `GET` | `/api/delivery-addresses/{id}` | 단건 조회 | USER |
| `PUT` | `/api/delivery-addresses/{id}` | 수정 | USER |
| `DELETE` | `/api/delivery-addresses/{id}` | 삭제 | USER |
| `POST` | `/api/delivery-addresses/{id}/default` | 기본 배송지 설정 | USER |

```json
// POST 요청
{
  "alias": "집",
  "recipient": "홍길동",
  "phone": "010-1234-5678",
  "zipCode": "06292",
  "address1": "서울시 강남구 테헤란로 123",
  "address2": "101호"
}
```

---

## 포인트

> 결제 확정 시 실결제금액의 1% 자동 적립.
> 주문 취소 시 사용 포인트 반환 + 적립 포인트 회수 (잔액 부족 시 가능한 만큼만)

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/points/my` | 포인트 잔액 + 사용 가능 포인트 조회 |
| `GET` | `/api/points/transactions` | 변동 이력 (커서 페이지네이션, `?lastId=&size=20`) |
| `GET` | `/api/points/expiring-soon` | 30일 내 만료 예정 포인트 조회 |
| `POST` | `/api/points/use` | 포인트 수동 사용 (관리자 발급 포인트 사용 시) |

> 권한: USER

---

## 환불

> `DONE` 상태 결제만 환불 요청 가능.
> 이전 환불이 `FAILED`이면 `reset()` 후 재시도 가능.

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| `POST` | `/api/refunds` | 환불 요청 (본인 주문만) | USER |
| `GET` | `/api/refunds` | 내 환불 목록 (커서 페이지네이션, `?lastId=&size=20`) | USER |
| `GET` | `/api/refunds/{id}` | 환불 단건 조회 (본인만) | USER |
| `GET` | `/api/payments/{paymentId}/refund` | 결제 ID로 환불 조회 | USER |

```json
// POST 요청
{ "paymentId": 1, "reason": "상품 불량" }
```

---

## 알림

> 결제 확정·배송 상태 변경 등 주요 이벤트 발생 시 자동 생성

| Method | Endpoint | 설명 | 권한 |
|---|---|---|---|
| `GET` | `/api/notifications` | 내 알림 목록 (커서 페이지네이션, `?lastId=&size=20`) | USER |
| `PATCH` | `/api/notifications/{id}/read` | 단건 읽음 처리 | USER |
| `PATCH` | `/api/notifications/read-all` | 전체 읽음 처리 | USER |

---

## 관리자 REST API

> ADMIN JWT 인증 필요. `/api/admin/**`

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/admin/dashboard` | 주문 통계, 매출, 사용자 수, 저재고 목록 |
| `GET` | `/api/admin/users` | 전체 사용자 목록 (페이징) |
| `PATCH` | `/api/admin/users/{id}/role` | 권한 변경 (`USER` ↔ `ADMIN`) |
| `GET` | `/api/admin/orders` | 전체 주문 (`?status=`) |
| `GET` | `/api/admin/products` | 전체 상품 (ACTIVE + DISCONTINUED) |
| `GET` | `/api/admin/stats/orders` | 기간별 일별 주문·매출 통계 (`?from=&to=`) |
| `GET` | `/api/admin/stats/inventory` | 특정 날짜 재고 스냅샷 (`?date=`) |
| `GET` | `/api/admin/settings/low-stock-threshold` | 저재고 임계값 조회 |
| `PUT` | `/api/admin/settings/low-stock-threshold` | 저재고 임계값 변경 |

---

## 보안 정책 요약

| 기능 | 설정 |
|---|---|
| 로그인 Rate Limit | 15분 내 5회 실패 → 429 (Redis Lua 원자화) |
| 주문 생성 Rate Limit | 10회/분 (AOP, Redis) |
| 결제 확정 Rate Limit | 5회/분 (AOP, Redis) |
| JWT 블랙리스트 | 로그아웃 시 jti로 Redis 등록, 만료까지 유효 |
| Refresh Token | Rotation (30일 TTL), 회원 탈퇴 시 일괄 폐기 |
| IDOR 방어 | 주문·결제·배송·환불 소유권 검증 |
| 가격 조작 방어 | DB canonical price 강제 적용, 클라이언트 `unitPrice` 무시 |
| CORS | `cors.allowed-origins` 환경변수 (콤마 구분) |
| Webhook 서명 | `Toss-Signature` HMAC-SHA256 검증 |
