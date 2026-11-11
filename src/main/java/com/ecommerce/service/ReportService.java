package com.ecommerce.service;

import com.ecommerce.dto.response.AccountingReportResponse;
import com.ecommerce.dto.response.InventoryItem;
import com.ecommerce.dto.response.ProductCostItem;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    /** Product rankings count sales: also leave out orders that were never paid. */
    private static final List<OrderStatus> NOT_SOLD = List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.PENDING);

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

        // sales are counted on the day the money came in: an order waiting for payment is not a sale
        List<Order> orders = orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(start, now);

        Map<String, List<Order>> byDate = orders.stream()
                .filter(o -> !EXCLUDED.contains(o.getStatus()))
                .collect(Collectors.groupingBy(o -> o.getPaidAt().toLocalDate().toString()));

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

    // ── Accounting ────────────────────────────────────────────────────────────

    static final int MAX_RANGE_DAYS = 400;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /**
     * Sales paid between {@code from} and {@code to} (inclusive days), refunds made in those days,
     * and what selling cost: commissions, labels and the garments themselves.
     */
    @Transactional(readOnly = true)
    public AccountingReportResponse getAccountingReport(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new BadRequestException("Indica las fechas del periodo");
        if (to.isBefore(from)) throw new BadRequestException("La fecha final es anterior a la inicial");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("El periodo puede ser de hasta " + MAX_RANGE_DAYS + " días");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay().minusNanos(1000);
        List<Order> paid = orderRepository.findByPaidAtBetweenOrderByPaidAtAsc(start, end);
        List<Order> refunded = orderRepository.findByRefundedAtBetweenOrderByRefundedAtAsc(start, end).stream()
                .filter(o -> o.getPaidAt() != null).toList();

        BigDecimal productSales = ZERO, discounts = ZERO, shipping = ZERO, collected = ZERO;
        BigDecimal fees = ZERO, labels = ZERO, cogs = ZERO;
        long missingFee = 0, missingLabel = 0, unitsMissingCost = 0, paidThenCancelled = 0;
        Map<String, BigDecimal[]> methods = new TreeMap<>();   // method → {orders, collected, fees}
        Map<String, long[]> dailyCount = new TreeMap<>();
        Map<String, BigDecimal> dailyRevenue = new TreeMap<>();
        List<AccountingReportResponse.LedgerRow> ledger = new java.util.ArrayList<>();

        for (Order o : paid) {
            BigDecimal items = itemsTotal(o), cost = goodsCost(o);
            productSales = productSales.add(items);
            discounts = discounts.add(nz(o.getDiscountAmount()));
            shipping = shipping.add(nz(o.getShippingCost()));
            collected = collected.add(nz(o.getTotalAmount()));
            fees = fees.add(nz(o.getPaymentFee()));
            labels = labels.add(nz(o.getShippingLabelCost()));
            cogs = cogs.add(cost);
            if (o.getPaymentFee() == null && chargesCommission(o)) missingFee++;
            if (o.getShippingLabelCost() == null && "NATIONAL".equalsIgnoreCase(o.getShippingType())
                    && o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.REFUNDED) missingLabel++;
            unitsMissingCost += o.getItems().stream().filter(i -> i.getUnitCost() == null).mapToLong(i -> i.getQuantity()).sum();
            if (o.getStatus() == OrderStatus.CANCELLED && o.getRefundedAt() == null) paidThenCancelled++;

            String method = method(o);
            BigDecimal[] m = methods.computeIfAbsent(method, k -> new BigDecimal[]{BigDecimal.ZERO, ZERO, ZERO});
            m[0] = m[0].add(BigDecimal.ONE);
            m[1] = m[1].add(nz(o.getTotalAmount()));
            m[2] = m[2].add(nz(o.getPaymentFee()));

            String day = o.getPaidAt().toLocalDate().toString();
            dailyCount.computeIfAbsent(day, k -> new long[1])[0]++;
            dailyRevenue.merge(day, nz(o.getTotalAmount()), BigDecimal::add);

            ledger.add(row("VENTA", o.getPaidAt(), o, items, nz(o.getDiscountAmount()), nz(o.getShippingCost()),
                    nz(o.getTotalAmount()), o.getPaymentFee(), o.getShippingLabelCost(), cost));
        }

        BigDecimal refunds = ZERO, returnedCost = ZERO;
        for (Order o : refunded) {
            BigDecimal cost = goodsCost(o);
            refunds = refunds.add(nz(o.getTotalAmount()));
            returnedCost = returnedCost.add(cost);
            ledger.add(row("REEMBOLSO", o.getRefundedAt(), o, itemsTotal(o).negate(), nz(o.getDiscountAmount()).negate(),
                    nz(o.getShippingCost()).negate(), nz(o.getTotalAmount()).negate(), null, null, cost.negate()));
        }
        ledger.sort(java.util.Comparator.comparing(AccountingReportResponse.LedgerRow::date));

        BigDecimal netSales = collected.subtract(refunds);
        BigDecimal costOfGoods = cogs.subtract(returnedCost);
        BigDecimal grossProfit = netSales.subtract(fees).subtract(labels).subtract(costOfGoods);

        var summary = new AccountingReportResponse.Summary(paid.size(), productSales, discounts, shipping, collected,
                refunded.size(), refunds, netSales, fees, missingFee, labels, missingLabel, costOfGoods,
                unitsMissingCost, grossProfit, paidThenCancelled);
        var byMethod = methods.entrySet().stream()
                .map(e -> new AccountingReportResponse.PaymentMethodTotal(e.getKey(), e.getValue()[0].longValue(),
                        e.getValue()[1], e.getValue()[2]))
                .toList();
        var daily = dailyCount.keySet().stream()
                .map(d -> new SalesDataPoint(d, dailyCount.get(d)[0], dailyRevenue.get(d)))
                .toList();
        return new AccountingReportResponse(from, to, summary, byMethod, daily, ledger);
    }

    /** Products with their price and cost, for the admin to fill in the costs. */
    @Transactional(readOnly = true)
    public List<ProductCostItem> getProductCosts() {
        return productRepository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                .map(p -> new ProductCostItem(p.getId(), p.getName(), p.getSku(), p.getPrice(), p.getCostPrice(), p.isActive()))
                .toList();
    }

    /**
     * Sets what a product cost the store. New sales record it; past sales that had no cost take it
     * too (the store starts filling costs after having sold), while sales that already have one
     * keep theirs. Returns how many past sales were filled.
     */
    @Transactional
    public int setProductCost(Long productId, BigDecimal cost) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        BigDecimal value = cost == null ? null : cost.setScale(2, java.math.RoundingMode.HALF_UP);
        product.setCostPrice(value);
        productRepository.save(product);
        return value == null ? 0 : orderItemRepository.fillMissingUnitCost(productId, value);
    }

    private static AccountingReportResponse.LedgerRow row(String movement, LocalDateTime date, Order o,
            BigDecimal items, BigDecimal discount, BigDecimal shipping, BigDecimal total,
            BigDecimal fee, BigDecimal label, BigDecimal cost) {
        return new AccountingReportResponse.LedgerRow(movement, date, o.getOrderNumber(), customerName(o),
                o.getUser() != null ? o.getUser().getEmail() : o.getGuestEmail(), o.getStatus().name(), method(o),
                o.getPaymentId(), o.getCouponCode(), o.getShippingType(), items, discount, shipping, total, fee, label, cost);
    }

    private static String customerName(Order o) {
        String first = o.getUser() != null ? o.getUser().getFirstName() : o.getGuestFirstName();
        String last = o.getUser() != null ? o.getUser().getLastName() : o.getGuestLastName();
        return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
    }

    private static String method(Order o) {
        return o.getPaymentMethod() == null || o.getPaymentMethod().isBlank() ? "otro" : o.getPaymentMethod().toLowerCase();
    }

    private static boolean chargesCommission(Order o) {
        String m = method(o);
        return m.equals("stripe") || m.equals("paypal");
    }

    private static BigDecimal itemsTotal(Order o) {
        return o.getItems().stream().map(i -> nz(i.getSubtotal())).reduce(ZERO, BigDecimal::add);
    }

    /** What the garments of the order cost the store (items without a cost count as 0). */
    private static BigDecimal goodsCost(Order o) {
        return o.getItems().stream()
                .filter(i -> i.getUnitCost() != null)
                .map(i -> i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    // ── Product sales ─────────────────────────────────────────────────────────

    public List<ProductSalesItem> getTopSellingProducts(int limit) {
        return buildSalesItems(orderItemRepository.findProductSalesRanked(NOT_SOLD), limit, false);
    }

    public List<ProductSalesItem> getLeastSellingProducts(int limit) {
        List<Object[]> ranked = orderItemRepository.findProductSalesRanked(NOT_SOLD);
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
