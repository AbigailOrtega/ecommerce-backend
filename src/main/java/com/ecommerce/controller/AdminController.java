package com.ecommerce.controller;

import com.ecommerce.dto.request.CouponRequest;
import com.ecommerce.dto.request.PickupLocationRequest;
import com.ecommerce.dto.request.PickupTimeSlotRequest;
import com.ecommerce.dto.request.PromoBannerRequest;
import com.ecommerce.dto.request.PromotionRequest;
import com.ecommerce.dto.request.ShippingConfigRequest;
import com.ecommerce.dto.request.SkydropxCreateShipmentRequest;
import com.ecommerce.dto.request.TicketUpdateRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.*;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.dto.response.SkydropxQuotationResponse;
import com.ecommerce.dto.response.SkydropxShipmentResponse;
import com.ecommerce.dto.response.TicketResponse;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.AnalyticsService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.CouponService;
import com.ecommerce.service.PromoBannerService;
import com.ecommerce.service.PromotionService;
import com.ecommerce.service.PickupLocationService;
import com.ecommerce.service.ReviewService;
import com.ecommerce.service.ShippingConfigService;
import com.ecommerce.service.SkydropxService;
import com.ecommerce.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Admin endpoints")
public class AdminController {

    private final AnalyticsService analyticsService;
    private final OrderService orderService;
    private final ProductService productService;
    private final PromotionService promotionService;
    private final PromoBannerService promoBannerService;
    private final CouponService couponService;
    private final ReviewService reviewService;
    private final TicketService ticketService;
    private final ShippingConfigService shippingConfigService;
    private final PickupLocationService pickupLocationService;
    private final SkydropxService skydropxService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDashboardStats()));
    }

    @GetMapping("/orders")
    @Operation(summary = "Get all orders (paginated)")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders(pageable)));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    @PutMapping("/orders/{id}/status")
    @Operation(summary = "Update order status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Order status updated",
                orderService.updateOrderStatus(id, request)));
    }

    @GetMapping("/products")
    @Operation(summary = "Get all products including inactive (paginated)")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(productService.getAllProductsAdmin(pageable)));
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getFirstName(), u.getLastName(),
                        u.getEmail(), u.getPhone(), u.getRole().name()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return ResponseEntity.ok(ApiResponse.success(new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), user.getRole().name())));
    }

    @GetMapping("/promotions")
    @Operation(summary = "Get all promotions")
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getAllPromotions() {
        return ResponseEntity.ok(ApiResponse.success(promotionService.getAllPromotions()));
    }

    @PostMapping("/promotions")
    @Operation(summary = "Create a promotion")
    public ResponseEntity<ApiResponse<PromotionResponse>> createPromotion(
            @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promotionService.createPromotion(request)));
    }

    @PutMapping("/promotions/{id}")
    @Operation(summary = "Update a promotion")
    public ResponseEntity<ApiResponse<PromotionResponse>> updatePromotion(
            @PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promotionService.updatePromotion(id, request)));
    }

    @DeleteMapping("/promotions/{id}")
    @Operation(summary = "Delete a promotion")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/promotions/{id}/toggle")
    @Operation(summary = "Toggle promotion active status")
    public ResponseEntity<ApiResponse<PromotionResponse>> togglePromotion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(promotionService.togglePromotion(id)));
    }

    // --- Promotional Banners ---

    @GetMapping("/banners")
    @Operation(summary = "Get all promotional banners")
    public ResponseEntity<ApiResponse<List<PromoBannerResponse>>> getAllBanners() {
        return ResponseEntity.ok(ApiResponse.success(promoBannerService.getAllBanners()));
    }

    @PostMapping("/banners")
    @Operation(summary = "Create a promotional banner")
    public ResponseEntity<ApiResponse<PromoBannerResponse>> createBanner(
            @Valid @RequestBody PromoBannerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promoBannerService.createBanner(request)));
    }

    @PutMapping("/banners/{id}")
    @Operation(summary = "Update a promotional banner")
    public ResponseEntity<ApiResponse<PromoBannerResponse>> updateBanner(
            @PathVariable Long id, @Valid @RequestBody PromoBannerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promoBannerService.updateBanner(id, request)));
    }

    @DeleteMapping("/banners/{id}")
    @Operation(summary = "Delete a promotional banner")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        promoBannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/banners/{id}/toggle")
    @Operation(summary = "Toggle banner active status")
    public ResponseEntity<ApiResponse<PromoBannerResponse>> toggleBanner(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(promoBannerService.toggleBanner(id)));
    }

    // --- Coupons ---

    @GetMapping("/coupons")
    @Operation(summary = "Get all coupons")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getAllCoupons() {
        return ResponseEntity.ok(ApiResponse.success(couponService.getAllCoupons()));
    }

    @PostMapping("/coupons")
    @Operation(summary = "Create a coupon")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success(couponService.createCoupon(request)));
    }

    @PutMapping("/coupons/{id}")
    @Operation(summary = "Update a coupon")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success(couponService.updateCoupon(id, request)));
    }

    @DeleteMapping("/coupons/{id}")
    @Operation(summary = "Delete a coupon")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/coupons/{id}/toggle")
    @Operation(summary = "Toggle coupon active status")
    public ResponseEntity<ApiResponse<CouponResponse>> toggleCoupon(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponService.toggleCoupon(id)));
    }

    // --- Reviews ---

    @GetMapping("/reviews/pending")
    @Operation(summary = "Get all pending (unapproved) reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getPendingReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getPendingReviews()));
    }

    @PatchMapping("/reviews/{id}/approve")
    @Operation(summary = "Approve a review")
    public ResponseEntity<ApiResponse<ReviewResponse>> approveReview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Review approved", reviewService.approveReview(id)));
    }

    @DeleteMapping("/reviews/{id}")
    @Operation(summary = "Delete a review (admin)")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id,
            org.springframework.security.core.Authentication auth) {
        reviewService.deleteReview(auth.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }

    // --- Tickets ---

    @GetMapping("/tickets")
    @Operation(summary = "Get all support tickets")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getAllTickets() {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getAllTickets()));
    }

    @PutMapping("/tickets/{id}")
    @Operation(summary = "Update ticket status and admin notes")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicket(
            @PathVariable Long id, @Valid @RequestBody TicketUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ticket updated", ticketService.updateTicket(id, request)));
    }

    // --- Shipping Config ---

    @GetMapping("/shipping/config")
    @Operation(summary = "Get admin shipping configuration")
    public ResponseEntity<ApiResponse<ShippingConfigAdminResponse>> getShippingConfig() {
        return ResponseEntity.ok(ApiResponse.success(shippingConfigService.getAdminConfig()));
    }

    @PutMapping("/shipping/config")
    @Operation(summary = "Update shipping configuration")
    public ResponseEntity<ApiResponse<ShippingConfigAdminResponse>> updateShippingConfig(
            @RequestBody ShippingConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shippingConfigService.updateConfig(request)));
    }

    // --- Pickup Locations ---

    @GetMapping("/pickup-locations")
    @Operation(summary = "Get all pickup locations")
    public ResponseEntity<ApiResponse<List<PickupLocationResponse>>> getAllPickupLocations() {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.getAllLocations()));
    }

    @PostMapping("/pickup-locations")
    @Operation(summary = "Create pickup location")
    public ResponseEntity<ApiResponse<PickupLocationResponse>> createPickupLocation(
            @Valid @RequestBody PickupLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.create(request)));
    }

    @PutMapping("/pickup-locations/{id}")
    @Operation(summary = "Update pickup location")
    public ResponseEntity<ApiResponse<PickupLocationResponse>> updatePickupLocation(
            @PathVariable Long id, @Valid @RequestBody PickupLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.update(id, request)));
    }

    @DeleteMapping("/pickup-locations/{id}")
    @Operation(summary = "Delete pickup location")
    public ResponseEntity<ApiResponse<Void>> deletePickupLocation(@PathVariable Long id) {
        pickupLocationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/pickup-locations/{id}/toggle")
    @Operation(summary = "Toggle pickup location active status")
    public ResponseEntity<ApiResponse<PickupLocationResponse>> togglePickupLocation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.toggle(id)));
    }

    @PostMapping("/pickup-locations/{id}/time-slots")
    @Operation(summary = "Add time slot to pickup location")
    public ResponseEntity<ApiResponse<PickupTimeSlotResponse>> addTimeSlot(
            @PathVariable Long id, @Valid @RequestBody PickupTimeSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.addTimeSlot(id, request)));
    }

    @PutMapping("/pickup-locations/{lid}/time-slots/{sid}")
    @Operation(summary = "Update time slot")
    public ResponseEntity<ApiResponse<PickupTimeSlotResponse>> updateTimeSlot(
            @PathVariable Long lid, @PathVariable Long sid,
            @Valid @RequestBody PickupTimeSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.updateTimeSlot(sid, request)));
    }

    @DeleteMapping("/pickup-locations/{lid}/time-slots/{sid}")
    @Operation(summary = "Delete time slot")
    public ResponseEntity<ApiResponse<Void>> deleteTimeSlot(
            @PathVariable Long lid, @PathVariable Long sid) {
        pickupLocationService.deleteTimeSlot(sid);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/pickup-locations/{lid}/time-slots/{sid}/toggle")
    @Operation(summary = "Toggle time slot active status")
    public ResponseEntity<ApiResponse<PickupTimeSlotResponse>> toggleTimeSlot(
            @PathVariable Long lid, @PathVariable Long sid) {
        return ResponseEntity.ok(ApiResponse.success(pickupLocationService.toggleTimeSlot(sid)));
    }

    // --- Skydropx ---

    @PostMapping("/orders/{id}/skydropx/quotation")
    @Operation(summary = "Cotizar envío con Skydropx para un pedido")
    public ResponseEntity<ApiResponse<SkydropxQuotationResponse>> skydropxQuotation(@PathVariable Long id) {
        com.ecommerce.entity.Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return ResponseEntity.ok(ApiResponse.success(skydropxService.createQuotation(order)));
    }

    @PostMapping("/orders/{id}/skydropx/shipment")
    @Operation(summary = "Crear guía Skydropx con la tarifa seleccionada")
    public ResponseEntity<ApiResponse<OrderResponse>> skydropxCreateShipment(
            @PathVariable Long id, @Valid @RequestBody SkydropxCreateShipmentRequest request) {
        com.ecommerce.entity.Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        SkydropxShipmentResponse shipment = skydropxService.createShipment(request.rateId(), order);
        order.setSkydropxShipmentId(shipment.shipmentId());
        order.setTrackingNumber(shipment.trackingNumber());
        order.setCarrierName(shipment.carrierName());
        order.setLabelUrl(shipment.labelUrl());
        order.setShipmentStatus(shipment.status());
        orderRepository.save(order);
        return ResponseEntity.ok(ApiResponse.success("Guía generada", orderService.getOrderById(id)));
    }

    @GetMapping("/orders/{id}/skydropx/shipment")
    @Operation(summary = "Refrescar datos de guía Skydropx (label URL, tracking, status)")
    public ResponseEntity<ApiResponse<OrderResponse>> skydropxRefreshShipment(@PathVariable Long id) {
        com.ecommerce.entity.Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        if (order.getSkydropxShipmentId() == null) {
            throw new com.ecommerce.exception.BadRequestException("Este pedido no tiene una guía Skydropx.");
        }
        SkydropxShipmentResponse shipment = skydropxService.fetchShipment(order.getSkydropxShipmentId());
        if (!shipment.trackingNumber().isBlank()) order.setTrackingNumber(shipment.trackingNumber());
        if (!shipment.carrierName().isBlank()) order.setCarrierName(shipment.carrierName());
        if (!shipment.labelUrl().isBlank()) order.setLabelUrl(shipment.labelUrl());
        if (!shipment.status().isBlank()) order.setShipmentStatus(shipment.status());
        orderRepository.save(order);
        return ResponseEntity.ok(ApiResponse.success("Datos actualizados", orderService.getOrderById(id)));
    }

    @GetMapping("/orders/{id}/skydropx/label")
    @Operation(summary = "Descargar guía Skydropx como PDF (proxy autenticado)")
    public ResponseEntity<byte[]> skydropxDownloadLabel(@PathVariable Long id) {
        com.ecommerce.entity.Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        if (order.getSkydropxShipmentId() == null) {
            throw new com.ecommerce.exception.BadRequestException("Este pedido no tiene una guía Skydropx.");
        }
        byte[] pdf = skydropxService.downloadLabel(order.getSkydropxShipmentId(), order.getLabelUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "guia-" + order.getOrderNumber() + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @DeleteMapping("/orders/{id}/skydropx/shipment")
    @Operation(summary = "Cancelar guía Skydropx de un pedido")
    public ResponseEntity<ApiResponse<OrderResponse>> skydropxCancelShipment(@PathVariable Long id) {
        com.ecommerce.entity.Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        if (order.getSkydropxShipmentId() == null) {
            throw new com.ecommerce.exception.BadRequestException("Este pedido no tiene una guía Skydropx.");
        }
        skydropxService.cancelShipment(order.getSkydropxShipmentId());
        order.setSkydropxShipmentId(null);
        order.setTrackingNumber(null);
        order.setCarrierName(null);
        order.setLabelUrl(null);
        order.setShipmentStatus("cancelled");
        orderRepository.save(order);
        return ResponseEntity.ok(ApiResponse.success("Guía cancelada", orderService.getOrderById(id)));
    }
}
