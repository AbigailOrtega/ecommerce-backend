package com.ecommerce.dto.response;

public record CategoryResponse(
    Long id,
    String name,
    String description,
    String imageUrl,
    String slug,
    boolean visible
) {
    public CategoryResponse(Long id, String name, String description, String imageUrl, String slug) {
        this(id, name, description, imageUrl, slug, true);
    }
}
