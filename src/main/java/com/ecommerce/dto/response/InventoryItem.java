package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record InventoryItem(
        Long productId,
        String name,
        String sku,
        int stock,
        BigDecimal price,
        boolean active
) {}
