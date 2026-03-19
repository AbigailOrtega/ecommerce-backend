package com.ecommerce.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GuestOrderRequest(
    @NotBlank String guestFirstName,
    @NotBlank String guestLastName,
    @Email @NotBlank String guestEmail,
    String guestPhone,
    @NotEmpty @Valid List<GuestOrderItemRequest> items,
    String shippingAddress,
    String shippingCity,
    String shippingState,
    String shippingZipCode,
    String shippingCountry,
    @NotBlank String paymentMethod,
    String paymentId,
    String notes,
    String couponCode,
    @NotBlank String shippingType,
    Long pickupLocationId,
    Long pickupTimeSlotId,
    String skydropxRateId
) {}
