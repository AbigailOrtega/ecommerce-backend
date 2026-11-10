package com.ecommerce.dto.response;

import java.util.List;

/** What a Mexican postal code tells the checkout: where it is and which colonias it covers. */
public record PostalCodeResponse(
    String code,
    String state,
    String municipality,
    /** The city when the catalog names one, else the municipality (most rural codes have none). */
    String city,
    List<Settlement> settlements
) {
    public record Settlement(String name, String type) {}
}
