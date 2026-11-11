package com.ecommerce.dto.response;

import java.math.BigDecimal;

/**
 * What a category sold in a period: units, orders that had any of its products, and the money from
 * those items. categoryId is null for products without a category. A product in two categories
 * counts in both, so the rows can add up to more than the period's total.
 */
public record CategorySalesItem(
        Long categoryId,
        String name,
        boolean visible,
        long unitsSold,
        long orders,
        BigDecimal revenue
) {}
