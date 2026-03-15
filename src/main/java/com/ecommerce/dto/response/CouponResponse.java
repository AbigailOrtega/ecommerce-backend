package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CouponResponse(
    Long id,
    String code,
    BigDecimal discountPercent,
    LocalDate expiresAt,
    boolean active,
    int usageCount,
    Integer usageLimit,
    LocalDateTime createdAt
) {}
