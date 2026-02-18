package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    Integer stockQuantity,
    String imageUrl,
    List<String> images,
    String slug,
    boolean featured,
    boolean active,
    List<CategoryResponse> categories,
    LocalDateTime createdAt
) {}
