package com.ecommerce.dto.response;

public record PickupLocationResponse(
    Long id,
    String name,
    String address,
    String city,
    String state,
    boolean active
) {}
