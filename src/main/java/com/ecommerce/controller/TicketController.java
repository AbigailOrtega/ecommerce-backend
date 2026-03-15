package com.ecommerce.controller;

import com.ecommerce.dto.request.TicketRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.TicketResponse;
import com.ecommerce.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tickets", description = "Order problem tickets")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/orders/{orderNumber}/tickets")
    @Operation(summary = "Report a problem with a delivered order")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @PathVariable String orderNumber,
            @Valid @RequestBody TicketRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                "Ticket created", ticketService.createTicket(auth.getName(), orderNumber, request)));
    }

    @GetMapping("/tickets")
    @Operation(summary = "Get my tickets")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getMyTickets(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getUserTickets(auth.getName())));
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "Get ticket by ID")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicket(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getTicketById(auth.getName(), id)));
    }
}
