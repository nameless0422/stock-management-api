# 코드 리뷰 계획

> 도메인별로 리뷰 범위·체크리스트·미해결 TODO 항목을 정리한다.
> 완료된 개선 사항은 `docs/IMPROVEMENTS.md` 참조.

---

## 리뷰 우선순위 기준

| 등급 | 기준 | 예시 |
|------|------|------|
| P0 | 금전 손실·보안 취약점 | 결제 이중 처리, IDOR, 인증 우회 |
| P1 | 데이터 정합성·운영 장애 | 트랜잭션 경합, 재고 불일치, 스케줄러 오류 |
| P2 | 성능·코드 품질 | N+1, 불필요한 DB 조회, God Object |
| P3 | 유지보수·확장성 | API 버전, 코드 중복, DTO 검증 누락 |

---

## ~~Phase 1 — 핵심 트랜잭션 (P0~P1)~~ ✅ 리뷰 완료

> Payment·Order·Inventory 3개 도메인 리뷰 완료.
> 발견된 항목은 `docs/TODO.md` #113~#133에 반영됨.

---

## ~~Phase 2 — 보안·인증 (P0~P1)~~ ✅ 리뷰 완료

> Security/JWT/Auth + User 도메인 리뷰 완료.
> 발견된 항목은 `docs/TODO.md` #134~#143에 반영됨.
> 기존 항목 #83, #47, #96, #105, #108, #42는 리뷰에서 재확인됨.

---

## ~~Phase 3 — 부가 도메인 (P1~P2)~~ ✅ 리뷰 완료

> Coupon·Refund·Shipment·Point 4개 도메인 리뷰 완료.
> 발견된 항목은 `docs/TODO.md` #144~#148에 반영됨.
> 기존 항목 #45, #74, #75, #79, #111은 리뷰에서 재확인됨.

### 3-1. Coupon — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| 동시성 (비관적 락) | ✅ `findByCodeWithLock` + 분산 락 이중 보호. usageCount 정합성 확보 |
| 주문 취소 쿠폰 반환 | ✅ `releaseCoupon()` 호출 경로 정상. 비관적 락으로 decreaseUsage 직렬화 |
| 만료 스케줄러 | ✅ `deactivateExpiredCoupons()` 벌크 UPDATE. 새벽 1시 실행으로 영향도 낮음 |
| PERCENTAGE 상한 | ⚠️ 서비스에 >100 체크 있으나 DTO 검증과 불일치 → **#144** |

**신규 TODO**: #144 (PERCENTAGE 상한 DTO 검증), #147 (로깅 부재)

### 3-2. Refund — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| 소유권 검증 | ✅ ADMIN bypass + 일반 사용자 order.userId 검증 정상 |
| FAILED 재시도 | ✅ `reset(reason)` 기존 레코드 재사용 정상 동작 |
| noRollbackFor | ✅ `Exception.class`로 FAILED Dirty Checking 보장 |

**기존 미해결**: #45 (UNIQUE 제약), #75 (userId 직접 전달), #111 (@Size 누락)
**신규 발견**: 없음 (코드 품질 양호)

### 3-3. Shipment — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| REQUIRES_NEW 독립 커밋 | ✅ 설계 정확 |
| 상태 전이 유효성 | ✅ 잘못된 상태에서 INVALID_SHIPMENT_STATUS 예외 |
| DTO 입력 검증 | ✅ @Size, @Pattern 적용. XSS 방어(htmlEscape) 확인 |
| 소유권 검증 | ✅ `findUserIdById()` 스칼라 프로젝션 사용 |

**기존 미해결**: #79 (Outbox false dead letter)
**신규 발견**: 없음 (핵심 설계 양호)

### 3-4. Point — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| earn/use 전파 레벨 | ✅ earn: REQUIRES_NEW, use: REQUIRED 설계 적절 |
| 이중 적립 방어 | ⚠️ `existsByOrderIdAndType()` 앱 레벨만 — DB UNIQUE 없음 → **#146** |
| 환불 포인트 회수 | ⚠️ 부분 회수 시 모니터링 불가 → **#148** |
| 엔티티 검증 | ⚠️ 뮤테이션 메서드 amount <= 0 가드 없음 → **#145** |

**기존 미해결**: #74 (불필요한 SELECT FOR UPDATE)
**신규 TODO**: #145 (엔티티 검증), #146 (UNIQUE 제약), #148 (부분 회수 모니터링)

---

## ~~Phase 4 — 상품·카탈로그 (P2~P3)~~ ✅ 리뷰 완료

> Product·Category·ProductImage·Review·Wishlist 리뷰 완료.
> 발견된 항목은 `docs/TODO.md` #149~#158에 반영됨.
> 기존 항목 #53, #104는 리뷰에서 재확인됨.

### 4-1. Product 핵심 — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| ES 검색 정확성 | ✅ multi_match, 가격 범위, 카테고리 필터 정상 |
| MySQL fallback | ⚠️ ES/MySQL 응답 불일치 (#53 재확인) + sort만으로 ES 진입 → **#155** |
| ES 동기화 타이밍 | ✅ `@TransactionalEventListener(AFTER_COMMIT)` 올바름 |
| ES 검색 결과 응답 | ⚠️ 재고·리뷰 통계 null → MySQL 조회와 불일치 → **#149** |
| DTO 검증 | ⚠️ description @Size 누락 → **#158** |

### 4-2. Category — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| 카테고리 캐시 | ⚠️ `getById()` @Cacheable 누락 → **#150** |
| 트리 조회 | ✅ in-memory 조립 O(n), parent LEFT JOIN FETCH N+1 방지 |
| 삭제 제약 | ✅ CATEGORY_HAS_CHILDREN / PRODUCTS 검증 정상 |
| 순환 참조 | ✅ 2단계 고정 구조이므로 직접 순환 방지만으로 충분 |

### 4-3. ProductImage — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| MIME/확장자 검증 | ⚠️ contentType 형식 미검증 + 확장자-MIME 불일치 허용 → **#151** |
| 이미지 개수 제한 | ⚠️ 상품당 무제한 → **#152** |
| objectKey 보안 | ⚠️ 클라이언트 조작 가능 → **#153** |
| S3 삭제 정합성 | ⚠️ 삭제 실패 시 DB 불일치 → **#156** |

### 4-4. Review + Wishlist — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| 리뷰 구매 검증 | ✅ `orderRepository.existsConfirmedOrder()` 정상 |
| 리뷰 중복 방지 | ✅ `existsByProductIdAndUserId()` 정상 |
| content 검증 | ⚠️ @Size 누락 → **#154** |
| 탈퇴 사용자 | ⚠️ username null 노출 → **#157** |
| 위시리스트 N+1 | ✅ `findAllById()` + `findAllByProductIdIn()` 배치 조회 |

**신규 TODO**: #149~#158 (10개)
**기존 미해결**: #53 (ES fallback 불일치)

---

## ~~Phase 5 — 공통 인프라 (P1~P3)~~ ✅ 리뷰 완료

> Outbox·예외처리·캐시/Redis·Rate Limit 4개 영역 리뷰 완료.
> 발견된 항목은 `docs/TODO.md` #159~#161에 반영됨.
> 기존 항목 #47, #83, #95, #101, #136, #137은 리뷰에서 재확인됨. #138은 완료 처리됨.

### 5-1. Outbox 이벤트 — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| relay 분산 락 | ✅ `@DistributedLock(skipOnFailure=true)` 중복 relay 방지 정상 |
| dead letter 알람 | ✅ Prometheus Gauge `outbox.dead_letters` 정상 노출 |
| 지수 백오프 | ✅ `min(30 × 2^retryCount, 3600)` 연산 정확 |
| Propagation.MANDATORY | ✅ 비즈니스 TX 바깥 호출 시 즉시 예외 |
| processOne 멱등성 | ✅ `publishedAt != null` 체크로 이중 발행 방지 |
| relayedCounter 정확성 | ⚠️ 실패 이벤트도 카운팅 → **#161** |

**기존 미해결**: #101 (purge 대량 삭제 배치 전환)

### 5-2. 예외 처리 / API 응답 — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| BusinessException | ✅ ErrorCode 기반 HTTP 상태 매핑 정확 |
| MethodArgumentNotValid | ✅ 필드별 FieldErrorDetail 반환 |
| IllegalArgumentException | ✅ 400 Bad Request |
| DataIntegrityViolation | ✅ 409 Conflict, DB 구조 미노출 |
| OptimisticLocking | ✅ 409 Conflict, 재시도 안내 |
| NoResourceFound | ✅ 404 |
| HttpMessageNotReadable | ✅ `@ExceptionHandler` 추가, 400 응답 (#95 해결) |
| ErrorCode 체계 | ✅ 도메인별 그룹화, HTTP 상태 매핑 적절 |

### 5-3. 캐시 / Redis — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| 직렬화 화이트리스트 | ✅ `allowIfBaseType` → `allowIfSubType` 전환 완료 (#57 해결) |
| TTL 정책 | ✅ products 10분, inventory/orders 5분, categories 30분 적절 |
| null 캐싱 | ✅ `disableCachingNullValues()` 적용 |
| Redisson 연결 설정 | ✅ timeout(1000)/connectTimeout(1000)/retryAttempts(1)/poolSize(32) 완료 (#58 해결) |
| RefreshTokenStore | ✅ revokeAll Lua 원자화 (#137 해결), consume() 탈취 감지 (#136 해결) |

### 5-4. Rate Limit — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| Lua 원자성 | ✅ INCR + EXPIRE 원자적 처리 정상 |
| trust-proxy 설정 | ✅ false 시 헤더 무시, true 시 X-Real-IP 우선 |
| LoginRateLimiter | ✅ username+IP 복합 키로 Account Lockout DoS 방지 (#83 해결) |
| X-Forwarded-For IP | ✅ `trusted-proxy-count=1` 적용, 클라이언트 IP 올바르게 추출 (#47 해결) |
| /api/auth/refresh | ✅ Rate Limit 적용 완료 (#138 완료) |

---

## ~~Phase 6 — DB·인프라·코드 품질 (P2~P3)~~ ✅ 리뷰 완료

> Flyway·Admin·크로스커팅 3개 영역 리뷰 완료.
> 발견된 항목은 `docs/TODO.md` #162~#163에 반영됨.
> 기존 항목 #41, #59, #62, #107, #43, #104, #25, #13, #103, #128은 리뷰에서 재확인됨.

### 6-1. Flyway 마이그레이션 — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| DECIMAL precision | ✅ V40에서 `DECIMAL(15,2)` 통일 완료 (#41 해결) |
| SSL/자격증명 | ✅ useSSL=false 주석 추가 (#59), DB 자격증명 환경변수 전환 (#62 해결) |
| ENGINE/CHARSET 일관성 | ✅ V4/V9~V13/V15/V17/V22에 ENGINE/CHARSET 명시 완료 (#63 해결) |
| 인덱스 전략 | ✅ V23/V25/V27/V31 복합 인덱스 적절. orders.created_at, point_transactions 커버링 인덱스 추가됨 |
| FK 제약 | ✅ V32에서 누락 FK 일괄 추가 완료 |
| 타임스탬프 기본값 | ✅ V33에서 일괄 수정 완료 |

### 6-2. Admin 도메인 — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| 마지막 ADMIN 보호 | ❌ `updateRole()`에 ADMIN 수 체크 없음 (#107 재확인) |
| 대시보드 성능 | ✅ `findOrderStats()` GROUP BY 단일 쿼리 + `userRepository.count()` — 현재 규모 적정 |
| 주문 목록 사용자명 | ✅ `findAllById()` 배치 IN 쿼리 — 심각도 낮음 |
| 검색 LIKE | ✅ `ProductService`/`AdminService.escapeLike()` 유틸리티 추출 완료 (#60 해결) |
| RoleUpdateRequest | ✅ `@NotNull UserRole role` 검증 정상 |

### 6-3. 크로스커팅 코드 품질 — 리뷰 결과

| 체크항목 | 결과 |
|---------|------|
| resolveUserId | ⚠️ 10개+ 컨트롤러로 확산 (#104 재확인, 기존 6개→10개+) |
| Pageable 기본값 | ✅ 전 컨트롤러 `size=20, sort=createdAt, DESC` 일관적 |
| API 버전 관리 | ⚠️ `/api/v1/` 미도입 (#25 재확인) |
| 페이지네이션 | ⚠️ 오프셋 기반 (#13), size 상한 미검증 (#103) 재확인 |

**해결**: #60 (LIKE escape ProductService/AdminService), #61 (Flyway ENGINE/CHARSET), #62 (DB 자격증명), #41 (V40 DECIMAL 통일)
**기존 미해결**: #107, #43, #104, #25, #13, #103, #128

---

## 리뷰 진행 요약

| Phase | 범위 | 도메인 | 상태 |
|-------|------|--------|------|
| ~~**1**~~ | ~~핵심 트랜잭션~~ | ~~Payment, Order, Inventory~~ | ✅ 완료 → TODO #113~#133 |
| ~~**2**~~ | ~~보안·인증~~ | ~~Security, User~~ | ✅ 완료 → TODO #134~#143 |
| ~~**3**~~ | ~~부가 도메인~~ | ~~Coupon, Refund, Shipment, Point~~ | ✅ 완료 → TODO #144~#148 |
| ~~**4**~~ | ~~상품·카탈로그~~ | ~~Product (+ category/image/review/wishlist)~~ | ✅ 완료 → TODO #149~#158 |
| ~~**5**~~ | ~~공통 인프라~~ | ~~Outbox, Exception, Cache, RateLimit~~ | ✅ 완료 → TODO #159~#161 |
| ~~**6**~~ | ~~DB·코드 품질~~ | ~~Flyway, Admin, 크로스커팅~~ | ✅ 완료 → TODO #162~#163 |

---

## 전체 리뷰 통계

| 구분 | 수 |
|------|---|
| Phase 1~6 총 신규 발견 | **51개** (#113~#163) |
| P0 (금전 손실·보안) | 5개 (#113, #115, #116, #117, #114) |
| P1 (데이터 정합성·운영) | 22개 |
| P2 (성능·코드 품질) | 16개 |
| P3 (유지보수·확장성) | 8개 |
| 보류 (판단 필요) | 6개 |
| 조치 불필요 | 4개 |
