
---

## 프론트엔드 관점 API 검토 — 쇼핑몰

> **전체 16개 항목 모두 구현 완료** (1차·2차·3차 커밋)

### 🔴 결제 흐름

**~~1. `PaymentPrepareResponse`에 Toss 결제창 필수 필드 누락~~ ✅ 완료 (1차)**

`orderName`, `customerName`, `customerEmail` 필드 추가.
응답: `{ tossOrderId, amount, orderName, customerName, customerEmail }`

**~~2. 주문 최종 금액 계산 API 없음~~ ✅ 완료 (1차)**

`POST /api/orders/preview` 구현.
`{ items, couponCode, usePoints, deliveryAddressId }` → `{ originalAmount, couponDiscount, pointDiscount, shippingFee, finalAmount, earnablePoints }`

---

### 🟠 목록 조회

**~~3. 주문 목록에 배송 상태 없음~~ ✅ 완료 (1차·2차)**

`OrderResponse.shipmentStatus` 추가. `findStatusMapByOrderIds()` 배치 쿼리로 N+1 방지.

**~~4. 상품 목록에 위시리스트 여부(`wishlisted`) 없음~~ ✅ 완료 (2차)**

`GET /api/products` 응답에 `wishlisted: Boolean` 포함 (비로그인 시 null).

**~~5. 내 결제 목록 API 없음~~ ✅ 완료 (1차)**

`GET /api/payments/my?page=&size=&status=` 구현.

**~~6. 내 환불 목록 API 없음~~ ✅ 완료 (1차)**

`GET /api/refunds/my?page=&size=` 구현.

---

### 🟡 상품/리뷰

**~~7. 상품 상세에 "리뷰 작성 가능 여부" 없음~~ ✅ 완료 (1차)**

`GET /api/products/{id}` 응답에 `canReview: Boolean` 추가 (로그인 시 계산, 비로그인 시 null).

**~~8. `ReviewResponse`에 작성자 정보 없음~~ ✅ 완료 (1차)**

`ReviewResponse.username` 추가 (마스킹). `buildUsernameMap()` 배치 조회로 N+1 방지.

**~~9. 리뷰 정렬/필터 없음~~ ✅ 완료 (3차)**

`GET /api/products/{productId}/reviews?rating=&sort=` 지원. 기본 정렬 `createdAt desc`.

**~~10. 내가 작성한 리뷰 목록 없음~~ ✅ 완료 (1차)**

`GET /api/users/me/reviews?page=&size=` 구현.

---

### 🟡 사용자 UX

**~~11. `GET /api/users/me` 응답에 포인트 잔액 없음~~ ✅ 완료 (1차)**

`UserResponse.pointBalance: Long` 추가.

**~~12. 쿠폰 목록 필터 없음~~ ✅ 완료 (1차)**

`GET /api/coupons/my?usable=true` 지원.

---

### 🟡 기타

**~~13. 장바구니 선택 결제 불가~~ ✅ 완료 (3차)**

`POST /api/cart/checkout` 요청에 `selectedProductIds: List<Long>` 추가. null=전체 결제, 지정 시 선택 상품만 주문 후 나머지 장바구니 유지.

**~~14. 배송 추적 URL 없음~~ ✅ 완료 (1차)**

`ShipmentResponse.trackingUrl` 추가. CJ대한통운·한진택배·롯데택배·우체국택배 자동 매핑. 미지원 배송사는 null.

**~~15. 상품 이미지 `sortOrder` 관리 API 없음~~ ✅ 완료 (3차)**

`PATCH /api/products/{productId}/images/order` 구현. `[{ imageId, displayOrder }]` 배열로 일괄 변경.

**~~16. 카테고리 하위 포함 상품 검색 불가~~ ✅ 완료 (2차)**

`GET /api/products?categoryId=5&includeChildren=true` 지원.

---

### 구현 완료 요약

| 항목 | 커밋 |
|---|---|
| `PaymentPrepareResponse` 필드 보완 | 1차 |
| 주문 금액 미리보기 API | 1차 |
| 주문 목록 `shipmentStatus` | 1차·2차 |
| 내 결제 목록 API | 1차 |
| 내 환불 목록 API | 1차 |
| 상품 상세 `canReview` | 1차 |
| 리뷰 `username` 마스킹 | 1차 |
| 내 리뷰 목록 API | 1차 |
| `UserResponse` 포인트 잔액 | 1차 |
| 쿠폰 `usable` 필터 | 1차 |
| 배송 추적 URL | 1차 |
| 상품 목록 `wishlisted` | 2차 |
| 카테고리 하위 포함 검색 | 2차 |
| 리뷰 정렬/필터 | 3차 |
| 장바구니 선택 결제 | 3차 |
| 이미지 순서 변경 API | 3차 |
