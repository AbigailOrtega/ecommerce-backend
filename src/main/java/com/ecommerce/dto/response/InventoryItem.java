package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record InventoryItem(
        Long productId,
        String name,
        String sku,
        int stock,
        BigDecimal price,
        boolean active,
        List<String> outOfStockVariants,
        String slug   // for the link to the product's store page
) {
    public InventoryItem(Long productId, String name, String sku, int stock, BigDecimal price, boolean active,
                         List<String> outOfStockVariants) {
        this(productId, name, sku, stock, price, active, outOfStockVariants, null);
    }
}
