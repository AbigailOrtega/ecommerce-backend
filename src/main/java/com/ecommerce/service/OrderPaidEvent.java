package com.ecommerce.service;

/**
 * An order's payment went through and it moved from PENDING to CONFIRMED. Published by
 * PaymentService; OrderService emails the shopper once the transaction has committed.
 */
public record OrderPaidEvent(Long orderId) {}
