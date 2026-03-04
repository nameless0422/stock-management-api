package com.stockmanagement.common.exception;

/**
 * 재고 부족 예외.
 *
 * <p>주문 수량이 가용 재고(available = onHand - reserved - allocated)를 초과할 때 발생한다.
 * {@link GlobalExceptionHandler}가 409 Conflict 응답으로 변환한다.
 */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException() {
        super(ErrorCode.INSUFFICIENT_STOCK);
    }

    /** 요청 수량과 가용 재고를 포함한 구체적인 메시지를 생성한다. */
    public InsufficientStockException(int requested, int available) {
        super(ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. (요청: %d, 가용: %d)", requested, available));
    }
}
