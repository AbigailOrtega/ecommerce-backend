package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PromotionResponse(
    Long id,
    String name,
    BigDecimal discountPercent,
    LocalDate startDate,
    LocalDate endDate,
    boolean active,
    List<PromotionProductSummary> products
) {
    public record PromotionProductSummary(Long id, String name, BigDecimal price) {}
}
