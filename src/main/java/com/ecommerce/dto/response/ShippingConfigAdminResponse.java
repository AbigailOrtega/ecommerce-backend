package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record ShippingConfigAdminResponse(
    boolean nationalEnabled,
    boolean pickupEnabled,
    BigDecimal nationalBasePrice,
    BigDecimal nationalPricePerKm,
    String originAddress,
    boolean hasApiKey,
    BigDecimal pickupCost,
    // Skydropx
    boolean hasSkydropxCredentials,
    String skydropxOriginStreet,
    String skydropxOriginPostalCode,
    String skydropxOriginCity,
    String skydropxOriginState,
    String skydropxOriginCountry,
    String skydropxSenderName,
    String skydropxSenderEmail,
    String skydropxSenderPhone,
    Double skydropxDefaultWeight,
    Integer skydropxDefaultLength,
    Integer skydropxDefaultWidth,
    Integer skydropxDefaultHeight
) {}
