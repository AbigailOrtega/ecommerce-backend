package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PayPalCaptureForOrderRequest(@NotBlank String orderNumber) {}
