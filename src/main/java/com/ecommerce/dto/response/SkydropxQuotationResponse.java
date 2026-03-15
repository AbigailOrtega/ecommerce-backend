package com.ecommerce.dto.response;

import java.util.List;

public record SkydropxQuotationResponse(String quotationId, List<SkydropxRateDto> rates) {}
