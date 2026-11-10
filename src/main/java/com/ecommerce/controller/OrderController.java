package com.ecommerce.controller;

import com.ecommerce.dto.request.GuestOrderRequest;
import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.request.ReschedulePickupRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.ratelimit.ClientIp;
import com.ecommerce.ratelimit.RateLimit;
import com.ecommerce.ratelimit.RateLimiter;
import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Order endpoints")
public class OrderController {

    private final OrderService orderService;
    private final RateLimiter rateLimiter;

    @GetMapping("/track/{orderNumber}")
    @Operation(summary = "Track an order by order number (no authentication required)")
    public ResponseEntity<ApiResponse<OrderResponse>> trackOrder(@PathVariable String orderNumber,
                                                                 HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.TRACK_ORDER_PER_IP, ClientIp.of(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderPublic(orderNumber)));
    }

    @PostMapping("/guest")
    @Operation(summary = "Create a guest order without authentication")
    public ResponseEntity<ApiResponse<OrderResponse>> createGuestOrder(@Valid @RequestBody GuestOrderRequest request,
                                                                       HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.GUEST_ORDER_PER_IP, ClientIp.of(httpRequest));
        rateLimiter.consume(RateLimit.GUEST_ORDER_PER_EMAIL, request.guestEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", orderService.createGuestOrder(request)));
    }

    @PostMapping
    @Operation(summary = "Create a new order from cart")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            Authentication authentication, @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Order placed successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get user's orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getUserOrders(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserOrders(authentication.getName())));
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get order by order number")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByNumber(
            @PathVariable String orderNumber, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrderByNumber(orderNumber, authentication.getName())));
    }

    @PostMapping("/{orderNumber}/reschedule-pickup")
    @Operation(summary = "Reschedule a pickup (can be customer-initiated or after seller cancellation)")
    public ResponseEntity<ApiResponse<OrderResponse>> reschedulePickup(
            @PathVariable String orderNumber,
            Authentication authentication,
            @Valid @RequestBody ReschedulePickupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.reschedulePickup(authentication.getName(), orderNumber,
                        request.pickupDate(), request.pickupAvailabilityId())));
    }

    @PatchMapping("/{orderNumber}/cancel-pickup")
    @Operation(summary = "Customer cancels their own pickup so they can reschedule later")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelPickup(
            @PathVariable String orderNumber,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Recolección cancelada",
                orderService.customerCancelPickup(authentication.getName(), orderNumber)));
    }

    // ── The pickup link of the emails (guests have no account): the token is the permission ──

    @GetMapping("/{orderNumber}/pickup")
    @Operation(summary = "The pickup of an order, for its emailed link (X-Pickup-Token)")
    public ResponseEntity<ApiResponse<OrderResponse>> pickupWithToken(
            @PathVariable String orderNumber, @RequestHeader(value = "X-Pickup-Token", required = false) String token,
            HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.PICKUP_MANAGE_PER_IP, ClientIp.of(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(orderService.getPickupWithToken(orderNumber, token)));
    }

    @PostMapping("/{orderNumber}/pickup/reschedule")
    @Operation(summary = "Reschedule a pickup from its emailed link (X-Pickup-Token)")
    public ResponseEntity<ApiResponse<OrderResponse>> reschedulePickupWithToken(
            @PathVariable String orderNumber, @RequestHeader(value = "X-Pickup-Token", required = false) String token,
            @Valid @RequestBody ReschedulePickupRequest request, HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.PICKUP_MANAGE_PER_IP, ClientIp.of(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Recolección reagendada",
                orderService.reschedulePickupWithToken(orderNumber, token, request.pickupDate(), request.pickupAvailabilityId())));
    }

    @PostMapping("/{orderNumber}/pickup/cancel")
    @Operation(summary = "Cancel a pickup from its emailed link (X-Pickup-Token); it can be rescheduled later")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelPickupWithToken(
            @PathVariable String orderNumber, @RequestHeader(value = "X-Pickup-Token", required = false) String token,
            HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimit.PICKUP_MANAGE_PER_IP, ClientIp.of(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Recolección cancelada",
                orderService.cancelPickupWithToken(orderNumber, token)));
    }
}
