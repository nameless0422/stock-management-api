# 코드 리뷰 계획 (최종)

> 도메인별로 리뷰 범위·체크리스트·미해결 TODO 항목을 정리한다.
> 완료된 개선 사항은 `docs/IMPROVEMENTS.md` 참조.
> Phase 1~6 리뷰 완료. Phase 7 리뷰 완료 — 22개 해결 확인, 7개 신규 발견.

---

## 리뷰 우선순위 기준

| 등급 | 기준 | 예시 |
|------|------|------|
| P0 | 금전 손실·보안 취약점 | 결제 이중 처리, IDOR, 인증 우회 |
| P1 | 데이터 정합성·운영 장애 | 트랜잭션 경합, 재고 불일치, 스케줄러 오류 |
| P2 | 성능·코드 품질 | N+1, 불필요한 DB 조회, God Object |
| P3 | 유지보수·확장성 | API 버전, 코드 중복, DTO 검증 누락 |

---

## 이전 리뷰 요약 (Phase 1~6 완료)

| Phase | 범위 | 상태 |
|-------|------|------|
| ~~1~~ | 핵심 트랜잭션 (Payment, Order, Inventory) | ✅ 완료 → TODO #113~#133 |
| ~~2~~ | 보안·인증 (Security, User) | ✅ 완료 → TODO #134~#143 |
| ~~3~~ | 부가 도메인 (Coupon, Refund, Shipment, Point) | ✅ 완료 → TODO #144~#148 |
| ~~4~~ | 상품·카탈로그 (Product, Category, Image, Review, Wishlist) | ✅ 완료 → TODO #149~#158 |
| ~~5~~ | 공통 인프라 (Outbox, Exception, Cache, RateLimit) | ✅ 완료 → TODO #159~#161 |
| ~~6~~ | DB·코드 품질 (Flyway, Admin, 크로스커팅) | ✅ 완료 → TODO #162~#163 |

**전체 통계**: Phase 1~6에서 51개 항목 발견 (#113~#163), 647개 테스트 전체 통과.

---

## ~~Phase 7 — 미해결 TODO 정리 + 신규 발견~~ ✅ 리뷰 완료

> 기존 TODO.md 잔여 38개 항목 + 최신 코드 탐색.
> **22개 해결 확인**, **11개 미해결 잔존**, **7개 신규 발견** → TODO #164~#170.

---

### 7-1. 데이터 정합성·트랜잭션 — 리뷰 결과

| # | 항목 | 결과 |
|---|------|------|
| ~~130~~ | 포인트+쿠폰 초과 할인 미검증 | ✅ **해결** — 명시적 예외로 차단 |
| ~~146~~ | PointTransaction UNIQUE 제약 부재 | ✅ **해결** — V41 마이그레이션 적용 |
| ~~131~~ | CartService.addOrUpdate() 동시 500 | ✅ **해결** — DataIntegrityViolationException catch |
| ~~109~~ | Inventory releaseReservation 음수 클램핑 | ✅ **해결** — 상태 불일치 예외로 강화 |
| ~~126~~ | Inventory amount ≤ 0 미검증 | ✅ **해결** — 5개 메서드 모두 가드 추가 |
| ~~145~~ | UserPoint amount ≤ 0 미검증 | ✅ **해결** — 3개 메서드 가드 추가 |
| **164** | RefundService.getByPaymentId() 다건 환불 500 | ❌ **신규** — `findByPaymentId()` 단건 → 부분 취소 2회+ 시 500 |
| **165** | cancelPendingOrdersByUser() 전체 롤백 | ❌ **신규** — deactivate 트랜잭션 전파로 N번째 실패 시 전체 롤백 |
| **166** | PointService.refundByOrder() UK 충돌 미처리 | ❌ **신규** — V41 UNIQUE 추가 후 멱등성 체크 누락 |

---

### 7-2. 보안 — 리뷰 결과

| # | 항목 | 결과 |
|---|------|------|
| ~~107~~ | 마지막 ADMIN 강등 방지 | ✅ **해결** — `countByRole` 체크 + LAST_ADMIN ErrorCode |
| 108 | 현재/신규 비밀번호 동일 허용 | ❌ **미수정** — DTO·서비스 모두 검증 없음 |
| **167** | AdminSecurityConfig AND 조건 취약 | ❌ **신규** — username만 기본값이면 password 약해도 통과 |
| **168** | swagger.public 기본값 true | ❌ **신규** — properties 미설정 → 항상 공개 |
| **169** | /actuator/prometheus 인증 없이 공개 | ❌ **신규** — 주석과 코드 불일치, permitAll() |

---

### 7-3. DTO 입력 검증 — 리뷰 결과

| # | 항목 | 결과 |
|---|------|------|
| ~~111~~ | RefundRequest.reason @Size | ✅ **해결** |
| ~~127~~ | InventoryReceiveRequest/AdjustRequest note @Size | ✅ **해결** |
| ~~154~~ | ReviewCreateRequest/UpdateRequest content @Size | ✅ **해결** |
| ~~158~~ | ProductCreateRequest/UpdateRequest description @Size | ✅ **해결** |
| ~~128~~ | InventorySpecification LIKE 이스케이프 | ✅ **해결** |
| 144 | CouponCreateRequest PERCENTAGE DTO 검증 | ⚠️ **부분** — 서비스 검증 있음, DTO 구멍 잔존 |
| 103 | Pageable size 상한 | ⚠️ **부분** — 커서 해결, Pageable 11개 미해결 |
| **170** | ProductCreateRequest/UpdateRequest name @Size 누락 | ❌ **신규** — 255자 초과 시 500 에러 |

---

### 7-4. 성능 최적화 — 리뷰 결과

| # | 항목 | 결과 |
|---|------|------|
| 69 | PaymentService.getByOrderId() 전체 Order 로드 | ❌ **미수정** — 주석과 구현 불일치 |
| ~~71~~ | OrderService.getHistory() 전체 Order 로드 | ✅ **해결** — findUserIdById() 프로젝션 |
| 76 | PaymentTransactionHelper.loadAndValidateForCancel() | ⏸️ **보류** — 상태 변경 겸용으로 전체 로드 불가피 |
| ~~74~~ | PointService.refundByOrder() SELECT FOR UPDATE | ✅ **해결** — early return |
| ~~75~~ | RefundService username→userId | ✅ **해결** — Long userId 전환 |
| ~~106~~ | CartRepository N+1 DELETE | ✅ **해결** — 벌크 DELETE |
| ~~70~~ | CartService.addOrUpdate() 장바구니 재조회 | ✅ **해결** — 단건 응답 |

---

### 7-5. 코드 품질 — 리뷰 결과

| # | 항목 | 결과 |
|---|------|------|
| 51 | OrderService God Object | ⚠️ **부분** — OrderDetailService 분리, 본체 600+ 줄 잔존 |
| 104 | resolveUserId() 복붙 | ⚠️ **부분** — SecurityUtils 도입, 4개 컨트롤러 미적용 |
| 147 | CouponService 로깅 부재 | ⚠️ **부분** — 비즈니스 메서드 완료, ADMIN 연산 미흡 |
| ~~148~~ | PointService 부분 회수 메트릭 | ✅ **해결** — Prometheus Counter |
| ~~157~~ | 탈퇴 사용자 리뷰 null | ✅ **해결** — `[탈퇴사용자]` 기본값 |
| ~~132~~ | OrderRepository 통계 쿼리 범위 | ✅ **해결** — 반열린 구간 통일 |
| ~~133~~ | Webhook JSON 파싱 500 | ✅ **해결** — catch → 200 |

---

### 7-6. 카탈로그 — 리뷰 결과

| # | 항목 | 결과 |
|---|------|------|
| 149 | ES 검색 응답 재고·리뷰 null | ❌ **미수정** — toProductResponse()에 필드 없음 |
| ~~150~~ | CategoryService.getById() @Cacheable | ✅ **해결** |

---

## 리뷰 진행 요약

| Phase | 범위 | 상태 |
|-------|------|------|
| ~~**1**~~ | 핵심 트랜잭션 | ✅ 완료 → TODO #113~#133 |
| ~~**2**~~ | 보안·인증 | ✅ 완료 → TODO #134~#143 |
| ~~**3**~~ | 부가 도메인 | ✅ 완료 → TODO #144~#148 |
| ~~**4**~~ | 상품·카탈로그 | ✅ 완료 → TODO #149~#158 |
| ~~**5**~~ | 공통 인프라 | ✅ 완료 → TODO #159~#161 |
| ~~**6**~~ | DB·코드 품질 | ✅ 완료 → TODO #162~#163 |
| ~~**7**~~ | 미해결 정리 + 신규 | ✅ 완료 → TODO #164~#170, 22개 해결 확인 |

---

## 전체 리뷰 통계

| 구분 | 수 |
|------|---|
| Phase 1~6 총 발견 | 51개 (#113~#163) |
| Phase 7 신규 발견 | 7개 (#164~#170) |
| Phase 7에서 해결 확인 | 22개 |
| **현재 미해결 잔여** | **15개** |
| 보류 | 7개 |

### 미해결 항목 요약 (우선순위순)

| 등급 | 항목 | 핵심 |
|------|------|------|
| 🔴 P0 | #164 | RefundService.getByPaymentId() 다건 500 |
| 🔴 P0 | #165 | cancelPendingOrdersByUser() 전체 롤백 |
| 🔴 P0 | #166 | PointService.refundByOrder() UK 충돌 |
| 🟠 P1 | #108 | 비밀번호 동일 변경 허용 |
| 🟠 P1 | #167 | AdminSecurityConfig AND 조건 취약 |
| 🟠 P1 | #168 | swagger.public 기본값 true |
| 🟠 P1 | #169 | /actuator/prometheus 공개 |
| 🟠 P1 | #170 | ProductRequest name @Size 누락 |
| 🟡 P2 | #69 | PaymentService 전체 Order 로드 |
| 🟡 P2 | #103 | Pageable max-page-size 미설정 |
| 🟡 P2 | #104 | resolveUserId 4개 컨트롤러 미적용 |
| 🟡 P2 | #144 | CouponCreateRequest PERCENTAGE DTO |
| 🟡 P2 | #147 | CouponService ADMIN 로깅 |
| 🟡 P2 | #149 | ES 검색 응답 null |
| 🟡 P3 | #51 | OrderService God Object |
