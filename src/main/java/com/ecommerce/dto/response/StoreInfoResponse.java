package com.ecommerce.dto.response;

import com.ecommerce.entity.StoreImage;

import java.util.List;

public record StoreInfoResponse(
    String name,
    String aboutText,
    String mission,
    String vision,
    String phone,
    String logoUrl,
    String themeKey,
    List<StoreImage> images,
    String instagramUrl,
    String facebookUrl
) {}
