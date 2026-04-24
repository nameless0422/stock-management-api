package com.stockmanagement.domain.payment.entity;

/**
 * Payment lifecycle states.
 *
 * <pre>
 *   PENDING ──→ DONE ──→ PARTIAL_CANCELLED ──→ CANCELLED
 *           │        └──→ CANCELLED
 *           └──→ FAILED
 * </pre>
 *
 * <ul>
 *   <li>PENDING           – payment record created; waiting for customer to complete checkout
 *   <li>DONE              – payment approved by TossPayments
 *   <li>CANCELLED         – payment cancelled / fully refunded after approval
 *   <li>FAILED            – payment approval rejected by TossPayments
 *   <li>PARTIAL_CANCELLED – partial refund applied; accumulated cancelledAmount &lt; amount
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    DONE,
    CANCELLED,
    FAILED,
    PARTIAL_CANCELLED
}
