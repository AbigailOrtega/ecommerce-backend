package com.ecommerce.dto.request;

import java.util.List;

public record ProductColorRequest(
    Long id,
    String name,
    List<String> images,
    List<ProductSizeRequest> sizes
) {}
