package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PickupTimeSlotRequest(
    @NotBlank String label
) {}
