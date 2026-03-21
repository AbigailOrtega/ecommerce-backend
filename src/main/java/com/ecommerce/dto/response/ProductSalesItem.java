package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record ProductSalesItem(
        Long productId,
        String productName,
        long unitsSold,
        BigDecimal revenue,
        int currentStock
) {}
