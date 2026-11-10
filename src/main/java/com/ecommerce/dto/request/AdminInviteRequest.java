package com.ecommerce.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Someone to make admin. Names are only used when the email has no account yet. */
public record AdminInviteRequest(
    @Email @NotBlank String email,
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName
) {}
