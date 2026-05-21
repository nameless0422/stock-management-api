-- 포인트 트랜잭션 확정 상태 컬럼 추가
-- 기존 데이터는 모두 CONFIRMED (이미 잔액에 반영된 트랜잭션)
ALTER TABLE point_transactions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';

-- 적립 예정 내역 조회용 인덱스
CREATE INDEX idx_point_txn_user_status ON point_transactions (user_id, status);
