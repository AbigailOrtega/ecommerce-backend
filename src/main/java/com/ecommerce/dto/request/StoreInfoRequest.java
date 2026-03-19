package com.ecommerce.dto.request;

public record StoreInfoRequest(
    String name,
    String aboutText,
    String mission,
    String vision,
    String phone,
    String logoUrl,
    String themeKey,
    String instagramUrl,
    String facebookUrl
) {}
