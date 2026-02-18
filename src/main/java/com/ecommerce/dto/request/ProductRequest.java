package com.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
    @NotBlank String name,
    String description,
    @NotNull @Positive BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    @Min(0) Integer stockQuantity,
    String imageUrl,
    List<String> images,
    List<Long> categoryIds,
    Boolean featured,
    Boolean active
) {}
