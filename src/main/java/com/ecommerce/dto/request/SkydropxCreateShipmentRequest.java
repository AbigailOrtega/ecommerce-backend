package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SkydropxCreateShipmentRequest(
    @NotBlank String rateId
) {}
