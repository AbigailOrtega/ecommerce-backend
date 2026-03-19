package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.CartService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = CartController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("CartController")
class CartControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CartService cartService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns a mock Authentication that makes authentication.getName() == "user@test.com". */
    private UsernamePasswordAuthenticationToken mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                "user@test.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    private CartItemResponse stubCartItem(Long id) {
        ProductResponse product = new ProductResponse(
                1L, "Test T-Shirt", "desc",
                BigDecimal.valueOf(29.99), null, "SKU-001",
                50, null, List.of(), "test-t-shirt",
                false, true, List.of(), null, List.of(),
                null, null, null
        );
        return new CartItemResponse(id, product, 2, BigDecimal.valueOf(59.98), null, null);
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/cart")
    class GetCartItems {

        @Test
        @DisplayName("returns 200 with list of cart items")
        void getCart_200() throws Exception {
            when(cartService.getCartItems("user@test.com"))
                    .thenReturn(List.of(stubCartItem(1L), stubCartItem(2L)));

            mockMvc.perform(get("/api/cart")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].quantity").value(2))
                    .andExpect(jsonPath("$.data[0].subtotal").value(59.98));
        }

        @Test
        @DisplayName("returns 200 with empty list when cart is empty")
        void getCart_200_empty() throws Exception {
            when(cartService.getCartItems("user@test.com")).thenReturn(List.of());

            mockMvc.perform(get("/api/cart")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("calls service with the authenticated user email")
        void getCart_callsServiceWithEmail() throws Exception {
            when(cartService.getCartItems(anyString())).thenReturn(List.of());

            mockMvc.perform(get("/api/cart")
                    .principal(mockAuth()))
                    .andExpect(status().isOk());

            verify(cartService).getCartItems("user@test.com");
        }
    }

    // ── POST /api/cart ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/cart")
    class AddToCart {

        @Test
        @DisplayName("returns 201 with created cart item on valid request")
        void addToCart_201() throws Exception {
            CartItemRequest req = new CartItemRequest(1L, 2, null);
            when(cartService.addToCart(eq("user@test.com"), any(CartItemRequest.class)))
                    .thenReturn(stubCartItem(1L));

            mockMvc.perform(post("/api/cart")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Item added to cart"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.product.name").value("Test T-Shirt"));
        }

        @Test
        @DisplayName("returns 400 when productId is null")
        void addToCart_400_nullProductId() throws Exception {
            // productId is @NotNull — missing it triggers validation
            String body = "{\"quantity\":1}";

            mockMvc.perform(post("/api/cart")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when quantity is less than 1")
        void addToCart_400_quantityZero() throws Exception {
            CartItemRequest req = new CartItemRequest(1L, 0, null);

            mockMvc.perform(post("/api/cart")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 when product does not exist")
        void addToCart_404_productNotFound() throws Exception {
            CartItemRequest req = new CartItemRequest(999L, 1, null);
            when(cartService.addToCart(anyString(), any()))
                    .thenThrow(new ResourceNotFoundException("Product", "id", 999L));

            mockMvc.perform(post("/api/cart")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when service throws BadRequestException (insufficient stock)")
        void addToCart_400_insufficientStock() throws Exception {
            CartItemRequest req = new CartItemRequest(1L, 999, null);
            when(cartService.addToCart(anyString(), any()))
                    .thenThrow(new BadRequestException("Insufficient stock for product: Test T-Shirt"));

            mockMvc.perform(post("/api/cart")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── PUT /api/cart/{itemId} ────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/cart/{itemId}")
    class UpdateCartItem {

        @Test
        @DisplayName("returns 200 with updated cart item")
        void updateCartItem_200() throws Exception {
            when(cartService.updateCartItem("user@test.com", 1L, 3))
                    .thenReturn(new CartItemResponse(1L, null, 3, BigDecimal.valueOf(89.97), null, null));

            mockMvc.perform(put("/api/cart/1")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("quantity", 3))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Cart updated"))
                    .andExpect(jsonPath("$.data.quantity").value(3));
        }

        @Test
        @DisplayName("returns 404 when cart item does not exist")
        void updateCartItem_404() throws Exception {
            when(cartService.updateCartItem(anyString(), eq(99L), anyInt()))
                    .thenThrow(new ResourceNotFoundException("CartItem", "id", 99L));

            mockMvc.perform(put("/api/cart/99")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("quantity", 1))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when item belongs to another user")
        void updateCartItem_400_wrongUser() throws Exception {
            when(cartService.updateCartItem(anyString(), eq(5L), anyInt()))
                    .thenThrow(new BadRequestException("Cart item does not belong to user"));

            mockMvc.perform(put("/api/cart/5")
                    .principal(mockAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("quantity", 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── DELETE /api/cart/{itemId} ─────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/cart/{itemId}")
    class RemoveFromCart {

        @Test
        @DisplayName("returns 200 with success message")
        void removeFromCart_200() throws Exception {
            doNothing().when(cartService).removeFromCart("user@test.com", 1L);

            mockMvc.perform(delete("/api/cart/1")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Item removed from cart"));

            verify(cartService).removeFromCart("user@test.com", 1L);
        }

        @Test
        @DisplayName("returns 404 when item does not exist")
        void removeFromCart_404() throws Exception {
            doThrow(new ResourceNotFoundException("CartItem", "id", 99L))
                    .when(cartService).removeFromCart(anyString(), eq(99L));

            mockMvc.perform(delete("/api/cart/99")
                    .principal(mockAuth()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/cart")
    class ClearCart {

        @Test
        @DisplayName("returns 200 with cart cleared message")
        void clearCart_200() throws Exception {
            doNothing().when(cartService).clearCart("user@test.com");

            mockMvc.perform(delete("/api/cart")
                    .principal(mockAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Cart cleared"));

            verify(cartService).clearCart("user@test.com");
        }
    }
}
