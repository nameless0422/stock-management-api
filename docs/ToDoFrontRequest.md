
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

---

## 2차 검토 — 시니어 프론트엔드 관점 추가 항목

---

### 🔴 필수 — 없으면 서비스 운영 불가

**17. 비밀번호 찾기/재설정 API 없음**

현재 `PATCH /api/users/me/password`(기존 비밀번호 필요)만 있다.
비밀번호를 잊은 사용자는 계정 복구 수단이 전혀 없다 — 실서비스에선 CS 문의 폭발.

```
필요: POST /api/auth/forgot-password   { email }
      POST /api/auth/reset-password    { token, newPassword }
흐름: 이메일로 재설정 링크 발송 → 토큰 검증 → 비밀번호 교체
```

**18. 배송지 수령인 정보 없음**

`DeliveryAddressResponse`에 `address`, `postalCode`는 있지만 `recipientName`, `recipientPhone`이 없다.
한국 택배는 수령인 이름·연락처가 운송장 필수 항목이다. 주문자 != 수령인(선물) 케이스도 흔하다.

```
현재: { id, address, detailAddress, postalCode, isDefault }
필요: { id, recipientName, recipientPhone, address, detailAddress, postalCode, isDefault }
```

---

### 🟠 중요 — N+1 또는 UX 치명적 결함

**19. 위시리스트 목록에 상품 상세 미포함 (N+1)**

`GET /api/wishlist` 응답: `{ id, productId, userId, createdAt }` — 상품명·가격·이미지·재고 없음.
위시리스트 페이지를 렌더링하려면 `productId` 개수만큼 `GET /api/products/{id}`를 반복 호출해야 한다.

```
개선: GET /api/wishlist 응답에 product 필드 포함
     { id, productId, createdAt, product: { name, price, thumbnailUrl, availableQuantity, status } }
```

**20. 위시리스트 페이지네이션 없음**

`GET /api/wishlist`는 `List<>` 반환 — 위시리스트가 100건 이상이면 전체를 한번에 불러온다.

```
개선: GET /api/wishlist?page=&size= → Page<WishlistResponse>
```

**21. 리뷰 별점 분포 통계 없음**

상품 상세 페이지의 별점 히스토그램(★5: 42건, ★4: 18건 ...) 구현 불가.
현재 `avgRating`·`reviewCount`만 있고 분포가 없다. 클라이언트에서 계산하려면 전체 리뷰를 다 가져와야 한다.

```
필요: GET /api/products/{id}/reviews/stats
     → { avgRating, reviewCount, distribution: { 1:n, 2:n, 3:n, 4:n, 5:n } }
```

**22. 주문 상세에 환불 정보 미포함**

`GET /api/orders/{id}/detail` 응답: `{ order, payment, shipment }` — `refund` 없음.
마이페이지 주문 상세에서 환불 진행 상황을 보려면 `paymentKey`로 `/api/refunds/payments/{paymentId}`를 추가 호출해야 한다.

```
개선: GET /api/orders/{id}/detail 응답에 refund 필드 추가
     { order, payment, shipment, refund }
```

**23. `OrderPreviewResponse` 필드 미흡**

현재 응답: `{ totalAmount, discountAmount, usedPoints, finalAmount }`.
결제 화면에서 필요한 항목이 누락되어 클라이언트가 직접 계산해야 한다.

```
현재: { totalAmount, discountAmount, usedPoints, finalAmount }
필요: { originalAmount, couponDiscount, pointDiscount,
        shippingFee, finalAmount, earnablePoints }
```
→ `couponDiscount`(쿠폰 할인액), `shippingFee`(배송비), `earnablePoints`(결제 시 적립 예정 포인트) 누락.

**24. 주문 취소 사유 없음**

`POST /api/orders/{id}/cancel` 요청 바디 없음 — 취소 사유를 받지 않는다.
운영 분석, CS 처리, 취소 이력 표시에 필요하다.

```
개선: POST /api/orders/{id}/cancel  Body: { reason: string (optional) }
```

---

### 🟡 중장기 — UX 완성도·신규 기능

**25. 검색 자동완성 API 없음**

검색창 입력 시 실시간 추천어(상품명 prefix 매칭)가 없다.
현재 `GET /api/products?q=` 전체 검색만 있어 키 입력마다 호출하기 부적합하다.

```
필요: GET /api/products/search/suggestions?q=티셔
     → { suggestions: ["티셔츠", "티셔츠 반팔", ...] }  (Elasticsearch prefix query)
```

**26. 소셜 로그인 없음**

한국 쇼핑몰에서 카카오·네이버 로그인은 사실상 필수다.
현재 username/password 방식만 있어 전환율에 직접적인 영향을 준다.

```
필요: GET  /api/auth/oauth2/{provider}          (provider: kakao | naver | google)
      GET  /api/auth/oauth2/{provider}/callback  (Redirect URI)
```

**27. 이메일 인증 없음**

회원가입 후 이메일 인증 단계가 없다.
스팸 계정 방지, 비밀번호 재설정(#17) 연동에 모두 필요하다.

```
필요: POST /api/auth/email/verify-send    { email }   (인증 메일 발송)
      POST /api/auth/email/verify-confirm { token }   (토큰 확인)
```

**28. `UserResponse`에 전화번호 없음**

마이페이지 프로필, 배송지 자동 채우기, 본인 인증에 필요하다.
현재 `{ id, username, email, role, createdAt, pointBalance }` — `phoneNumber` 없음.

```
개선: UserResponse에 phoneNumber: String 추가
     PATCH /api/users/me Body에 phoneNumber 수정 지원
```

**29. 포인트 적립 예정 내역 없음**

결제 완료 후 "이 주문에서 N포인트 적립 예정" 표시가 불가하다.
현재 `GET /api/points/history`는 이미 확정된 이력만 반환한다.

```
개선: PointTransactionResponse에 status (PENDING | CONFIRMED | EXPIRED) 추가
     또는: GET /api/points/pending → 적립 예정 포인트 목록
```

**30. 상품 재입고 알림 신청 없음**

품절 상품(`availableQuantity=0`) 페이지에서 "재입고 알림 신청" 버튼을 구현할 수 없다.

```
필요: POST   /api/products/{id}/restock-notify    (알림 신청)
      DELETE /api/products/{id}/restock-notify    (신청 취소)
      GET    /api/users/me/restock-notifications  (신청 목록)
```

**31. 최근 본 상품 없음**

"최근 본 상품" 위젯은 쇼핑몰 체류 시간을 높이는 핵심 기능이다.
현재 상품 조회 이력을 서버에서 관리하지 않아 클라이언트 localStorage에만 의존해야 한다 (기기 간 공유 불가).

```
필요: POST /api/users/me/recently-viewed       { productId }  (조회 시 자동 기록)
      GET  /api/users/me/recently-viewed?size=10
```

---

### 2차 요약

| 우선순위 | 항목 | 이유 |
|---|---|---|
| 🔴 필수 | 비밀번호 찾기/재설정 | 사용자 계정 복구 수단 전무 |
| 🔴 필수 | 배송지 수령인 정보 | 운송장 필수 항목 누락 |
| 🟠 중요 | 위시리스트 상품 상세 포함 | N+1 요청 방지 |
| 🟠 중요 | 위시리스트 페이지네이션 | 대량 데이터 처리 |
| 🟠 중요 | 리뷰 별점 분포 통계 | 상품 상세 핵심 UI |
| 🟠 중요 | 주문 상세에 환불 정보 포함 | 마이페이지 round-trip 제거 |
| 🟠 중요 | `OrderPreviewResponse` 필드 보완 | 결제 화면 정확한 정보 표시 |
| 🟠 중요 | 주문 취소 사유 | CS/운영 분석 |
| 🟡 중장기 | 검색 자동완성 | 검색 UX |
| 🟡 중장기 | 소셜 로그인 | 전환율 |
| 🟡 중장기 | 이메일 인증 | 보안·스팸 방지 |
| 🟡 중장기 | `UserResponse` 전화번호 | 프로필 완성도 |
| 🟡 중장기 | 포인트 적립 예정 내역 | 결제 UX |
| 🟡 중장기 | 재입고 알림 신청 | 구매 전환율 |
| 🟡 중장기 | 최근 본 상품 | 체류 시간·재방문 유도 |

---

## 2차 정오표

> 2차 작성 시 탐색 오류로 잘못 파악한 항목을 정정합니다.

**#18 배송지 수령인 정보 없음** → ✅ 이미 구현됨
`DeliveryAddressResponse`에 `recipient`, `phone`, `alias`, `zipCode`, `address1`, `address2` 이미 포함. 해당 항목 무효.

**#23 `OrderPreviewResponse` 필드 미흡** → ✅ 이미 구현됨
`OrderPreviewResponse`에 `originalAmount`, `couponDiscount`, `pointDiscount`, `shippingFee`, `finalAmount`, `earnablePoints` 모두 포함. 해당 항목 무효.

**#19 위시리스트 N+1** → 🔶 부분 구현됨
`WishlistResponse`에 `productName`, `productPrice`, `thumbnailUrl`은 포함되어 있으나 `availableQuantity`(재고), `status`(판매 상태)는 없음. 품절/단종 배지 표시 불가.

---

## 3차 검토 — 구조적 결함 및 UX 완성도

---

### 🔴 구조적 한계 — 카테고리 전체에 영향

**32. 상품 옵션/변형(variants) 없음**

`Product` 1개 = SKU 1개. 색상·사이즈 선택 개념이 없다.
의류·신발·가방 카테고리를 다루는 순간 구현 불가. "블랙 M", "화이트 L"을 각각 다른 상품으로 등록해야 하는데, 단일 상품 상세 페이지에서 옵션 선택 → 재고 확인 → 장바구니 추가 플로우가 원천 불가능하다.

```
필요: Product ─┬─ ProductOption (color, size ...)
               └─ ProductVariant (optionValues 조합, 자체 price/sku/inventory)

GET /api/products/{id} 응답에 variants 배열 포함
POST /api/orders → OrderItemRequest.variantId
```

---

### 🟠 중요 — 결제·주문 흐름 결함

**33. 결제 실패 후 재결제 불가**

`V8 migration`에서 `payments.order_id UNIQUE` 제약이 추가되었다. 결제가 FAILED 상태가 되면 동일 주문으로 `POST /api/payments/prepare`를 재호출해도 UNIQUE 충돌이 발생한다. 프론트에서 "다시 결제하기" 버튼을 구현할 방법이 없다.

```
현재: payments.order_id UNIQUE → FAILED 상태에서 재결제 시 DB 제약 위반
필요: 아래 중 하나 선택
  A) FAILED 결제를 PENDING으로 reset 후 재시도 허용
  B) payments.order_id UNIQUE 제거 + (order_id, status) 복합 제약으로 교체
  C) POST /api/payments/{orderId}/retry 엔드포인트 추가
```

**34. `OrderResponse`에 배송지 스냅샷 없음**

`OrderResponse.deliveryAddressId`만 있다. 사용자가 이후 배송지를 삭제하면 `delivery_addresses.ON DELETE SET NULL`로 인해 `deliveryAddressId`가 null이 된다. 주문 이력에서 실제 배송된 주소를 볼 수 없는 상황이 발생한다.

```
필요: OrderResponse에 snapshotAddress 필드 추가
     { recipientName, phone, address1, address2, zipCode }
     → 주문 생성 시점 주소를 order_delivery_snapshots 테이블에 별도 저장
```

**35. 주문 아이템별 부분 취소 없음**

`POST /api/orders/{id}/cancel`은 전체 주문 취소만 가능하다. "10종류 상품 중 품절된 3개만 취소" 같은 부분 취소 플로우를 구현할 수 없다. 쇼핑몰 운영 중 흔히 발생하는 시나리오다.

```
필요: POST /api/orders/{id}/items/cancel
     Body: { itemIds: [1, 3, 5], reason: "..." }
```

**36. `WishlistResponse`에 재고·판매 상태 미포함**

`productName`, `productPrice`, `thumbnailUrl`은 있지만 `availableQuantity`, `status`가 없다.
위시리스트에 담긴 상품이 품절/단종되었을 때 "품절" 오버레이 배지를 표시할 수 없다.

```
개선: WishlistResponse에 추가
     availableQuantity: int
     productStatus: ProductStatus (ACTIVE | DISCONTINUED)
```

**37. 내 결제 목록에 주문 상품 정보 없음**

`GET /api/payments/my` 응답인 `PaymentResponse`는 `orderId`만 있다. 결제 내역 페이지("무엇을 결제했나")를 표시하려면 각 `orderId`로 `GET /api/orders/{id}`를 추가 호출해야 한다.

```
개선: PaymentResponse에 orderSummary 필드 추가
     { orderName, itemCount, thumbnailUrl }
     → Payment 생성 시 snapshots 저장 또는 JOIN 쿼리로 포함
```

---

### 🟡 중장기 — UX 완성도

**38. 공개 쿠폰 다운로드 목록 없음**

`GET /api/coupons`는 ADMIN 전용이다. 일반 사용자에게 "다운로드 가능한 쿠폰 목록" 페이지를 보여줄 수 없다. 현재 사용자는 코드를 이미 알고 있어야만 `POST /api/coupons/claim`으로 등록 가능하다.

```
필요: GET /api/coupons/public?page=&size=
     → 공개 쿠폰만 반환 (isPublic=true, active=true, 기간 유효, 수량 여유)
     → "쿠폰 다운로드" 버튼: POST /api/coupons/claim { couponCode }
```

**39. 상품 정렬 옵션 부족**

현재 ES 정렬: `price_asc`, `price_desc`, `newest`, `relevance` 4가지뿐이다.
"판매량 순", "리뷰 많은 순" 없음 — 쇼핑몰에서 가장 많이 쓰이는 정렬 2가지가 빠져 있다.

```
개선: sort 파라미터에 추가
     popular  → 누적 판매량 기준 (daily_order_stats 활용)
     review   → reviewCount 내림차순
```

**40. 배송 예상 도착일 없음**

`ShipmentResponse`에 `shippedAt`, `deliveredAt`(실제 배송 완료)은 있지만 `estimatedDeliveryAt`(예상 도착일)이 없다. "예상 배송일: 4월 20일(월)" 표시 불가.

```
개선: ShipmentResponse에 estimatedDeliveryAt: LocalDate 추가
     PATCH /api/shipments/orders/{orderId}/ship Body에 estimatedDeliveryAt 포함
```

**41. 홈 화면 전용 집계 API 없음**

홈 화면 구성에 필요한 데이터(신상품, 인기상품, 진행 중인 이벤트/배너, 추천 카테고리)를 모으려면 최소 3~4번의 개별 API를 병렬 호출해야 한다. SSR/SSG 환경에서는 직접적인 성능 문제가 된다.

```
필요: GET /api/home
     → { banners, newArrivals, popularProducts, featuredCategories }
     → 서버에서 Redis 캐시 기반으로 단일 응답 반환 (TTL: 5~10분)
```

**42. 결제 수단 상세 정보 없음**

`PaymentResponse.method`는 단순 String이다. 카드 결제인 경우 카드사, 카드 번호 뒷 4자리가 없어 "KB국민카드 ****1234" 표시 불가. 가상계좌인 경우 은행명·계좌번호 없음.

```
개선: PaymentResponse에 methodDetail 필드 추가 (nullable)
     카드:     { cardCompany, cardNumber(마스킹), installmentPlanMonths }
     가상계좌: { bank, accountNumber, dueDate }
     계좌이체: { bank }
```

---

### 3차 요약

| 우선순위 | 항목 | 이유 |
|---|---|---|
| 🔴 구조적 | 상품 옵션/variants 없음 | 의류·신발 등 카테고리 구현 불가 |
| 🟠 중요 | 결제 실패 후 재결제 불가 | order_id UNIQUE 제약 — 사용자 이탈 |
| 🟠 중요 | OrderResponse 배송지 스냅샷 누락 | 주문 이력 데이터 유실 |
| 🟠 중요 | 주문 아이템별 부분 취소 없음 | 일상적 운영 시나리오 미지원 |
| 🟠 중요 | WishlistResponse 재고/상태 미포함 | 품절 배지 표시 불가 |
| 🟠 중요 | 내 결제 목록 주문 상품 정보 없음 | 결제 내역 N+1 |
| 🟡 중장기 | 공개 쿠폰 다운로드 목록 | 마케팅 쿠폰 배포 불가 |
| 🟡 중장기 | 상품 정렬 옵션 부족 | popular/review 정렬 없음 |
| 🟡 중장기 | 배송 예상 도착일 없음 | 배송 UX |
| 🟡 중장기 | 홈 화면 집계 API 없음 | SSR 성능 |
| 🟡 중장기 | 결제 수단 상세 정보 없음 | 결제 내역 표시 |

---

## 4차 검토 — API 인터페이스 결함 및 데이터 누락

---

### 🔴 필수 — 개발 시작 전 합의 필요

**43. 에러 응답에 머신-리더블 코드 없음**

`ApiResponse` 에러 응답 구조: `{ success: false, message: "재고가 부족합니다." }`.
`message`는 사람이 읽는 한국어 문자열이고 기계가 읽는 에러 코드가 없다.
프론트에서 에러 종류별로 다른 UX(토스트/모달/인라인 메시지)를 적용하려면 `message` 텍스트를 직접 비교해야 한다 — i18n 불가, 문자열 변경 시 프론트 코드 깨짐.

```
현재: { success: false, message: "재고가 부족합니다." }
필요: { success: false, code: "INSUFFICIENT_STOCK", message: "재고가 부족합니다." }
     → code는 ErrorCode enum name() 그대로 사용
```

**44. Spring Security 에러와 일반 에러 응답 형식 불일치**

`GlobalExceptionHandler`는 `BusinessException` 등을 `ApiResponse` 형식으로 처리하지만,
Spring Security 필터 레이어의 401(미인증)/403(권한 없음) 에러는 `AuthenticationEntryPoint`/`AccessDeniedHandler`가 별도 설정되지 않아 Spring Boot 기본 형식(`{ timestamp, status, error, path }`)으로 반환된다.
프론트 에러 인터셉터가 두 가지 형식을 모두 처리해야 하는 문제가 생긴다.

```
현재: 
  401/403 → { timestamp: ..., status: 403, error: "Forbidden", path: "/api/orders" }
  비즈니스 에러 → { success: false, message: "..." }

필요: 모든 에러 → { success: false, code: "...", message: "..." }
  → SecurityConfig에 authenticationEntryPoint/accessDeniedHandler 추가
```

---

### 🟠 중요 — 이미지 및 데이터 누락

**45. `OrderItemResponse`에 썸네일 없음**

`OrderItemResponse` 필드: `id`, `productId`, `productName`, `quantity`, `unitPrice`, `subtotal`, `hasReview`.
`thumbnailUrl`이 없다. 주문 목록·상세 페이지에서 상품 이미지를 표시하려면 `productId`마다 `GET /api/products/{id}`를 반복 호출해야 한다.
주문 당시 이미지가 이후 변경될 수도 있으므로 스냅샷 저장이 올바른 방향이다.

```
개선: OrderItemResponse에 추가
     thumbnailUrl: String  (주문 당시 thumbnailUrl 스냅샷)
```

**46. `CartItemResponse`에 썸네일 없음**

`CartItemResponse` 필드: `productId`, `productName`, `unitPrice`, `quantity`, `subtotal`, `availableQuantity`, `isAvailable`.
장바구니 화면에서 상품 이미지를 표시할 방법이 없다. `productId`로 별도 조회 필요.

```
개선: CartItemResponse에 추가
     thumbnailUrl: String
```

**47. 장바구니 가격 변동 감지 없음**

`CartItemResponse.unitPrice`는 `Product.price`를 실시간으로 참조한다 (스냅샷 아님).
장바구니에 담은 이후 가격이 인하/인상되어도 프론트에서 이를 감지할 수 없다.
"이 상품의 가격이 변경되었습니다 (10,000원 → 8,000원)" 알림 구현 불가.

```
개선: CartItemResponse에 추가
     originalPrice: BigDecimal   (장바구니에 담을 당시 가격)
     currentPrice: BigDecimal    (현재 가격)
     priceChanged: Boolean       (originalPrice != currentPrice)
```

**48. `CategoryResponse`에 상품 수 없음**

네비게이션 메뉴나 카테고리 탐색 페이지에서 "상의 (234)", "하의 (189)" 같은 상품 수 표시가 일반적이다.
현재 `CategoryResponse`에는 `id`, `name`, `description`, `parentId`, `children` 만 있다.

```
개선: CategoryResponse에 productCount: long 추가
     (하위 포함 여부를 boolean includeChildren 파라미터로 선택)
```

**49. `MyCouponResponse`에 사용 주문 정보 없음**

사용된 쿠폰이 "어떤 주문에서 얼마나 할인되었는지" 알 수 없다.
쿠폰 이력 페이지("2024-04-01 주문 #12345에서 3,000원 할인")를 구현하려면 별도 데이터가 필요하다.

```
개선: MyCouponResponse에 추가 (사용된 쿠폰인 경우)
     usedAt: LocalDateTime
     usedOrderId: Long
     actualDiscountAmount: BigDecimal
```

---

### 🟡 중장기 — API 설계 일관성

**50. 페이지네이션 전략 혼재 (Page vs CursorPage)**

주문 목록 조회가 두 가지 방식으로 중복 존재한다:
- `GET /api/orders` → `Page<OrderResponse>` (offset 기반)
- `GET /api/orders/scroll` → `CursorPage<OrderResponse>` (커서 기반)

재고 이력은 `CursorPage`, 상품/카테고리/쿠폰은 `Page`. 어떤 API가 어떤 방식인지 패턴이 없다.
프론트에서 API마다 다른 페이지네이션 로직을 작성해야 하고, 무한 스크롤과 페이지 번호 UI를 각각 구현해야 한다.

```
개선 방향 합의 필요:
  - 목록 기본: Page (어드민 테이블, 정렬 가능한 목록)
  - 무한 스크롤: CursorPage (피드형 목록)
  - GET /api/orders/scroll 제거 또는 GET /api/orders에 통합
```

**51. 재고 수량 직접 노출 정책 미비**

`ProductResponse.availableQuantity`, `CartItemResponse.availableQuantity` 등에서 정확한 재고 숫자를 그대로 노출한다.
경쟁사가 API를 통해 재고 현황을 파악하거나, 사용자가 "재고 9개" 보다 "품절 임박" 뱃지가 더 구매 전환에 효과적이다.

```
개선 방향 합의 필요:
  A) 임계값 이하만 숫자 표시: 10개 이상 → null, 10개 미만 → 실제 숫자
  B) 단계별 표시: IN_STOCK | LOW_STOCK | OUT_OF_STOCK enum 반환
  C) 현행 유지 (정확한 숫자 노출)
```

**52. 부분 환불 지원 여부 불명확**

`POST /api/payments/{paymentKey}/cancel` API가 전액 취소만 지원하는지, 금액 지정 부분 취소도 가능한지 문서·응답만으로 판단 불가.
다품목 주문에서 특정 상품만 환불하는 UX를 구현하려면 부분 취소 지원이 필수다.

```
명확화 필요: POST /api/payments/{paymentKey}/cancel Body에
     amount: BigDecimal (지정 안 하면 전액, 지정 시 부분 취소)
     → Toss Payments API 부분 취소 지원 여부와 연동 필요
```

**53. 인앱 알림 시스템 없음**

주문 상태 변경(결제 완료, 배송 출고 등)을 실시간으로 사용자에게 전달할 방법이 없다.
이메일 발송(`EmailService`)은 있지만 앱/웹 내 알림 UI(헤더 벨 아이콘, 읽지 않은 알림 수)를 구현할 수 없다.

```
필요: 
  GET  /api/notifications?read=false&page=
  POST /api/notifications/{id}/read
  GET  /api/notifications/unread-count
  → SSE(Server-Sent Events) 또는 주기적 polling 방식 선택 필요
```

---

### 4차 요약

| 우선순위 | 항목 | 핵심 이유 |
|---|---|---|
| 🔴 필수 | 에러 응답에 `errorCode` 없음 | 에러 종류 구분 불가 — i18n·조건부 UX 불가 |
| 🔴 필수 | Security 에러 형식 불일치 | 인터셉터 구현 복잡 |
| 🟠 중요 | `OrderItemResponse` 썸네일 없음 | 주문 목록 이미지 N+1 |
| 🟠 중요 | `CartItemResponse` 썸네일 없음 | 장바구니 이미지 N+1 |
| 🟠 중요 | 장바구니 가격 변동 감지 없음 | 스냅샷 구조 부재 |
| 🟠 중요 | `CategoryResponse` 상품 수 없음 | 네비게이션 UX |
| 🟠 중요 | `MyCouponResponse` 사용 주문 없음 | 쿠폰 이력 UX |
| 🟡 중장기 | 페이지네이션 전략 혼재 | 프론트 구현 복잡성 |
| 🟡 중장기 | 재고 수량 직접 노출 정책 | 비즈니스 정책 결정 필요 |
| 🟡 중장기 | 부분 환불 지원 여부 불명확 | 다품목 환불 UX |
| 🟡 중장기 | 인앱 알림 시스템 없음 | 실시간 상태 알림 |
