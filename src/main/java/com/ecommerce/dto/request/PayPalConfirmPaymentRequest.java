package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PayPalConfirmPaymentRequest(@NotBlank String orderNumber, @NotBlank String captureId) {}
