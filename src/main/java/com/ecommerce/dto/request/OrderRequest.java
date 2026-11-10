package com.ecommerce.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OrderRequest(
    @Size(max = 45, message = "La calle y número admiten hasta 45 caracteres") String shippingAddress,
    String shippingCity,
    String shippingState,
    String shippingZipCode,
    @Size(max = 150) String shippingNeighborhood,
    String shippingCountry,
    @NotBlank String paymentMethod,
    String paymentId,
    @Size(max = 255, message = "Las indicaciones admiten hasta 255 caracteres") String notes,
    String couponCode,
    @NotBlank String shippingType,   // "NATIONAL" or "PICKUP"
    Long pickupLocationId,           // required when PICKUP
    String skydropxRateId,           // optional – Skydropx rate selected by customer
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate pickupDate,  // new calendar-based date
    Long pickupAvailabilityId        // specific availability rule chosen by buyer
) {}
