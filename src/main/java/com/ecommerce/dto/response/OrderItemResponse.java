package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    Long productId,
    String productName,
    BigDecimal productPrice,
    Integer quantity,
    BigDecimal subtotal,
    String selectedColorName,
    String selectedSizeName
) {}
