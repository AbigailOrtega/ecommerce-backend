package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record SalesDataPoint(String date, long orderCount, BigDecimal revenue) {}
