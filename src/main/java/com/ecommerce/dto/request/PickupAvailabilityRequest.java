package com.ecommerce.dto.request;

import com.ecommerce.entity.AvailabilityType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record PickupAvailabilityRequest(
    @NotNull AvailabilityType type,
    DayOfWeek dayOfWeek,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate specificDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Min(1) int maxCapacity
) {}
