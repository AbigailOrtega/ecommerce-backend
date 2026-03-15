package com.ecommerce.dto.response;

import com.ecommerce.entity.TicketStatus;
import java.time.LocalDateTime;

public record TicketResponse(
    Long id,
    Long orderId,
    String orderNumber,
    Long userId,
    String userName,
    String subject,
    String description,
    TicketStatus status,
    String adminNotes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
