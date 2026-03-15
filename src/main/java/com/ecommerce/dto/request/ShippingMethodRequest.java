package com.ecommerce.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ShippingMethodRequest(
    @NotBlank String name,
    String description,
    @NotNull @DecimalMin("0.00") BigDecimal price,
    Integer estimatedDays,
    int displayOrder
) {}
