package com.ecommerce.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * A new store: its short name (platform subdomain), display name, the domains that serve it, the
 * address used in its e-mails, and who administers it (invited by e-mail to set a password).
 */
public record CreateStoreRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?",
                message = "slug: minúsculas, números y guiones") String slug,
        @NotBlank String name,
        List<String> domains,
        String baseUrl,
        @NotBlank @Email String adminEmail,
        String adminFirstName,
        String adminLastName
) {}
