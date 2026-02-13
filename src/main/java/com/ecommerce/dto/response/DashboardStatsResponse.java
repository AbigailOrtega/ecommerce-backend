package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {
    private long totalOrders;
    private long totalProducts;
    private long totalUsers;
    private BigDecimal totalRevenue;
    private List<OrderResponse> recentOrders;
    private Map<String, Long> ordersByStatus;
}
