package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrderRequest(
    String shippingAddress,
    String shippingCity,
    String shippingState,
    String shippingZipCode,
    String shippingCountry,
    @NotBlank String paymentMethod,
    String paymentId,
    String notes,
    String couponCode,
    @NotBlank String shippingType,   // "NATIONAL" or "PICKUP"
    Long pickupLocationId,           // required when PICKUP
    Long pickupTimeSlotId,           // required when PICKUP
    String skydropxRateId            // optional – Skydropx rate selected by customer
) {}
