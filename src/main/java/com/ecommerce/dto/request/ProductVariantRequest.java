package com.ecommerce.dto.request;

public record ProductVariantRequest(
    String color,
    String size,
    Integer stock
) {}
