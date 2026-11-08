package com.ecommerce.util;

import java.text.Normalizer;
import java.util.function.Predicate;

/** URL slugs for products and categories. */
public final class Slugs {

    private Slugs() {}

    /** "Cojín Azul" → "cojin-azul". Accents are dropped instead of turning into dashes. */
    public static String slugify(String text) {
        String slug = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "item" : slug;
    }

    /** The slug for {@code text}, or "-2", "-3"… appended until {@code taken} says it is free. */
    public static String unique(String text, Predicate<String> taken) {
        String base = slugify(text);
        String slug = base;
        for (int n = 2; taken.test(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }
}
