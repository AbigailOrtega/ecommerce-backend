package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record ProductSalesItem(
        Long productId,
        String productName,
        long unitsSold,
        BigDecimal revenue,
        int currentStock,
        String slug   // for the link to the product's store page
) {
    public ProductSalesItem(Long productId, String productName, long unitsSold, BigDecimal revenue, int currentStock) {
        this(productId, productName, unitsSold, revenue, currentStock, null);
    }
}
