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
import java.util.regex.Pattern;
import java.util.Locale;

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

    /** Pages that are not part of a shopper's visit: the admin, signing in, and the checkout steps. */
    private static final List<String> UNTRACKED_PREFIXES = List.of(
            "/admin", "/login", "/register", "/forgot-password", "/reset-password", "/checkout");

    /** Crawlers, link previews and headless browsers that run the site's JavaScript. */
    private static final Pattern BOT_USER_AGENT = Pattern.compile(
            "bot|crawl|spider|slurp|headless|lighthouse|pagespeed|preview|facebookexternalhit|whatsapp|"
                    + "telegram|discord|curl|wget|python|java/|okhttp|axios|node-fetch|go-http",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /**
     * Records one page view, unless it is not a shopper's: an admin, a bot, or a page outside the
     * storefront. The path is stored without its query string so /products/x?utm_source=... and
     * /products/x count as the same page. Returns whether it was recorded.
     */
    public boolean recordPageView(String rawPath, String sessionId, String source, String userAgent, boolean admin) {
        String path = normalizePath(rawPath);
        if (path == null || admin || isBot(userAgent) || !isTracked(path)) return false;
        pageViewRepository.save(PageView.builder()
                .path(path)
                .sessionId(sessionId != null && SESSION_ID.matcher(sessionId).matches() ? sessionId : null)
                .source(normalizeSource(source))
                .timestamp(LocalDateTime.now())
                .build());
        return true;
    }

    static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String path = raw.trim().split("[?#]", 2)[0];
        if (!path.startsWith("/")) path = "/" + path;
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.length() > 255 ? path.substring(0, 255) : path;
    }

    static String normalizeSource(String raw) {
        if (raw == null) return null;
        String source = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "").replaceAll("[^a-z0-9._-]", "");
        if (source.isEmpty()) return null;
        return source.length() > 50 ? source.substring(0, 50) : source;
    }

    static boolean isTracked(String path) {
        return UNTRACKED_PREFIXES.stream().noneMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    static boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT_USER_AGENT.matcher(userAgent).find();
    }

    /** Page views and visits (distinct sessions) today, this week, month, year and in total. */
    public Map<String, Object> getPageViewSummary() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, LocalDateTime> since = new LinkedHashMap<>();
        since.put("today",     now.toLocalDate().atStartOfDay());
        since.put("thisWeek",  now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay());
        since.put("thisMonth", now.withDayOfMonth(1).toLocalDate().atStartOfDay());
        since.put("thisYear",  now.withDayOfYear(1).toLocalDate().atStartOfDay());

        Map<String, Object> summary = new LinkedHashMap<>();
        since.forEach((key, from) -> {
            summary.put(key, pageViewRepository.countByTimestampBetween(from, now));
            summary.put(key + "Visits", pageViewRepository.countVisitsBetween(from, now));
        });
        summary.put("total", pageViewRepository.count());
        summary.put("totalVisits", pageViewRepository.countVisits());
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
        return rows.stream().map(AnalyticsService::viewsAndVisits).toList();
    }

    public List<Map<String, Object>> getPageViewsByPage(LocalDateTime from, LocalDateTime to) {
        return pageViewRepository.countByPage(from, to).stream().map(AnalyticsService::viewsAndVisits).toList();
    }

    /** Visits by where they came from; an empty label means direct (typed URL, bookmark, app). */
    public List<Map<String, Object>> getVisitsBySource(LocalDateTime from, LocalDateTime to) {
        return pageViewRepository.countVisitsBySource(from, to).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", r[0] == null ? "" : r[0].toString());
            m.put("visits", ((Number) r[1]).longValue());
            return m;
        }).toList();
    }

    /** label, count (page views, the name the admin page has always read) and visits. */
    private static Map<String, Object> viewsAndVisits(Object[] row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", row[0].toString());
        m.put("count", ((Number) row[1]).longValue());
        m.put("visits", row.length > 2 && row[2] != null ? ((Number) row[2]).longValue() : 0L);
        return m;
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
