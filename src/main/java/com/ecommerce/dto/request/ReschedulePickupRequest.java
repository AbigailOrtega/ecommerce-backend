package com.ecommerce.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReschedulePickupRequest(
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate pickupDate,
    Long pickupAvailabilityId
) {}
