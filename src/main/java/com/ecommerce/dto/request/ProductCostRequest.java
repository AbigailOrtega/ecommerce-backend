package com.ecommerce.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/** What the product cost the store; null clears it. */
public record ProductCostRequest(
        @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El costo admite hasta 2 decimales")
        BigDecimal costPrice
) {}
