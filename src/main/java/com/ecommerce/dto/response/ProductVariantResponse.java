package com.ecommerce.dto.response;

public record ProductVariantResponse(
    Long id,
    String color,
    String size,
    Integer stock
) {}
