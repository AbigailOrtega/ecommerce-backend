package com.ecommerce.dto.response;

public record UserResponse(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String role
) {}
