package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PickupLocationRequest(
    @NotBlank String name,
    @NotBlank String address,
    @NotBlank String city,
    @NotBlank String state
) {}
