package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record ShippingMethodResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    Integer estimatedDays,
    boolean active,
    int displayOrder
) {}
