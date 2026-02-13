package com.ecommerce.controller;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "Shopping cart endpoints")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get cart items")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> getCartItems(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getCartItems(authentication.getName())));
    }

    @PostMapping
    @Operation(summary = "Add item to cart")
    public ResponseEntity<ApiResponse<CartItemResponse>> addToCart(
            Authentication authentication, @Valid @RequestBody CartItemRequest request) {
        CartItemResponse response = cartService.addToCart(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Item added to cart", response));
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update cart item quantity")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItem(
            Authentication authentication, @PathVariable Long itemId, @RequestBody Map<String, Integer> request) {
        CartItemResponse response = cartService.updateCartItem(
                authentication.getName(), itemId, request.get("quantity"));
        return ResponseEntity.ok(ApiResponse.success("Cart updated", response));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove item from cart")
    public ResponseEntity<ApiResponse<Void>> removeFromCart(
            Authentication authentication, @PathVariable Long itemId) {
        cartService.removeFromCart(authentication.getName(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", null));
    }

    @DeleteMapping
    @Operation(summary = "Clear cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(Authentication authentication) {
        cartService.clearCart(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", null));
    }
}
