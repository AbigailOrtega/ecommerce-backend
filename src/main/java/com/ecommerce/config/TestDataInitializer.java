package com.ecommerce.config;

import com.ecommerce.entity.*;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ShippingConfigRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds test data when the "e2e" Spring profile is active.
 *
 *   Users:
 *     test@test.com  / Test123!  → CUSTOMER
 *     admin@test.com / Admin123! → ADMIN
 *
 *   Products (no variants — show "Add to Cart" button directly):
 *     Test T-Shirt, Test Jeans, Test Sneakers
 *
 *   Orders:
 *     ORD-E2E-NATL  → NATIONAL shipping  (PENDING)
 *     ORD-E2E-PKUP  → PICKUP shipping    (CONFIRMED)
 *
 * Run the backend with:
 *   mvn spring-boot:run -Dspring-boot.run.profiles=e2e
 */
@Slf4j
@Component
@Profile("e2e")
@RequiredArgsConstructor
public class TestDataInitializer implements ApplicationRunner {

    private final UserRepository           userRepository;
    private final PasswordEncoder          passwordEncoder;
    private final ProductRepository        productRepository;
    private final CategoryRepository       categoryRepository;
    private final OrderRepository          orderRepository;
    private final ShippingConfigRepository shippingConfigRepository;

    @Override
    public void run(ApplicationArguments args) {
        createUserIfAbsent("Test", "User",  "test@test.com",  "Test123!",  Role.CUSTOMER);
        createUserIfAbsent("Test", "Admin", "admin@test.com", "Admin123!", Role.ADMIN);
        log.info("[e2e] Test users seeded: test@test.com (CUSTOMER), admin@test.com (ADMIN)");

        Category ropa    = createCategoryIfAbsent("Ropa",     "ropa");
        Category calzado = createCategoryIfAbsent("Calzado",  "calzado");
        log.info("[e2e] Test categories seeded");

        createProductIfAbsent("Test T-Shirt",  "A comfortable test t-shirt", BigDecimal.valueOf(29.99), 50, "test-t-shirt",  ropa);
        createProductIfAbsent("Test Jeans",    "Classic blue jeans",         BigDecimal.valueOf(59.99), 30, "test-jeans",    ropa);
        createProductIfAbsent("Test Sneakers", "Comfortable sneakers",       BigDecimal.valueOf(89.99), 20, "test-sneakers", calzado);
        log.info("[e2e] Test products seeded");

        seedOrders();
        log.info("[e2e] Test orders seeded");

        seedShippingConfig();
        log.info("[e2e] Shipping config seeded");
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private void createUserIfAbsent(String firstName, String lastName,
                                     String email, String rawPassword, Role role) {
        if (userRepository.existsByEmail(email)) return;

        userRepository.save(User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .build());
    }

    // ── Products ──────────────────────────────────────────────────────────────

    private Category createCategoryIfAbsent(String name, String slug) {
        return categoryRepository.findBySlug(slug).orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .name(name)
                        .slug(slug)
                        .build()));
    }

    private void createProductIfAbsent(String name, String description,
                                        BigDecimal price, int stock, String slug,
                                        Category category) {
        if (productRepository.findBySlug(slug).isPresent()) return;

        Product product = Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .stockQuantity(stock)
                .slug(slug)
                .active(true)
                .build();
        product.getCategories().add(category);
        productRepository.save(product);
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    private void seedShippingConfig() {
        ShippingConfig cfg = shippingConfigRepository.findById(1L)
                .orElse(ShippingConfig.builder().id(1L).build());
        cfg.setPickupEnabled(true);
        shippingConfigRepository.save(cfg);
    }

    private void seedOrders() {
        if (orderRepository.findByOrderNumber("ORD-E2E-NATL").isPresent()) return;

        User customer = userRepository.findByEmail("test@test.com").orElseThrow();
        Product tshirt = productRepository.findBySlug("test-t-shirt").orElseThrow();

        // --- NATIONAL order (needed for Skydropx + order-table tests) ---
        Order national = Order.builder()
                .orderNumber("ORD-E2E-NATL")
                .user(customer)
                .totalAmount(BigDecimal.valueOf(59.98))
                .status(OrderStatus.PENDING)
                .paymentMethod("COD")
                .shippingAddress("Calle Falsa 123")
                .shippingCity("Ciudad de México")
                .shippingState("CDMX")
                .shippingZipCode("06600")
                .shippingCountry("MX")
                .shippingType("NATIONAL")
                .shippingMethodName("Estafeta Express")
                .shippingCost(BigDecimal.valueOf(120.00))
                .discountAmount(BigDecimal.ZERO)
                .build();

        OrderItem item1 = OrderItem.builder()
                .productName(tshirt.getName())
                .productPrice(tshirt.getPrice())
                .quantity(2)
                .subtotal(tshirt.getPrice().multiply(BigDecimal.valueOf(2)))
                .product(tshirt)
                .build();
        national.addItem(item1);
        orderRepository.save(national);

        // --- PICKUP order ---
        if (orderRepository.findByOrderNumber("ORD-E2E-PKUP").isEmpty()) {
            Order pickup = Order.builder()
                    .orderNumber("ORD-E2E-PKUP")
                    .user(customer)
                    .totalAmount(BigDecimal.valueOf(89.99))
                    .status(OrderStatus.CONFIRMED)
                    .paymentMethod("STRIPE")
                    .shippingAddress("Sucursal Centro")
                    .shippingCity("Ciudad de México")
                    .shippingState("CDMX")
                    .shippingZipCode("06600")
                    .shippingCountry("MX")
                    .shippingType("PICKUP")
                    .pickupLocationName("Sucursal Centro")
                    .pickupTimeSlotLabel("Lunes 10:00 – 14:00")
                    .shippingCost(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .build();

            Product sneakers = productRepository.findBySlug("test-sneakers").orElseThrow();
            OrderItem item2 = OrderItem.builder()
                    .productName(sneakers.getName())
                    .productPrice(sneakers.getPrice())
                    .quantity(1)
                    .subtotal(sneakers.getPrice())
                    .product(sneakers)
                    .build();
            pickup.addItem(item2);
            orderRepository.save(pickup);
        }
    }
}
