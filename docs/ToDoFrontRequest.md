
---

## 프론트엔드 관점 API 검토 — 쇼핑몰

### 🔴 결제 흐름 — 없으면 구현 불가

**1. `PaymentPrepareResponse`에 Toss 결제창 필수 필드 누락**

현재 응답: `tossOrderId`, `amount`만 반환.
Toss 결제창(`loadTossPayments()`) 초기화에는 `orderName`(상품명 요약), `customerName`(구매자 이름), `customerEmail`이 필수 파라미터다. 현재 프론트가 이걸 채우려면 `/api/orders/{id}` → `/api/users/me`를 추가로 2번 호출해야 한다.

```
현재: { tossOrderId, amount }
필요: { tossOrderId, amount, orderName, customerName, customerEmail }
```

**2. 주문 최종 금액 계산 API 없음**

주문 생성 전 "쿠폰 + 포인트 복합 적용 시 최종 결제 금액" 미리보기가 없다. `/api/coupons/validate`는 쿠폰 할인만 계산하고 포인트를 반영하지 않는다. 결제 화면에서 "쿠폰 적용 + 포인트 5,000P 사용 시 최종 금액은?" 같은 계산을 클라이언트에서 직접 해야 하는데, 쿠폰 할인 계산 로직(PERCENTAGE cap, minimumOrderAmount 등)이 서버에만 있다.

```
필요: POST /api/orders/preview
     { items, couponCode, usePoints, deliveryAddressId }
     → { originalAmount, couponDiscount, pointDiscount, shippingFee, finalAmount, earnablePoints }
```

---

### 🟠 목록 조회 — N+1 요청을 강요하는 구조

**3. 주문 목록에 배송 상태 없음**

`GET /api/orders`는 `OrderResponse`(주문 정보)만 반환한다. "내 주문 목록" 페이지에서 각 주문의 배송 상태(준비중/배송중/배송완료)를 표시하려면 주문 1건당 `GET /api/shipments/orders/{orderId}` 1번씩 추가 호출이 발생한다. 주문 20건 목록 = 최대 21번 요청.

`OrderResponse` 또는 `OrderDetailResponse`를 목록 API에서도 선택적으로 내려주거나, `shipmentStatus` 필드 하나만 추가해도 해결된다.

**4. 상품 목록에 위시리스트 여부(`wishlisted`) 없음**

`GET /api/products` 응답에는 위시리스트 여부가 없다. 상품 카드마다 ♥ 아이콘 상태를 표시하려면 로그인 사용자 기준으로 `GET /api/wishlist` 전체 목록을 먼저 불러온 뒤 클라이언트에서 교차 대조해야 한다. 위시리스트 수천 건 보유 사용자라면 응답 페이로드도 크다.

```
개선: GET /api/products 응답에 wishlisted: boolean (비로그인 시 null 또는 false)
```

**5. 내 결제 목록 API 없음**

현재 결제 조회는 `paymentKey` 또는 `orderId` 단건 조회만 있다. "내 결제 내역" 페이지(카드사 청구 확인용)를 구현하려면 주문 목록을 먼저 불러온 뒤 각 주문별 결제를 조회해야 한다.

```
필요: GET /api/payments/my?page=&size=&status=
```

**6. 내 환불 목록 API 없음**

`GET /api/refunds/{id}`, `GET /api/refunds/payments/{paymentId}` 단건만 있고 목록이 없다.

```
필요: GET /api/refunds/my?page=&size=
```

---

### 🟡 상품/리뷰 — 완성도 문제

**7. 상품 상세에 "리뷰 작성 가능 여부" 없음**

리뷰 작성 버튼 노출 조건: 로그인 + 구매 완료 + 미작성. 현재 API로는 이 조건을 확인하려면 `GET /api/users/me/orders`를 모두 가져와 해당 상품이 포함된 CONFIRMED 주문이 있는지 + 이미 리뷰를 작성했는지(`PUT` 시도 시 에러로만 확인 가능)를 클라이언트에서 판단해야 한다.

```
개선: GET /api/products/{id} 응답에 canReview: boolean (로그인 시만 계산, 비로그인은 false)
     또는: GET /api/users/me/reviewable-products
```

**8. `ReviewResponse`에 작성자 정보 없음**

`userId`만 있고 `username`이 없다. 리뷰 목록에서 "작성자: 홍**" 같은 마스킹 표시가 일반적인데, 현재는 불가능하다. 닉네임/마스킹 username 필드 추가 필요.

**9. 리뷰 정렬/필터 없음**

`GET /api/products/{productId}/reviews`는 최신순 고정이다. "별점 높은 순", "별점 낮은 순", "별점 N점만 보기" 필터가 없다. 리뷰 수가 많은 상품에서 UX 부족.

**10. 내가 작성한 리뷰 목록 없음**

마이페이지 > 내 리뷰 관리 화면 구현 불가.

```
필요: GET /api/users/me/reviews?page=&size=
```

---

### 🟡 사용자 UX — 헤더/마이페이지

**11. `GET /api/users/me` 응답에 포인트 잔액 없음**

로그인 후 헤더에 포인트 잔액을 표시하는 패턴이 일반적이다. 현재는 페이지 진입마다 `GET /api/points/balance`를 별도 호출해야 한다. `UserResponse`에 `pointBalance: long` 하나만 추가하면 초기 렌더링 1 round-trip으로 해결된다.

**12. 쿠폰 목록 필터 없음**

`GET /api/coupons/my`는 전체 발급 쿠폰을 반환한다. "사용 가능한 것만", "만료된 것만" 탭 구현을 위한 `?usable=true` 같은 필터가 없다. 현재는 전체를 받아서 `isUsable`로 클라이언트 필터링해야 한다.

---

### 🟡 기타 구현 시 불편한 부분

**13. 장바구니 선택 결제 불가**

`POST /api/cart/checkout`은 장바구니 전체를 주문으로 전환한다. 일부 상품만 선택해서 결제하는 기능이 없다. 선택 항목 ID 목록을 받는 파라미터 추가 필요.

**14. 배송 추적 URL 없음**

`ShipmentResponse`에 `carrier`(택배사)와 `trackingNumber`는 있지만 "배송 조회 바로가기" 딥링크 URL이 없다. 택배사별 트래킹 URL 매핑은 서버에서 해주는 게 관리가 편하다.

```
개선: trackingUrl: "https://www.cjlogistics.com/ko/tool/parcel/tracking?gnbInvcNo=1234"
```

**15. 상품 이미지 `sortOrder` 관리 API 없음**

이미지 등록 / 삭제는 있지만 순서 변경(드래그 앤 드롭 정렬) API가 없다. `PATCH /api/products/{id}/images/order` 같은 순서 일괄 업데이트 필요.

**16. 카테고리 하위 포함 상품 검색 불가**

`GET /api/products?category=패션`으로 검색하면 "패션" 정확 일치만 동작한다. "패션 > 상의 > 티셔츠"처럼 하위 카테고리를 포함해서 검색하려면 프론트에서 카테고리 트리를 먼저 가져와 하위 ID를 모두 수집한 뒤 여러 번 요청해야 한다.

```
개선: ?categoryId=5&includeChildren=true  (또는 categoryId=5로 하위 포함 자동 처리)
```

---

### 요약 우선순위

| 우선순위 | 항목 | 이유 |
|---|---|---|
| 🔴 필수 | `PaymentPrepareResponse` 필드 보완 | 결제창 렌더링 불가 |
| 🔴 필수 | 주문 금액 미리보기 API | 결제 전 UX 핵심 |
| 🟠 중요 | 주문 목록에 `shipmentStatus` 포함 | N+1 요청 방지 |
| 🟠 중요 | 내 결제/환불 목록 API | 마이페이지 기본 기능 |
| 🟠 중요 | 상품 상세 `canReview` 필드 | 리뷰 버튼 노출 조건 |
| 🟠 중요 | `ReviewResponse` 작성자 username | 리뷰 목록 표시 |
| 🟡 중장기 | 상품 목록 `wishlisted` 포함 | 목록 N+1 제거 |
| 🟡 중장기 | 내 리뷰 목록 API | 마이페이지 완성도 |
| 🟡 중장기 | `UserResponse`에 포인트 잔액 포함 | 헤더 표시 최적화 |
| 🟡 중장기 | 쿠폰 목록 usable 필터 | 쿠폰 탭 UX |
| 🟡 중장기 | 카테고리 하위 포함 검색 | 상품 탐색 UX |
| 🟡 중장기 | 배송 트래킹 URL | 배송 조회 UX |