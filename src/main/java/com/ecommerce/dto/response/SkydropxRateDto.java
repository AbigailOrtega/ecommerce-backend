package com.ecommerce.dto.response;

public record SkydropxRateDto(
    String id,
    String carrier,
    String service,
    double price,
    Integer estimatedDays
) {}
