package com.ecommerce.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PromotionRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPercent,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull @Size(min = 1) List<Long> productIds
) {}
