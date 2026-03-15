package com.ecommerce.dto.response;

import java.time.LocalDateTime;

public record PromoBannerResponse(
    Long id,
    String imageUrl,
    String linkUrl,
    boolean active,
    LocalDateTime createdAt
) {}
