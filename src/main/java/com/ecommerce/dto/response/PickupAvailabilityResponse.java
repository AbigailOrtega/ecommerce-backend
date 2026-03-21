package com.ecommerce.dto.response;

import com.ecommerce.entity.AvailabilityType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PickupAvailabilityResponse(
    Long id,
    AvailabilityType type,
    DayOfWeek dayOfWeek,
    LocalDate specificDate,
    LocalTime startTime,
    LocalTime endTime,
    int maxCapacity,
    boolean active,
    List<PickupExceptionResponse> exceptions
) {}
