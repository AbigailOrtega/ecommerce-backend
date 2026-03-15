package com.ecommerce.dto.request;

import jakarta.validation.constraints.*;

public record ReviewRequest(
        @Min(1) @Max(5) int rating,
        @NotBlank String title,
        @NotBlank String comment
) {}
