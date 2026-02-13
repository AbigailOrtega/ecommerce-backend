package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrderRequest(
    @NotBlank String shippingAddress,
    @NotBlank String shippingCity,
    @NotBlank String shippingState,
    @NotBlank String shippingZipCode,
    @NotBlank String shippingCountry,
    @NotBlank String paymentMethod,
    String paymentId,
    String notes
) {}
