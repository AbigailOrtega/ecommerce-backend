package com.ecommerce.dto.response;

import java.util.List;

public record ProductColorResponse(
    Long id,
    String name,
    List<String> images,
    List<ProductSizeResponse> sizes
) {}
