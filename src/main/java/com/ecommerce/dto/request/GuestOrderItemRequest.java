package com.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GuestOrderItemRequest(
    @NotNull Long productId,
    @Min(1) Integer quantity,
    Long sizeId,
    String colorName
) {}
