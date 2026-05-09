# TODO — 기술적 개선 항목

> 모든 항목은 GitHub Issues로 관리. 완료된 항목은 `docs/IMPROVEMENTS.md` 참조.

---

## 아키텍처 요약

```
Client
  │
  ├─ AuthController (JWT 발급)
  ├─ ProductController / CategoryController / ProductImageController
  ├─ InventoryController
  ├─ OrderController → OrderService → InventoryService · CouponService · PointService
  ├─ PaymentController → PaymentService → PaymentTransactionHelper → OrderService
  │                                                                 └─ OutboxEventStore
  ├─ ShipmentController → ShipmentService
  ├─ RefundController → RefundService → PaymentService
  ├─ CartController → CartService → OrderService
  ├─ WishlistController / ReviewController / PointController
  └─ AdminController → AdminService

공통 인프라:
  Redis — JWT 블랙리스트 · Refresh Token · Cache · Rate Limit · 분산 락 · Outbox relay 락
  Elasticsearch — 상품 전문 검색 (MySQL fallback)
  MinIO(S3) — 상품 이미지 Presigned URL
  OutboxEventStore — ORDER_CREATED/CANCELLED · PAYMENT_CONFIRMED · SHIPMENT_CREATE · POINT_EARN
  Flyway V1~V42 — 스키마 버전 관리
```

**주요 데이터 흐름**: 주문 생성 → 재고 예약(분산 락) → PENDING → Toss 결제 승인 → Outbox 이벤트(배송·포인트) → CONFIRMED → 배송 상태 전이 → 완료

**동시성 전략**: 재고 변경 `@DistributedLock(Redisson)` + `SELECT FOR UPDATE`(이중), 결제 확정 `PaymentIdempotencyManager(Redis SETNX)`, 쿠폰 발급 `findByCodeWithLock(비관적 락)`

---

## Open Issues

### 🟡 중장기 개선

| Issue | 항목 |
|-------|------|
| [#14](https://github.com/nameless0422/stock-management-api/issues/14) | OrderService God Object 분리 |
| [#15](https://github.com/nameless0422/stock-management-api/issues/15) | 오프셋 페이지네이션 → 커서 기반 전환 |
| [#22](https://github.com/nameless0422/stock-management-api/issues/22) | API 버전 관리 부재 — /api/v1/ prefix 도입 |

### ⚪ 보류

| Issue | 항목 | 보류 이유 |
|-------|------|-----------|
| [#62](https://github.com/nameless0422/stock-management-api/issues/62) | 대시보드 daily_order_stats 미활용 | 현 규모 성능 문제 없음 |
| [#63](https://github.com/nameless0422/stock-management-api/issues/63) | ES Fallback MySQL LIKE 불일치 | 제품 결정 사항 |
| [#64](https://github.com/nameless0422/stock-management-api/issues/64) | 리드 레플리카 라우팅 | 인프라 의존 |
| [#65](https://github.com/nameless0422/stock-management-api/issues/65) | OrderExpiryScheduler 배치 전환 | 초기 운영 현행 유지 |
| [#66](https://github.com/nameless0422/stock-management-api/issues/66) | AdminService.getOrders() 사용자명 | 심각도 낮음 |
