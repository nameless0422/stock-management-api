-- =====================================================================
-- V32: 누락 FK 제약 추가
-- =====================================================================
-- cart_items.user_id:
--   product_id → products FK는 있지만 user_id → users FK가 없음.
--   사용자 탈퇴(소프트 삭제) 후 cart_items 레코드가 고아로 남아
--   user_id가 무효한 상태로 유지됨.
--   ON DELETE CASCADE: 사용자 hard delete 시 장바구니 자동 정리.
--   (소프트 삭제 운영이므로 실질적 CASCADE 발동은 없지만 무결성 보장)
--
-- point_transactions.order_id:
--   user_id → users FK는 있지만 order_id → orders FK가 없음.
--   주문 삭제 시 포인트 이력의 order_id가 고아 참조 상태가 됨.
--   ON DELETE SET NULL: 주문 삭제 후에도 포인트 이력 자체는 보존.
-- =====================================================================

-- #38: 장바구니 사용자 참조 무결성
ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- #39: 포인트 이력 주문 참조 무결성
ALTER TABLE point_transactions
    ADD CONSTRAINT fk_pt_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL;
