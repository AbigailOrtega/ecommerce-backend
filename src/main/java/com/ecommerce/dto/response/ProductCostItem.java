package com.ecommerce.dto.response;

import java.math.BigDecimal;

/** A product's price and what it cost the store (admin only). */
public record ProductCostItem(Long id, String name, String sku, BigDecimal price, BigDecimal costPrice, boolean active) {}
