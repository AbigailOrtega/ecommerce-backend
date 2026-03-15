package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record ShippingCalculateResponse(double distanceKm, BigDecimal price) {}
