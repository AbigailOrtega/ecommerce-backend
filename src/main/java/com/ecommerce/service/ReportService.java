package com.ecommerce.service;

import com.ecommerce.dto.response.InventoryItem;
import com.ecommerce.dto.response.ProductSalesItem;
import com.ecommerce.dto.response.SalesDataPoint;
import com.ecommerce.dto.response.SalesReportResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    private static final List<OrderStatus> EXCLUDED = List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    // ── Sales report ─────────────────────────────────────────────────────────

    public SalesReportResponse getSalesReport(String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        String periodLabel;

        switch (period) {
            case "week" -> {
                start = now.minusDays(7);
                periodLabel = "Última semana";
            }
            case "year" -> {
                start = now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                periodLabel = "Este año";
            }
            default -> {
                start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                periodLabel = "Este mes";
            }
        }

        List<Order> orders = orderRepository.findByCreatedAtBetween(start, now);

        Map<String, List<Order>> byDate = orders.stream()
                .filter(o -> !EXCLUDED.contains(o.getStatus()))
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().toLocalDate().toString()));

        List<SalesDataPoint> data = new TreeMap<>(byDate).entrySet().stream()
                .map(e -> {
                    List<Order> dayOrders = e.getValue();
                    BigDecimal revenue = dayOrders.stream()
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new SalesDataPoint(e.getKey(), dayOrders.size(), revenue);
                }).toList();

        BigDecimal totalRevenue = data.stream()
                .map(SalesDataPoint::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalOrders = data.stream().mapToLong(SalesDataPoint::orderCount).sum();

        return new SalesReportResponse(periodLabel, data, totalRevenue, totalOrders);
    }

    // ── Product sales ─────────────────────────────────────────────────────────

    public List<ProductSalesItem> getTopSellingProducts(int limit) {
        return buildSalesItems(orderItemRepository.findProductSalesRanked(EXCLUDED), limit, false);
    }

    public List<ProductSalesItem> getLeastSellingProducts(int limit) {
        List<Object[]> ranked = orderItemRepository.findProductSalesRanked(EXCLUDED);
        Map<Long, ProductSalesItem> salesMap = new LinkedHashMap<>();
        for (Object[] row : ranked) {
            Long pid = (Long) row[0];
            String name = (String) row[1];
            long units = ((Number) row[2]).longValue();
            BigDecimal revenue = (BigDecimal) row[3];
            int stock = stockOf(pid);
            salesMap.put(pid, new ProductSalesItem(pid, name, units, revenue, stock));
        }

        List<ProductSalesItem> result = new ArrayList<>(salesMap.values());

        // Include active products with zero sales
        productRepository.findAll().stream()
                .filter(p -> !salesMap.containsKey(p.getId()))
                .forEach(p -> result.add(
                        new ProductSalesItem(p.getId(), p.getName(), 0, BigDecimal.ZERO, p.getStockQuantity())));

        return result.stream()
                .sorted(Comparator.comparingLong(ProductSalesItem::unitsSold))
                .limit(limit)
                .toList();
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    public List<InventoryItem> getInventoryReport() {
        return productRepository.findAll().stream()
                .map(p -> new InventoryItem(p.getId(), p.getName(), p.getSku(),
                        effectiveStock(p), p.getPrice(), p.isActive(), outOfStockVariants(p)))
                .sorted(Comparator.comparing(InventoryItem::name))
                .toList();
    }

    public List<InventoryItem> getOutOfStockProducts() {
        return productRepository.findAll().stream()
                .filter(p -> {
                    if (p.getColors() != null && !p.getColors().isEmpty()) {
                        return p.getColors().stream()
                                .anyMatch(c -> c.getSizes().stream().anyMatch(s -> s.getStock() == 0));
                    }
                    return p.getStockQuantity() == 0;
                })
                .map(p -> new InventoryItem(p.getId(), p.getName(), p.getSku(),
                        effectiveStock(p), p.getPrice(), p.isActive(), outOfStockVariants(p)))
                .sorted(Comparator.comparing(InventoryItem::name))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<ProductSalesItem> buildSalesItems(List<Object[]> rows, int limit, boolean ascending) {
        return rows.stream()
                .limit(limit)
                .map(row -> {
                    Long pid = (Long) row[0];
                    String name = (String) row[1];
                    long units = ((Number) row[2]).longValue();
                    BigDecimal revenue = (BigDecimal) row[3];
                    return new ProductSalesItem(pid, name, units, revenue, stockOf(pid));
                }).toList();
    }

    private int stockOf(Long productId) {
        return productRepository.findById(productId)
                .map(this::effectiveStock)
                .orElse(0);
    }

    private int effectiveStock(Product p) {
        if (p.getColors() != null && !p.getColors().isEmpty()) {
            return p.getColors().stream()
                    .flatMap(c -> c.getSizes().stream())
                    .mapToInt(s -> s.getStock())
                    .sum();
        }
        return p.getStockQuantity();
    }

    private List<String> outOfStockVariants(Product p) {
        if (p.getColors() == null || p.getColors().isEmpty()) return List.of();
        return p.getColors().stream()
                .flatMap(c -> c.getSizes().stream()
                        .filter(s -> s.getStock() == 0)
                        .map(s -> c.getName() + " / " + s.getName()))
                .toList();
    }
}
