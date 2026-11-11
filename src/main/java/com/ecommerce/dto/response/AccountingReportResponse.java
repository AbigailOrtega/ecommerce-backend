package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The store's accounts for a date range: sales by the day the money came in, refunds by the day
 * they happened, and what selling cost (commissions, labels, merchandise).
 */
public record AccountingReportResponse(
        LocalDate from,
        LocalDate to,
        Summary summary,
        List<PaymentMethodTotal> byPaymentMethod,
        List<SalesDataPoint> daily,
        List<LedgerRow> ledger
) {
    public record Summary(
            long paidOrders,
            BigDecimal productSales,     // garments, before discounts
            BigDecimal discounts,        // coupons (promotion prices are already in productSales)
            BigDecimal shippingCharged,
            BigDecimal totalCollected,   // productSales - discounts + shippingCharged
            long refundedOrders,
            BigDecimal refunds,
            BigDecimal netSales,         // totalCollected - refunds
            BigDecimal paymentFees,
            long ordersMissingFee,       // card/PayPal payments whose commission is not known yet
            BigDecimal labelCosts,
            long shipmentsMissingCost,   // national orders with no label cost recorded
            BigDecimal costOfGoods,      // garments sold minus garments returned by refunds
            long unitsMissingCost,       // units sold whose product has no cost
            BigDecimal grossProfit,      // netSales - paymentFees - labelCosts - costOfGoods
            long paidThenCancelled       // paid, later cancelled without a refund recorded
    ) {}

    public record PaymentMethodTotal(String method, long orders, BigDecimal collected, BigDecimal fees) {}

    /** One movement: a sale (positive) or a refund (negative amounts). */
    public record LedgerRow(
            String movement,             // "VENTA" or "REEMBOLSO"
            LocalDateTime date,
            String orderNumber,
            String customer,
            String email,
            String status,
            String paymentMethod,
            String paymentId,
            String couponCode,
            String shippingType,
            BigDecimal productSales,
            BigDecimal discount,
            BigDecimal shipping,
            BigDecimal total,
            BigDecimal paymentFee,
            BigDecimal labelCost,
            BigDecimal costOfGoods
    ) {}
}
