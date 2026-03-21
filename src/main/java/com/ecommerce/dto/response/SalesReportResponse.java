package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SalesReportResponse(
        String period,
        List<SalesDataPoint> data,
        BigDecimal totalRevenue,
        long totalOrders
) {}
