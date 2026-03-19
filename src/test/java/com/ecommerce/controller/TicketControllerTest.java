package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.TicketRequest;
import com.ecommerce.dto.response.TicketResponse;
import com.ecommerce.entity.TicketStatus;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = TicketController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("TicketController")
class TicketControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TicketService ticketService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "customer@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private TicketResponse stubTicket(Long id) {
        return new TicketResponse(
                id,
                100L,
                "ORD-2025-001",
                1L,
                "Test User",
                "Missing item",
                "My order arrived without item #2.",
                TicketStatus.OPEN,
                null,
                LocalDateTime.of(2025, 2, 1, 9, 0),
                LocalDateTime.of(2025, 2, 1, 9, 0)
        );
    }

    // ── POST /api/orders/{orderNumber}/tickets ────────────────────────────────

    @Nested
    @DisplayName("POST /api/orders/{orderNumber}/tickets")
    class CreateTicket {

        @Test
        @DisplayName("returns 200 with created ticket on valid request")
        void createTicket_200() throws Exception {
            TicketRequest req = new TicketRequest("Missing item", "My order arrived without item #2.");
            when(ticketService.createTicket(eq("customer@test.com"), eq("ORD-2025-001"), any(TicketRequest.class)))
                    .thenReturn(stubTicket(1L));

            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Ticket created"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.orderNumber").value("ORD-2025-001"))
                    .andExpect(jsonPath("$.data.subject").value("Missing item"))
                    .andExpect(jsonPath("$.data.status").value("OPEN"));
        }

        @Test
        @DisplayName("returns 400 when subject is blank")
        void createTicket_400_blankSubject() throws Exception {
            String body = "{\"subject\":\"\",\"description\":\"Some description\"}";

            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when description is blank")
        void createTicket_400_blankDescription() throws Exception {
            String body = "{\"subject\":\"Missing item\",\"description\":\"\"}";

            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when subject exceeds 150 characters")
        void createTicket_400_subjectTooLong() throws Exception {
            String longSubject = "A".repeat(151);
            String body = String.format("{\"subject\":\"%s\",\"description\":\"desc\"}", longSubject);

            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when body is missing required fields")
        void createTicket_400_emptyBody() throws Exception {
            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 when order does not exist")
        void createTicket_404_orderNotFound() throws Exception {
            TicketRequest req = new TicketRequest("Problem", "Order never arrived.");
            when(ticketService.createTicket(anyString(), eq("ORD-UNKNOWN"), any()))
                    .thenThrow(new ResourceNotFoundException("Order", "orderNumber", "ORD-UNKNOWN"));

            mockMvc.perform(post("/api/orders/ORD-UNKNOWN/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 403 when order belongs to another user")
        void createTicket_403_notOwner() throws Exception {
            TicketRequest req = new TicketRequest("Problem", "Not my order.");
            when(ticketService.createTicket(anyString(), eq("ORD-OTHER"), any()))
                    .thenThrow(new AccessDeniedException("This order does not belong to you."));

            mockMvc.perform(post("/api/orders/ORD-OTHER/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when order is not yet delivered")
        void createTicket_400_orderNotDelivered() throws Exception {
            TicketRequest req = new TicketRequest("Where is it?", "Still in transit.");
            when(ticketService.createTicket(anyString(), eq("ORD-2025-001"), any()))
                    .thenThrow(new BadRequestException("You can only report a problem on delivered orders."));

            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when a ticket already exists for the order")
        void createTicket_400_alreadyExists() throws Exception {
            TicketRequest req = new TicketRequest("Again", "Another one.");
            when(ticketService.createTicket(anyString(), eq("ORD-2025-001"), any()))
                    .thenThrow(new BadRequestException("You have already submitted a ticket for this order."));

            mockMvc.perform(post("/api/orders/ORD-2025-001/tickets")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── GET /api/tickets ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/tickets")
    class GetMyTickets {

        @Test
        @DisplayName("returns 200 with list of tickets for authenticated user")
        void getMyTickets_200() throws Exception {
            when(ticketService.getUserTickets("customer@test.com"))
                    .thenReturn(List.of(stubTicket(1L), stubTicket(2L)));

            mockMvc.perform(get("/api/tickets")
                    .principal(customerAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].status").value("OPEN"));
        }

        @Test
        @DisplayName("returns 200 with empty list when user has no tickets")
        void getMyTickets_200_empty() throws Exception {
            when(ticketService.getUserTickets("customer@test.com")).thenReturn(List.of());

            mockMvc.perform(get("/api/tickets")
                    .principal(customerAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("calls service with authenticated user email")
        void getMyTickets_callsServiceWithEmail() throws Exception {
            when(ticketService.getUserTickets(anyString())).thenReturn(List.of());

            mockMvc.perform(get("/api/tickets")
                    .principal(customerAuth()))
                    .andExpect(status().isOk());

            verify(ticketService).getUserTickets("customer@test.com");
        }
    }

    // ── GET /api/tickets/{id} ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/tickets/{id}")
    class GetTicket {

        @Test
        @DisplayName("returns 200 with ticket for the owner")
        void getTicket_200_owner() throws Exception {
            when(ticketService.getTicketById("customer@test.com", 1L)).thenReturn(stubTicket(1L));

            mockMvc.perform(get("/api/tickets/1")
                    .principal(customerAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.orderNumber").value("ORD-2025-001"))
                    .andExpect(jsonPath("$.data.subject").value("Missing item"));
        }

        @Test
        @DisplayName("returns 200 with ticket when accessed by admin")
        void getTicket_200_admin() throws Exception {
            when(ticketService.getTicketById("admin@test.com", 1L)).thenReturn(stubTicket(1L));

            mockMvc.perform(get("/api/tickets/1")
                    .principal(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("returns 404 when ticket does not exist")
        void getTicket_404() throws Exception {
            when(ticketService.getTicketById(anyString(), eq(999L)))
                    .thenThrow(new ResourceNotFoundException("Ticket", "id", 999L));

            mockMvc.perform(get("/api/tickets/999")
                    .principal(customerAuth()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 403 when a different user tries to access the ticket")
        void getTicket_403_notOwner() throws Exception {
            when(ticketService.getTicketById(anyString(), eq(1L)))
                    .thenThrow(new AccessDeniedException("Access denied."));

            mockMvc.perform(get("/api/tickets/1")
                    .principal(customerAuth()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
