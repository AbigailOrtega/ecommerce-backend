package com.ecommerce.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponRequest(
    @NotBlank String code,
    @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPercent,
    @NotNull @Future LocalDate expiresAt,
    @Positive Integer usageLimit  // null = unlimited
) {}
