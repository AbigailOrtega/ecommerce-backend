package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketRequest(
    @NotBlank @Size(max = 150) String subject,
    @NotBlank @Size(max = 2000) String description
) {}
