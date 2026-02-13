package com.ecommerce.service;

import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.dto.response.OrderItemResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.User;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public DashboardStatsResponse getDashboardStats() {
        long totalOrders = orderRepository.count();
        long totalProducts = productRepository.count();
        long totalUsers = userRepository.count();
        var totalRevenue = orderRepository.sumTotalRevenue();

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        Arrays.stream(OrderStatus.values()).forEach(status ->
                ordersByStatus.put(status.name(), orderRepository.countByStatus(status))
        );

        List<OrderResponse> recentOrders = orderRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5))
                .map(this::mapToOrderResponse)
                .getContent();

        return DashboardStatsResponse.builder()
                .totalOrders(totalOrders)
                .totalProducts(totalProducts)
                .totalUsers(totalUsers)
                .totalRevenue(totalRevenue)
                .recentOrders(recentOrders)
                .ordersByStatus(ordersByStatus)
                .build();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        User user = order.getUser();
        UserResponse userResponse = new UserResponse(user.getId(), user.getFirstName(),
                user.getLastName(), user.getEmail(), user.getPhone(), user.getRole().name());

        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getId(),
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(), item.getProductPrice(),
                        item.getQuantity(), item.getSubtotal()))
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .user(userResponse)
                .items(items)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
