package com.ecommerce.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Amount to pre-authorize with Stripe or PayPal. The order total is re-checked server side when the order is created. */
public record PaymentAmountRequest(
    @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
    @Pattern(regexp = "^[A-Za-z]{3}$") String currency
) {
    public String currencyOrDefault() {
        return currency == null ? "mxn" : currency;
    }
}
