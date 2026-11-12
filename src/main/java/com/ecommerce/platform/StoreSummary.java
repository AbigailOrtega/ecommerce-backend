package com.ecommerce.platform;

import java.util.List;

public record StoreSummary(Long id, String slug, String name, String status, String baseUrl, List<String> domains) {}
