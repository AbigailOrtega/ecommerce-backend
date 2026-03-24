package com.ecommerce.service;

import com.ecommerce.dto.request.GuestOrderItemRequest;
import com.ecommerce.dto.request.GuestOrderRequest;
import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dto.response.OrderItemResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.UpcomingScheduleResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.dto.response.SkydropxRateDto;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import com.ecommerce.repository.PromotionRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final CouponService couponService;
    private final PromotionRepository promotionRepository;
    private final ShippingConfigService shippingConfigService;
    private final GoogleMapsService googleMapsService;
    private final PickupLocationService pickupLocationService;
    private final SkydropxService skydropxService;

    @Transactional
    public OrderResponse createOrder(String email, OrderRequest request) {
        User user = findUserByEmail(email);
        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());

        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Order order = Order.builder()
                .user(user)
                .paymentMethod(request.paymentMethod())
                .paymentId(request.paymentId())
                .notes(request.notes())
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            ProductSize selectedSize = cartItem.getSelectedSize();

            if (product.getColors().isEmpty()) {
                if (product.getStockQuantity() < cartItem.getQuantity()) {
                    throw new BadRequestException("Stock insuficiente para: " + product.getName());
                }
            } else {
                if (selectedSize == null || selectedSize.getStock() < cartItem.getQuantity()) {
                    throw new BadRequestException("Stock insuficiente para: " + product.getName()
                            + (selectedSize != null ? " – talla " + selectedSize.getName() : ""));
                }
            }

            BigDecimal effectivePrice = promotionRepository
                    .findBestActivePromotion(product.getId(), LocalDate.now())
                    .map(p -> product.getPrice()
                            .multiply(BigDecimal.ONE.subtract(
                                    p.getDiscountPercent().divide(BigDecimal.valueOf(100))))
                            .setScale(2, RoundingMode.HALF_UP))
                    .orElse(product.getPrice());

            BigDecimal subtotal = effectivePrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .productPrice(effectivePrice)
                    .quantity(cartItem.getQuantity())
                    .subtotal(subtotal)
                    .selectedSize(selectedSize)
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(subtotal);

            if (product.getColors().isEmpty()) {
                product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
                productRepository.save(product);
            } else if (selectedSize != null) {
                selectedSize.setStock(selectedSize.getStock() - cartItem.getQuantity());
                productSizeRepository.save(selectedSize);
            }
        }

        // --- Coupon discount ---
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            couponService.validateCoupon(request.couponCode());
            Coupon coupon = couponService.consumeCoupon(request.couponCode());
            discountAmount = totalAmount
                    .multiply(coupon.getDiscountPercent().divide(BigDecimal.valueOf(100)))
                    .setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
            order.setCouponCode(coupon.getCode());
            order.setDiscountAmount(discountAmount);
        }

        // --- Shipping ---
        String shippingType = request.shippingType();
        if ("NATIONAL".equals(shippingType)) {
            if (request.shippingAddress() == null || request.shippingAddress().isBlank()
                    || request.shippingCity() == null || request.shippingCity().isBlank()
                    || request.shippingCountry() == null || request.shippingCountry().isBlank()) {
                throw new BadRequestException("Shipping address fields are required for national shipping.");
            }
            ShippingConfig cfg = shippingConfigService.getOrCreate();
            if (!cfg.isNationalEnabled()) {
                throw new BadRequestException("National shipping is not available.");
            }
            BigDecimal cost;
            String methodName = "Envío Nacional";
            if (skydropxService.hasCredentials() && request.skydropxRateId() != null && !request.skydropxRateId().isBlank()) {
                // Re-quote to get a fresh price for the selected rate
                var quotation = skydropxService.createQuotationForAddress(
                        request.shippingAddress(), request.shippingCity(),
                        request.shippingState(), request.shippingZipCode(), request.shippingCountry());
                SkydropxRateDto rate = quotation.rates().stream()
                        .filter(r -> r.id().equals(request.skydropxRateId()))
                        .findFirst()
                        .orElseGet(() -> quotation.rates().isEmpty() ? null : quotation.rates().get(0));
                if (rate == null) throw new BadRequestException("No se encontró la tarifa de envío seleccionada.");
                cost = BigDecimal.valueOf(rate.price()).setScale(2, RoundingMode.HALF_UP);
                methodName = rate.carrier() + " – " + rate.service();
            } else if (cfg.getGoogleMapsApiKey() != null && !cfg.getGoogleMapsApiKey().isBlank()
                    && cfg.getOriginAddress() != null && !cfg.getOriginAddress().isBlank()) {
                String destination = request.shippingAddress() + ", " + request.shippingCity()
                        + ", " + (request.shippingState() != null ? request.shippingState() + ", " : "")
                        + (request.shippingZipCode() != null ? request.shippingZipCode() + ", " : "")
                        + request.shippingCountry();
                double km = googleMapsService.calculateDistanceKm(
                        cfg.getOriginAddress(), destination, cfg.getGoogleMapsApiKey());
                cost = cfg.getNationalBasePrice()
                        .add(cfg.getNationalPricePerKm().multiply(BigDecimal.valueOf(km)))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                cost = cfg.getNationalBasePrice().setScale(2, RoundingMode.HALF_UP);
            }
            order.setShippingCost(cost);
            order.setShippingMethodName(methodName);
            order.setShippingType("NATIONAL");
            if (request.skydropxRateId() != null && !request.skydropxRateId().isBlank()) {
                order.setSkydropxRateId(request.skydropxRateId());
            }
            order.setShippingAddress(request.shippingAddress());
            order.setShippingCity(request.shippingCity());
            order.setShippingState(request.shippingState() != null ? request.shippingState() : "");
            order.setShippingZipCode(request.shippingZipCode() != null ? request.shippingZipCode() : "");
            order.setShippingCountry(request.shippingCountry());
            totalAmount = totalAmount.add(cost);

        } else if ("PICKUP".equals(shippingType)) {
            if (request.pickupLocationId() == null) {
                throw new BadRequestException("pickupLocationId is required for PICKUP shipping.");
            }
            ShippingConfig cfg = shippingConfigService.getOrCreate();
            if (!cfg.isPickupEnabled()) {
                throw new BadRequestException("Pick Up is not available.");
            }
            PickupLocation loc = pickupLocationService.findActiveById(request.pickupLocationId());
            order.setShippingCost(cfg.getPickupCost());
            order.setShippingMethodName("Pick Up – " + loc.getName());
            order.setShippingType("PICKUP");
            order.setPickupLocationName(loc.getName() + ", " + loc.getAddress());
            order.setShippingAddress(loc.getAddress());
            order.setShippingCity(loc.getCity());
            order.setShippingState(loc.getState() != null ? loc.getState() : "");
            order.setShippingZipCode("-");
            order.setShippingCountry("-");
            totalAmount = totalAmount.add(cfg.getPickupCost());

            if (request.pickupDate() == null) {
                throw new BadRequestException("pickupDate es requerido para envío tipo PICKUP.");
            }
            applyPickupDate(order, loc.getId(), request.pickupDate(), request.pickupAvailabilityId());

        } else {
            throw new BadRequestException("shippingType must be NATIONAL or PICKUP");
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        cartItemRepository.deleteByUserId(user.getId());

        OrderResponse orderResponse = mapToResponse(savedOrder, false);
        emailService.sendOrderConfirmationEmail(user, orderResponse);
        return orderResponse;
    }

    @Transactional
    public OrderResponse createGuestOrder(GuestOrderRequest request) {
        Order order = Order.builder()
                .user(null)
                .guestEmail(request.guestEmail())
                .guestFirstName(request.guestFirstName())
                .guestLastName(request.guestLastName())
                .guestPhone(request.guestPhone())
                .paymentMethod(request.paymentMethod())
                .paymentId(request.paymentId())
                .notes(request.notes())
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (GuestOrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.productId()));

            ProductSize selectedSize = null;
            if (itemReq.sizeId() != null) {
                selectedSize = productSizeRepository.findById(itemReq.sizeId())
                        .orElseThrow(() -> new ResourceNotFoundException("ProductSize", "id", itemReq.sizeId()));
            }

            if (product.getColors().isEmpty()) {
                if (product.getStockQuantity() < itemReq.quantity()) {
                    throw new BadRequestException("Stock insuficiente para: " + product.getName());
                }
            } else {
                if (selectedSize == null || selectedSize.getStock() < itemReq.quantity()) {
                    throw new BadRequestException("Stock insuficiente para: " + product.getName()
                            + (selectedSize != null ? " – talla " + selectedSize.getName() : ""));
                }
            }

            BigDecimal effectivePrice = promotionRepository
                    .findBestActivePromotion(product.getId(), LocalDate.now())
                    .map(p -> product.getPrice()
                            .multiply(BigDecimal.ONE.subtract(
                                    p.getDiscountPercent().divide(BigDecimal.valueOf(100))))
                            .setScale(2, RoundingMode.HALF_UP))
                    .orElse(product.getPrice());

            BigDecimal subtotal = effectivePrice.multiply(BigDecimal.valueOf(itemReq.quantity()));

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .productPrice(effectivePrice)
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .selectedSize(selectedSize)
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(subtotal);

            if (product.getColors().isEmpty()) {
                product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());
                productRepository.save(product);
            } else if (selectedSize != null) {
                selectedSize.setStock(selectedSize.getStock() - itemReq.quantity());
                productSizeRepository.save(selectedSize);
            }
        }

        // --- Coupon discount ---
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            couponService.validateCoupon(request.couponCode());
            Coupon coupon = couponService.consumeCoupon(request.couponCode());
            discountAmount = totalAmount
                    .multiply(coupon.getDiscountPercent().divide(BigDecimal.valueOf(100)))
                    .setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
            order.setCouponCode(coupon.getCode());
            order.setDiscountAmount(discountAmount);
        }

        // --- Shipping ---
        String shippingType = request.shippingType();
        if ("NATIONAL".equals(shippingType)) {
            if (request.shippingAddress() == null || request.shippingAddress().isBlank()
                    || request.shippingCity() == null || request.shippingCity().isBlank()
                    || request.shippingCountry() == null || request.shippingCountry().isBlank()) {
                throw new BadRequestException("Shipping address fields are required for national shipping.");
            }
            ShippingConfig cfg = shippingConfigService.getOrCreate();
            if (!cfg.isNationalEnabled()) {
                throw new BadRequestException("National shipping is not available.");
            }
            BigDecimal cost;
            String methodName = "Envío Nacional";
            if (skydropxService.hasCredentials() && request.skydropxRateId() != null && !request.skydropxRateId().isBlank()) {
                var quotation = skydropxService.createQuotationForAddress(
                        request.shippingAddress(), request.shippingCity(),
                        request.shippingState(), request.shippingZipCode(), request.shippingCountry());
                SkydropxRateDto rate = quotation.rates().stream()
                        .filter(r -> r.id().equals(request.skydropxRateId()))
                        .findFirst()
                        .orElseGet(() -> quotation.rates().isEmpty() ? null : quotation.rates().get(0));
                if (rate == null) throw new BadRequestException("No se encontró la tarifa de envío seleccionada.");
                cost = BigDecimal.valueOf(rate.price()).setScale(2, RoundingMode.HALF_UP);
                methodName = rate.carrier() + " – " + rate.service();
            } else if (cfg.getGoogleMapsApiKey() != null && !cfg.getGoogleMapsApiKey().isBlank()
                    && cfg.getOriginAddress() != null && !cfg.getOriginAddress().isBlank()) {
                String destination = request.shippingAddress() + ", " + request.shippingCity()
                        + ", " + (request.shippingState() != null ? request.shippingState() + ", " : "")
                        + (request.shippingZipCode() != null ? request.shippingZipCode() + ", " : "")
                        + request.shippingCountry();
                double km = googleMapsService.calculateDistanceKm(
                        cfg.getOriginAddress(), destination, cfg.getGoogleMapsApiKey());
                cost = cfg.getNationalBasePrice()
                        .add(cfg.getNationalPricePerKm().multiply(BigDecimal.valueOf(km)))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                cost = cfg.getNationalBasePrice().setScale(2, RoundingMode.HALF_UP);
            }
            order.setShippingCost(cost);
            order.setShippingMethodName(methodName);
            order.setShippingType("NATIONAL");
            if (request.skydropxRateId() != null && !request.skydropxRateId().isBlank()) {
                order.setSkydropxRateId(request.skydropxRateId());
            }
            order.setShippingAddress(request.shippingAddress());
            order.setShippingCity(request.shippingCity());
            order.setShippingState(request.shippingState() != null ? request.shippingState() : "");
            order.setShippingZipCode(request.shippingZipCode() != null ? request.shippingZipCode() : "");
            order.setShippingCountry(request.shippingCountry());
            totalAmount = totalAmount.add(cost);

        } else if ("PICKUP".equals(shippingType)) {
            if (request.pickupLocationId() == null) {
                throw new BadRequestException("pickupLocationId is required for PICKUP shipping.");
            }
            ShippingConfig cfg = shippingConfigService.getOrCreate();
            if (!cfg.isPickupEnabled()) {
                throw new BadRequestException("Pick Up is not available.");
            }
            PickupLocation loc = pickupLocationService.findActiveById(request.pickupLocationId());
            order.setShippingCost(cfg.getPickupCost());
            order.setShippingMethodName("Pick Up – " + loc.getName());
            order.setShippingType("PICKUP");
            order.setPickupLocationName(loc.getName() + ", " + loc.getAddress());
            order.setShippingAddress(loc.getAddress());
            order.setShippingCity(loc.getCity());
            order.setShippingState(loc.getState() != null ? loc.getState() : "");
            order.setShippingZipCode("-");
            order.setShippingCountry("-");
            totalAmount = totalAmount.add(cfg.getPickupCost());

            if (request.pickupDate() == null) {
                throw new BadRequestException("pickupDate es requerido para envío tipo PICKUP.");
            }
            applyPickupDate(order, loc.getId(), request.pickupDate(), request.pickupAvailabilityId());

        } else {
            throw new BadRequestException("shippingType must be NATIONAL or PICKUP");
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        OrderResponse orderResponse = mapToResponse(savedOrder, false);
        emailService.sendGuestOrderConfirmationEmail(request.guestEmail(), request.guestFirstName(), orderResponse);
        return orderResponse;
    }

    @Transactional
    public void linkGuestOrders(String email, User user) {
        List<Order> orders = orderRepository.findByGuestEmailAndUserIsNullOrderByCreatedAtDesc(email);
        orders.forEach(o -> { o.setUser(user); o.setGuestEmail(null); });
        if (!orders.isEmpty()) orderRepository.saveAll(orders);
    }

    public List<OrderResponse> getUserOrders(String email) {
        User user = findUserByEmail(email);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(o -> mapToResponse(o, false)).toList();
    }

    public OrderResponse getOrderByNumber(String orderNumber, String email) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        User user = findUserByEmail(email);
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Order does not belong to user");
        }

        return mapToResponse(order, false);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(com.ecommerce.entity.OrderStatus status, String shippingType,
                                            String paymentMethod, java.time.LocalDateTime dateFrom,
                                            java.time.LocalDateTime dateTo, String search, Pageable pageable) {
        String searchTrim = (search != null && !search.isBlank()) ? search.trim() : null;
        String paymentTrim = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.trim() : null;
        return orderRepository.findAllWithFilters(status, shippingType, paymentTrim, dateFrom, dateTo, searchTrim, pageable)
                .map(o -> mapToResponse(o, true));
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return mapToResponse(order, true);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        order.setStatus(request.status());

        if (request.status() == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    if (product.getColors().isEmpty()) {
                        product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                        productRepository.save(product);
                    } else if (item.getSelectedSize() != null) {
                        ProductSize size = item.getSelectedSize();
                        size.setStock(size.getStock() + item.getQuantity());
                        productSizeRepository.save(size);
                    }
                }
            }
        }

        OrderResponse orderResponse = mapToResponse(orderRepository.save(order), true);
        if (order.getUser() != null) {
            emailService.sendOrderStatusUpdateEmail(order.getUser(), orderResponse);
        } else if (order.getGuestEmail() != null) {
            emailService.sendGuestOrderStatusUpdateEmail(
                    order.getGuestEmail(),
                    order.getGuestFirstName() != null ? order.getGuestFirstName() : "Cliente",
                    orderResponse);
        }
        return orderResponse;
    }

    /**
     * Validates capacity and sets pickup date / availability fields on the order.
     * If pickupAvailabilityId is provided, checks per-rule capacity; otherwise falls back to any-rule check.
     */
    private void applyPickupDate(Order order, Long locationId, LocalDate pickupDate, Long pickupAvailabilityId) {
        List<OrderStatus> excluded = List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        List<com.ecommerce.entity.PickupAvailability> rules =
                pickupLocationService.getAvailabilityRulesForDate(locationId, pickupDate);

        if (rules.isEmpty()) {
            throw new BadRequestException("Sin disponibilidad para esta fecha");
        }

        com.ecommerce.entity.PickupAvailability chosenRule;
        if (pickupAvailabilityId != null) {
            chosenRule = rules.stream()
                    .filter(r -> r.getId().equals(pickupAvailabilityId))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("El horario seleccionado no está disponible para esta fecha"));
            long booked = orderRepository.countPickupOrdersForRule(locationId, pickupDate, chosenRule.getId(), excluded);
            if (booked >= chosenRule.getMaxCapacity()) {
                throw new BadRequestException("Sin cupo disponible para el horario seleccionado");
            }
        } else {
            // No specific rule chosen — pick first rule with remaining capacity
            chosenRule = rules.stream()
                    .filter(r -> orderRepository.countPickupOrdersForRule(locationId, pickupDate, r.getId(), excluded) < r.getMaxCapacity())
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Sin cupo disponible para esta fecha"));
        }

        String timeLabel = pickupLocationService.formatTimeLabelPublic(chosenRule);
        order.setPickupDate(pickupDate);
        order.setPickupLocationId(locationId);
        order.setPickupAvailabilityId(chosenRule.getId());
        order.setPickupTimeSlotLabel(timeLabel);
    }

    @Transactional
    public OrderResponse reschedulePickup(String userEmail, String orderNumber, LocalDate newDate, Long availabilityId) {
        User user = findUserByEmail(userEmail);
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("No tienes permiso para modificar este pedido.");
        }
        if (!"PICKUP".equals(order.getShippingType())) {
            throw new BadRequestException("Este pedido no es de tipo recolección.");
        }
        List<OrderStatus> terminalStatuses = List.of(OrderStatus.CANCELLED, OrderStatus.DELIVERED, OrderStatus.REFUNDED);
        if (terminalStatuses.contains(order.getStatus())) {
            throw new BadRequestException("No se puede reagendar un pedido " + order.getStatus().name().toLowerCase() + ".");
        }
        applyPickupDate(order, order.getPickupLocationId(), newDate, availabilityId);
        order.setPickupCancelled(false);
        Order saved = orderRepository.save(order);
        emailService.sendPickupRescheduledConfirmationEmail(
                saved.getUser().getEmail(),
                saved.getUser().getFirstName(),
                saved.getOrderNumber(),
                saved.getPickupLocationName(),
                saved.getPickupDate(),
                saved.getPickupTimeSlotLabel());
        return mapToResponse(saved, true);
    }

    @Transactional
    public OrderResponse customerCancelPickup(String userEmail, String orderNumber) {
        User user = findUserByEmail(userEmail);
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("No tienes permiso para modificar este pedido.");
        }
        if (!"PICKUP".equals(order.getShippingType())) {
            throw new BadRequestException("Este pedido no es de tipo recolección.");
        }
        if (order.isPickupCancelled()) {
            throw new BadRequestException("La recolección ya está cancelada.");
        }
        List<OrderStatus> terminalStatuses = List.of(OrderStatus.CANCELLED, OrderStatus.DELIVERED, OrderStatus.REFUNDED);
        if (terminalStatuses.contains(order.getStatus())) {
            throw new BadRequestException("No se puede cancelar la recolección de un pedido " + order.getStatus().name().toLowerCase() + ".");
        }
        order.setPickupCancelled(true);
        Order saved = orderRepository.save(order);
        emailService.sendPickupRescheduleEmail(
                saved.getUser().getEmail(),
                saved.getUser().getFirstName(),
                saved.getOrderNumber(),
                saved.getPickupLocationName(),
                saved.getPickupDate(),
                saved.getPickupTimeSlotLabel(),
                "Cancelado a tu solicitud — puedes reagendar cuando quieras desde tu cuenta.");
        return mapToResponse(saved, true);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional
    public OrderResponse cancelPickupForAdmin(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (!"PICKUP".equals(order.getShippingType())) {
            throw new BadRequestException("Este pedido no es de tipo recolección.");
        }
        if (order.isPickupCancelled()) {
            throw new BadRequestException("La recolección ya está cancelada.");
        }
        List<OrderStatus> terminalStatuses = List.of(OrderStatus.CANCELLED, OrderStatus.DELIVERED, OrderStatus.REFUNDED);
        if (terminalStatuses.contains(order.getStatus())) {
            throw new BadRequestException("No se puede cancelar la recolección de un pedido " + order.getStatus().name().toLowerCase() + ".");
        }
        order.setPickupCancelled(true);
        Order saved = orderRepository.save(order);
        String recipientEmail = saved.getUser() != null ? saved.getUser().getEmail() : saved.getGuestEmail();
        String firstName = saved.getUser() != null ? saved.getUser().getFirstName() : saved.getGuestFirstName();
        if (recipientEmail != null) {
            emailService.sendPickupRescheduleEmail(
                    recipientEmail, firstName,
                    saved.getOrderNumber(), saved.getPickupLocationName(),
                    saved.getPickupDate(), saved.getPickupTimeSlotLabel(), reason);
        }
        return mapToResponse(saved, true);
    }

    public UpcomingScheduleResponse getUpcomingSchedule() {
        List<OrderStatus> activeStatuses = List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

        List<OrderResponse> shipments = orderRepository
                .findByShippingTypeAndStatusInOrderByCreatedAtAsc("NATIONAL", activeStatuses)
                .stream().map(o -> mapToResponse(o, true)).toList();

        List<Order> pickupOrders = orderRepository
                .findByShippingTypeAndStatusInOrderByCreatedAtAsc("PICKUP", activeStatuses)
                .stream().filter(o -> !o.isPickupCancelled()).toList();

        Map<String, List<OrderResponse>> grouped = new LinkedHashMap<>();
        for (Order o : pickupOrders) {
            String loc = o.getPickupLocationName() != null ? o.getPickupLocationName() : "Sin ubicación";
            grouped.computeIfAbsent(loc, k -> new ArrayList<>()).add(mapToResponse(o, true));
        }

        List<UpcomingScheduleResponse.PickupGroupResponse> pickupGroups = grouped.entrySet().stream()
                .map(e -> new UpcomingScheduleResponse.PickupGroupResponse(e.getKey(), e.getValue()))
                .toList();

        return new UpcomingScheduleResponse(shipments, pickupGroups);
    }

    private OrderResponse mapToResponse(Order order, boolean includeUser) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(),
                        item.getProductPrice(),
                        item.getQuantity(),
                        item.getSubtotal(),
                        item.getSelectedSize() != null ? item.getSelectedSize().getColor().getName() : null,
                        item.getSelectedSize() != null ? item.getSelectedSize().getName() : null
                )).toList();

        UserResponse userResponse = null;
        if (includeUser && order.getUser() != null) {
            User user = order.getUser();
            userResponse = new UserResponse(user.getId(), user.getFirstName(), user.getLastName(),
                    user.getEmail(), user.getPhone(), user.getRole().name());
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .user(userResponse)
                .items(items)
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .couponCode(order.getCouponCode())
                .shippingCost(order.getShippingCost())
                .shippingMethodName(order.getShippingMethodName())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .paymentId(order.getPaymentId())
                .shippingAddress(order.getShippingAddress())
                .shippingCity(order.getShippingCity())
                .shippingState(order.getShippingState())
                .shippingZipCode(order.getShippingZipCode())
                .shippingCountry(order.getShippingCountry())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .shippingType(order.getShippingType())
                .pickupLocationName(order.getPickupLocationName())
                .pickupTimeSlotLabel(order.getPickupTimeSlotLabel())
                .pickupDate(order.getPickupDate())
                .pickupLocationId(order.getPickupLocationId())
                .skydropxRateId(order.getSkydropxRateId())
                .skydropxShipmentId(order.getSkydropxShipmentId())
                .trackingNumber(order.getTrackingNumber())
                .carrierName(order.getCarrierName())
                .labelUrl(order.getLabelUrl())
                .shipmentStatus(order.getShipmentStatus())
                .guestEmail(order.getGuestEmail())
                .guestFirstName(order.getGuestFirstName())
                .guestLastName(order.getGuestLastName())
                .guestPhone(order.getGuestPhone())
                .pickupCancelled(order.isPickupCancelled())
                .build();
    }
}
