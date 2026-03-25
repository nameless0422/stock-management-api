# TODO — 프론트엔드 API 보완 항목

프론트엔드에서 UI를 구현할 때 현재 API로는 처리하기 어려운 항목들을 정리한 목록.

---

## 🔴 우선순위 높음 (구현 불가 수준)

### 1. ProductResponse에 재고 상태 및 별점 추가

**현재 문제**: 상품 목록 페이지에서 카드 하나를 렌더링하려면 최대 3번의 API 호출이 필요함

- `GET /api/products` — 상품 기본 정보
- `GET /api/inventory/{id}` × N — 품절 여부
- 리뷰 별점 — 별도 조회 필요

**필요한 작업**:

- [ ] `ProductResponse`에 `availableQuantity` (또는 `inStock: boolean`) 필드 추가
- [ ] `ProductResponse`에 `avgRating`, `reviewCount` 필드 추가
- [ ] `ProductService.getById()` / `getList()`에서 재고·리뷰 통계 함께 조회

---

## 🟠 우선순위 중간 (UX 저하)

### 2. 주문 상세 통합 응답

**현재 문제**: 주문 상세 페이지 렌더링 시 3번 요청 필요

- `GET /api/orders/{id}`
- `GET /api/payments/order/{id}`
- `GET /api/shipments/orders/{id}`

**필요한 작업**:

- [ ] `GET /api/orders/{id}/detail` 통합 엔드포인트 추가 (주문 + 결제 + 배송 정보 포함)
  - 또는 `OrderResponse`에 `payment`, `shipment` nested 필드 추가 (optional)

---

### 3. 내 쿠폰 목록 API

**현재 문제**: `GET /api/coupons`는 ADMIN 전용 → 일반 사용자는 보유 쿠폰 목록 조회 불가, 코드 직접 입력만 가능

**필요한 작업**:

- [ ] 쿠폰 사용 모델 설계 (사용자에게 쿠폰 발급하는 `coupon_usages` 또는 별도 발급 테이블)
- [ ] `GET /api/coupons/my` — 내가 사용할 수 있는 쿠폰 목록 (USER 권한)

---

### 4. 위시리스트 단건 상태 조회

**현재 문제**: 상품 상세 페이지의 하트 아이콘 상태를 표시하려면 `GET /api/wishlist` 전체를 미리 로드해야 함

**필요한 작업**:

- [ ] `GET /api/wishlist/{productId}` — 특정 상품의 위시리스트 추가 여부 반환 (`{ "wishlisted": true }`)

---

### 5. 프로필 수정 / 비밀번호 변경

**현재 문제**: `GET /me`, `DELETE /me`만 있고 수정 API 없음

**필요한 작업**:

- [ ] `PATCH /api/users/me` — 이름, 이메일 등 프로필 수정
- [ ] `PATCH /api/users/me/password` — 현재 비밀번호 확인 후 변경

---

### 6. 리뷰 수정 API

**현재 문제**: 리뷰 작성 후 수정 불가

**필요한 작업**:

- [ ] `PUT /api/products/{productId}/reviews/{reviewId}` — 리뷰 내용·별점 수정 (작성자 본인)

---

## 🟡 우선순위 낮음 (있으면 좋음)

### 7. 주문 아이템별 리뷰 작성 여부

**현재 문제**: 내 주문 목록에서 "리뷰 쓰기" 버튼 활/비활을 판단하는 정보 없음

**필요한 작업**:

- [ ] `OrderItemResponse`에 `hasReview: boolean` 필드 추가
  - 또는 `GET /api/orders/{id}/reviews` — 해당 주문의 리뷰 작성 현황 조회

---

### 8. 장바구니 아이템에 품절 상태 표시

**현재 문제**: `CartItemResponse`에 현재 재고 정보 없음 → 품절된 상품이 장바구니에 담겨 있을 때 UI 표시 어려움

**필요한 작업**:

- [ ] `CartItemResponse`에 `availableQuantity`, `isAvailable` 필드 추가

---

### 9. 상품 이미지 목록을 ProductResponse에 포함

**현재 문제**: 상품 상세 페이지에서 이미지 슬라이더를 만들려면 `GET /api/products/{id}/images` 별도 호출 필요

**필요한 작업**:

- [ ] `GET /api/products/{id}` 응답에 `images: List<ProductImageResponse>` 포함 (상세 조회 시만)

---

## 참고 — 현재 API 커버리지 요약

| 기능 | 상태 |
|------|------|
| 인증 (로그인/로그아웃/토큰 재발급) | ✅ |
| 상품 목록/검색 (Elasticsearch) | ✅ |
| 상품 상세 | ✅ |
| 카테고리 트리 | ✅ |
| 장바구니 | ✅ |
| 주문 생성/취소/목록/이력 | ✅ |
| TossPayments 결제/취소 | ✅ |
| 배송 조회 | ✅ |
| 환불 | ✅ |
| 리뷰 작성/조회/삭제 | ✅ |
| 위시리스트 추가/삭제/목록 | ✅ |
| 포인트 잔액/이력 | ✅ |
| 배송지 관리 | ✅ |
| 상품 재고 상태 (목록 연동) | ❌ |
| 별점 통계 (목록 연동) | ❌ |
| 내 쿠폰 목록 | ❌ |
| 프로필 수정 / 비밀번호 변경 | ❌ |
| 리뷰 수정 | ❌ |
| 위시리스트 단건 상태 조회 | ❌ |
