package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Promotion;
import com.ecommerce.entity.Review;
import com.ecommerce.entity.User;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.PromotionRepository;
import com.ecommerce.repository.ReviewRepository;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a product over the real database (whose foreign keys refused it with a 409): one in a
 * cart, a review and a promotion is deleted with them; one that was ordered is deactivated, and
 * its order keeps it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"e2e", "fake-payments"})
@DisplayName("Product delete — Integration")
class ProductDeleteIT {

    @Autowired TestRestTemplate rest;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired PromotionRepository promotionRepository;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @LocalServerPort int port;

    private String admin;

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders json(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) h.setBearerAuth(bearer);
        return h;
    }

    @SuppressWarnings("unchecked")
    private static <T> T data(ResponseEntity<Map> res) {
        return (T) res.getBody().get("data");
    }

    @BeforeEach
    void login() {
        ResponseEntity<ApiResponse> res = rest.postForEntity(url("/api/auth/login"), new LoginRequest("admin@test.com", "Admin123!"), ApiResponse.class);
        admin = (String) ((Map<?, ?>) res.getBody().getData()).get("accessToken");
    }

    @SuppressWarnings("rawtypes")
    private Long product(String price) {
        ResponseEntity<Map> res = rest.exchange(url("/api/products"), HttpMethod.POST, new HttpEntity<>(Map.of(
                "name", "Prenda a borrar " + System.nanoTime(), "price", new BigDecimal(price), "stockQuantity", 20,
                "active", true, "featured", true), json(admin)), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isIn(HttpStatus.OK, HttpStatus.CREATED);
        return ((Number) ((Map) data(res)).get("id")).longValue();
    }

    @SuppressWarnings("rawtypes")
    private Order guestOrder(Long productId, int qty) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "guestFirstName", "Rosa", "guestLastName", "Díaz", "guestEmail", "rosa" + System.nanoTime() + "@example.com",
                "items", List.of(Map.of("productId", productId, "quantity", qty)), "paymentMethod", "COD",
                "shippingType", "NATIONAL", "shippingAddress", "Calle 5 #10", "shippingCity", "Puebla"));
        body.putAll(Map.of("shippingState", "Puebla", "shippingZipCode", "72000", "shippingCountry", "MX"));
        ResponseEntity<Map> res = rest.exchange(url("/api/orders/guest"), HttpMethod.POST, new HttpEntity<>(body, json(null)), Map.class);
        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.CREATED);
        return orderRepository.findByOrderNumber((String) ((Map) data(res)).get("orderNumber")).orElseThrow();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> delete(Long id) {
        return rest.exchange(url("/api/products/" + id), HttpMethod.DELETE, new HttpEntity<>(json(admin)), Map.class);
    }

    @Test
    @DisplayName("a product never ordered is deleted, with its cart lines, reviews and promotion entries")
    void neverOrdered_isDeleted() {
        Long id = product("250");
        Long promotionId = tx.execute(st -> {
            Product p = productRepository.findById(id).orElseThrow();
            User user = userRepository.findByEmail("admin@test.com").orElseThrow();
            cartItemRepository.save(CartItem.builder().user(user).product(p).quantity(1).build());
            reviewRepository.save(Review.builder().user(user).product(p).rating(5).title("Linda").comment("Muy bonita").build());
            return promotionRepository.save(Promotion.builder().name("Promo " + System.nanoTime())
                    .discountPercent(BigDecimal.TEN).startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(5))
                    .products(new java.util.HashSet<>(Set.of(p))).build()).getId();
        });

        var res = delete(id);

        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("message")).isEqualTo("Producto eliminado");
        assertThat(productRepository.findById(id)).isEmpty();
        assertThat(promotionRepository.findById(promotionId)).isPresent();
        Integer left = tx.execute(st -> promotionRepository.findById(promotionId).orElseThrow().getProducts().size());
        assertThat(left).isZero();
    }

    @Test
    @DisplayName("a product that was ordered is deactivated (out of the store) and its order keeps it")
    void ordered_isDeactivated() {
        Long id = product("300");
        Order order = guestOrder(id, 1);

        var res = delete(id);

        assertThat(res.getStatusCode()).as(String.valueOf(res.getBody())).isEqualTo(HttpStatus.OK);
        assertThat((String) res.getBody().get("message")).contains("este producto tiene órdenes asociadas");
        Product p = productRepository.findById(id).orElseThrow();
        assertThat(p.isActive()).isFalse();
        assertThat(p.isFeatured()).isFalse();
        Long ordered = tx.execute(st -> orderRepository.findById(order.getId()).orElseThrow().getItems().get(0).getProduct().getId());
        assertThat(ordered).isEqualTo(id);
    }
}
