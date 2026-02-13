package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
    Long id,
    ProductResponse product,
    Integer quantity,
    BigDecimal subtotal
) {}
