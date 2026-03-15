package com.ecommerce.dto.response;

public record SkydropxShipmentResponse(
    String shipmentId,
    String trackingNumber,
    String carrierName,
    String labelUrl,
    String status
) {}
