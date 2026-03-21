package com.ecommerce.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

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
    String skydropxRateId,           // optional – Skydropx rate selected by customer
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate pickupDate,  // new calendar-based date
    Long pickupAvailabilityId        // specific availability rule chosen by buyer
) {}
