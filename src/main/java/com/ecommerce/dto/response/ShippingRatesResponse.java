package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ShippingRatesResponse(
    boolean skydropxAvailable,
    double distanceKm,
    BigDecimal flatPrice,
    String quotationId,
    List<SkydropxRateDto> rates
) {}
