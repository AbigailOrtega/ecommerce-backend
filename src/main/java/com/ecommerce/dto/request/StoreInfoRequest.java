package com.ecommerce.dto.request;

public record StoreInfoRequest(
    String name,
    String aboutText,
    String mission,
    String vision,
    String phone,
    String email,
    String privacyPolicy,
    String logoUrl,
    String themeKey,
    String fontKey,
    String instagramUrl,
    String facebookUrl,
    String whatsappNumber
) {}
