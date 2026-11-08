package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PayPalCaptureOrderRequest(@NotBlank String orderId) {}
