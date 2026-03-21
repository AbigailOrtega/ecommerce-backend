package com.ecommerce.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PickupExceptionRequest(
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
    String reason
) {}
