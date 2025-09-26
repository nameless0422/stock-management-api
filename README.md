# stock-management-api
재고관리 시스템 구현 

FastAPI + MySQL 
쇼핑몰 환경을 가정하여 **재고 → 주문 → 결제 → 유저 관리** 순으로 확장하며, 추후 Redis, Docker, AI 기반 데이터 정리 기능까지 포함할 예정입니다.

---

## 🎯 프로젝트 목표
- 안정적인 **재고 관리 API** 구현 (재고 예약, 출고, 롤백)
- 쇼핑몰 워크플로우(주문/결제/회원) 반영
- **동시성 제어** 및 **재고 초과 판매 방지**
- Docker, Redis 등 운영 환경 반영
- AI 기반 데이터 정리 및 상품 메타데이터 자동화 (가능하다면)

---

## 🗂 주요 도메인
1. **재고(Inventory)**
   - 상품 SKU, 창고 단위 관리
   - `on_hand`, `reserved`, `available` 수량 관리
   - 예약/출고/취소 트랜잭션 보장

2. **주문(Order)**
   - 주문 생성 시 재고 예약
   - 결제 대기/성공/실패 상태 전이
   - 중복 주문 방지 (idempotency key)

3. **결제(Payment)**
   - 결제 요청 → 승인/실패 → 웹훅 처리
   - 재고 확정(할당) 또는 예약 해제

4. **유저(User)**
   - 회원가입/로그인 (JWT 인증)
   - 주문/결제 이력 조회

---

## ⚙️ 기술 스택
- **Backend**: FastAPI
- **Database**: MySQL (InnoDB)
- **Cache/Queue**: Redis (재고 예약 TTL, 락)
- **Migration**: Alembic
- **Auth**: JWT
- **Container**: Docker, docker-compose

---

## 📌 개발 단계별 로드맵

### 1단계: 기본 뼈대
- FastAPI 프로젝트 초기화
- MySQL 연동, Alembic 마이그레이션 설정
- `products`, `inventory` 기본 CRUD 구현

### 2단계: 주문 & 재고 예약
- `orders`, `order_items` 테이블 설계
- 주문 생성 시 재고 예약 (`reserved` 증가)
- 재고 부족 시 예외 처리
- 동시성 제어 (FOR UPDATE / version 필드)

### 3단계: 결제 연동
- `payments` 테이블 설계
- 결제 요청 API / 결제 결과 웹훅 처리
- 성공 시 → 재고 확정 / 실패 시 → 예약 해제

### 4단계: 사용자 관리
- 회원가입/로그인 (JWT 기반)
- 주문 내역, 결제 내역 조회 API

### 5단계: 운영 환경 확장
- Docker/Docker-compose (MySQL, Redis, API)
- Redis로 재고 예약 TTL 관리
- 예약 만료 자동 해제 (백그라운드 작업/큐)

### 6단계: AI 데이터 정리
- 상품명/옵션 정규화 (브랜드, 색상, 사이즈 추출)
- 카테고리 자동 분류 (ML/NLP)
- 중복 상품 탐지 (임베딩 + 유사도 검색)
- 상품 데이터 품질 스코어링

---

## 🚀 최종 목표
- 확장 가능한 **쇼핑몰 백엔드 기반 구조**
- 안정적인 **재고 및 주문/결제 관리**
- AI 기반 **데이터 정제 자동화**

