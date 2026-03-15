package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PromoBannerRequest(
    @NotBlank String imageUrl,
    String linkUrl
) {}
