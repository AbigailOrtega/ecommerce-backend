package com.ecommerce.dto.request;

import jakarta.validation.constraints.Pattern;

public record StoreInfoRequest(
    String name,
    String aboutText,
    String mission,
    String vision,
    String phone,
    String email,
    String privacyPolicy,
    String logoUrl,
    // a palette name, or "custom:#rrggbb"; it ends up in the page's CSS, so nothing else is accepted
    @Pattern(regexp = "^([A-Za-z0-9]{1,30}|custom:#[0-9A-Fa-f]{6})$",
             message = "Color no válido: elige una paleta o un color en formato #RRGGBB") String themeKey,
    String fontKey,
    String instagramUrl,
    String facebookUrl,
    String whatsappNumber,
    Boolean showNameInNavbar
) {}
