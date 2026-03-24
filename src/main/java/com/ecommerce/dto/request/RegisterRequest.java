package com.ecommerce.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @Email @NotBlank String email,
    @Size(min = 8, message = "Password must be at least 8 characters") @NotBlank String password,
    String phone,
    boolean marketingOptIn
) {}
