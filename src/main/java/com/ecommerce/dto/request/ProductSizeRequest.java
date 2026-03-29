package com.ecommerce.dto.request;

public record ProductSizeRequest(
    Long id,
    String name,
    Integer stock
) {}
