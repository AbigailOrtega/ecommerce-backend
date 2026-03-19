package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.OrderService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = OrderController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrderService orderService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                "customer@test.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    private OrderResponse stubOrder(String orderNumber) {
        return OrderResponse.builder()
                .id(1L)
                .orderNumber(orderNumber)
                .totalAmount(BigDecimal.valueOf(129.99))
                .discountAmount(BigDecimal.ZERO)
                .shippingCost(BigDecimal.valueOf(50.00))
                .status("PENDING")
                .paymentMethod("COD")
                .shippingType("NATIONAL")
                .shippingAddress("Calle Falsa 123")
                .shippingCity("Ciudad de México")
                .shippingState("CDMX")
                .shippingZipCode("06600")
                .shippingCountry("MX")
                .items(List.of())
                .build();
    }

    private OrderRequest validOrderRequest() {
        return new OrderRequest(
                "Calle Falsa 123", "Ciudad de México", "CDMX", "06600", "MX",
                "COD", null, null, null,
                "NATIONAL", null, null, null
        );
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        @Test
        @DisplayName("returns 201 with created order on valid request")
        void createOrder_201() throws Exception {
            OrderRequest req = validOrderRequest();
            when(orderService.createOrder(eq("customer@test.com"), any(OrderRequest.class)))
                    .thenReturn(stubOrder("ORD-001"));

            mockMvc.perform(post("/api/orders")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Order placed successfully"))
                    .andExpect(jsonPath("$.data.orderNumber").value("ORD-001"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalAmount").value(129.99));
        }

        @Test
        @DisplayName("returns 400 when paymentMethod is blank")
        void createOrder_400_missingPaymentMethod() throws Exception {
            // paymentMethod is @NotBlank — omitting it triggers validation
            String body = "{\"shippingType\":\"NATIONAL\"}";

            mockMvc.perform(post("/api/orders")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when shippingType is blank")
        void createOrder_400_missingShippingType() throws Exception {
            String body = "{\"paymentMethod\":\"COD\"}";

            mockMvc.perform(post("/api/orders")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when cart is empty")
        void createOrder_400_emptyCart() throws Exception {
            when(orderService.createOrder(anyString(), any()))
                    .thenThrow(new BadRequestException("Cart is empty"));

            mockMvc.perform(post("/api/orders")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validOrderRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cart is empty"));
        }

        @Test
        @DisplayName("calls service with the authenticated user email")
        void createOrder_callsServiceWithEmail() throws Exception {
            when(orderService.createOrder(anyString(), any())).thenReturn(stubOrder("ORD-002"));

            mockMvc.perform(post("/api/orders")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validOrderRequest())))
                    .andExpect(status().isCreated());

            verify(orderService).createOrder(eq("customer@test.com"), any(OrderRequest.class));
        }
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/orders")
    class GetUserOrders {

        @Test
        @DisplayName("returns 200 with list of user orders")
        void getUserOrders_200() throws Exception {
            when(orderService.getUserOrders("customer@test.com"))
                    .thenReturn(List.of(stubOrder("ORD-001"), stubOrder("ORD-002")));

            mockMvc.perform(get("/api/orders")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-001"));
        }

        @Test
        @DisplayName("returns 200 with empty list when user has no orders")
        void getUserOrders_200_empty() throws Exception {
            when(orderService.getUserOrders("customer@test.com")).thenReturn(List.of());

            mockMvc.perform(get("/api/orders")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("calls service with the authenticated user email")
        void getUserOrders_callsServiceWithEmail() throws Exception {
            when(orderService.getUserOrders(anyString())).thenReturn(List.of());

            mockMvc.perform(get("/api/orders")
                    .principal(mockAuth()))
                    .andExpect(status().isOk());

            verify(orderService).getUserOrders("customer@test.com");
        }
    }

    // ── GET /api/orders/{orderNumber} ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/orders/{orderNumber}")
    class GetOrderByNumber {

        @Test
        @DisplayName("returns 200 with order details")
        void getOrderByNumber_200() throws Exception {
            when(orderService.getOrderByNumber("ORD-001", "customer@test.com"))
                    .thenReturn(stubOrder("ORD-001"));

            mockMvc.perform(get("/api/orders/ORD-001")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderNumber").value("ORD-001"))
                    .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
                    .andExpect(jsonPath("$.data.shippingType").value("NATIONAL"));
        }

        @Test
        @DisplayName("returns 404 when order number does not exist")
        void getOrderByNumber_404() throws Exception {
            when(orderService.getOrderByNumber(eq("ORD-GHOST"), anyString()))
                    .thenThrow(new ResourceNotFoundException("Order", "orderNumber", "ORD-GHOST"));

            mockMvc.perform(get("/api/orders/ORD-GHOST")
                    .principal(mockAuth()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when order belongs to a different user")
        void getOrderByNumber_400_wrongUser() throws Exception {
            when(orderService.getOrderByNumber(eq("ORD-OTHER"), anyString()))
                    .thenThrow(new BadRequestException("Order does not belong to user"));

            mockMvc.perform(get("/api/orders/ORD-OTHER")
                    .principal(mockAuth()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("calls service with orderNumber path variable and authenticated email")
        void getOrderByNumber_callsServiceCorrectly() throws Exception {
            when(orderService.getOrderByNumber(anyString(), anyString())).thenReturn(stubOrder("ORD-001"));

            mockMvc.perform(get("/api/orders/ORD-001")
                    .principal(mockAuth()))
                    .andExpect(status().isOk());

            verify(orderService).getOrderByNumber("ORD-001", "customer@test.com");
        }
    }
}
