package com.ecommerce.service;

import com.ecommerce.dto.response.DashboardStatsResponse;
import com.ecommerce.dto.response.OrderItemResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.PageView;
import com.ecommerce.entity.User;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PageViewRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
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
    private final PageViewRepository pageViewRepository;

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

    public void recordPageView(String path, String sessionId) {
        pageViewRepository.save(PageView.builder()
                .path(path)
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public Map<String, Object> getPageViewSummary() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("today",     pageViewRepository.countByTimestampBetween(now.toLocalDate().atStartOfDay(), now));
        summary.put("thisWeek",  pageViewRepository.countByTimestampBetween(now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(), now));
        summary.put("thisMonth", pageViewRepository.countByTimestampBetween(now.withDayOfMonth(1).toLocalDate().atStartOfDay(), now));
        summary.put("thisYear",  pageViewRepository.countByTimestampBetween(now.withDayOfYear(1).toLocalDate().atStartOfDay(), now));
        summary.put("total",     pageViewRepository.count());
        return summary;
    }

    public List<Map<String, Object>> getPageViewsByPeriod(String period, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = switch (period) {
            case "hour"  -> pageViewRepository.countByHour(from, to);
            case "week"  -> pageViewRepository.countByWeek(from, to);
            case "month" -> pageViewRepository.countByMonth(from, to);
            case "year"  -> pageViewRepository.countByYear(from, to);
            default      -> pageViewRepository.countByDay(from, to);
        };
        return rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", r[0].toString());
            m.put("count", ((Number) r[1]).longValue());
            return m;
        }).toList();
    }

    public List<Map<String, Object>> getPageViewsByPage(LocalDateTime from, LocalDateTime to) {
        return pageViewRepository.countByPage(from, to).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", r[0].toString());
            m.put("count", ((Number) r[1]).longValue());
            return m;
        }).toList();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        User user = order.getUser();
        UserResponse userResponse = user != null
                ? new UserResponse(user.getId(), user.getFirstName(),
                        user.getLastName(), user.getEmail(), user.getPhone(), user.getRole().name())
                : new UserResponse(null, order.getGuestFirstName(),
                        order.getGuestLastName(), order.getGuestEmail(), order.getGuestPhone(), "GUEST");

        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getId(),
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(), item.getProductPrice(),
                        item.getQuantity(), item.getSubtotal(),
                        item.getSelectedSize() != null ? item.getSelectedSize().getColor().getName() : null,
                        item.getSelectedSize() != null ? item.getSelectedSize().getName() : null))
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .user(userResponse)
                .guestEmail(order.getGuestEmail())
                .guestFirstName(order.getGuestFirstName())
                .guestLastName(order.getGuestLastName())
                .guestPhone(order.getGuestPhone())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
