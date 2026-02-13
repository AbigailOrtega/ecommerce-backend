package com.ecommerce.dto.response;

public record CategoryResponse(
    Long id,
    String name,
    String description,
    String imageUrl,
    String slug
) {}
