package com.ecommerce.dto.request;

import java.math.BigDecimal;

public record ShippingConfigRequest(
    Boolean nationalEnabled,
    Boolean pickupEnabled,
    BigDecimal nationalBasePrice,
    BigDecimal nationalPricePerKm,
    String originAddress,
    String googleMapsApiKey,
    BigDecimal pickupCost,
    // Skydropx
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
    Integer skydropxDefaultHeight,
    Boolean freeShippingEnabled,
    BigDecimal freeShippingMinAmount
) {}
