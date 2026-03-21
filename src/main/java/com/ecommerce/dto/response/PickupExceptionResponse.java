package com.ecommerce.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PickupExceptionResponse(
    Long id,
    LocalDate date,
    String reason,
    LocalDateTime createdAt
) {}
