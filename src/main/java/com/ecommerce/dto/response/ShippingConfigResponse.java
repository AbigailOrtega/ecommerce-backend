package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record ShippingConfigResponse(
    boolean nationalEnabled,
    boolean pickupEnabled,
    BigDecimal nationalBasePrice,
    BigDecimal nationalPricePerKm,
    String originAddress,
    BigDecimal pickupCost,
    String whatsappNumber
) {}
