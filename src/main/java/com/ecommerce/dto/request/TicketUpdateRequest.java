package com.ecommerce.dto.request;

import com.ecommerce.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketUpdateRequest(
    @NotNull TicketStatus status,
    String adminNotes
) {}
