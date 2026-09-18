package com.claseya.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Generates URL-friendly slugs (lowercase, ASCII-folded, dashes) and normalized
 * names. Spanish accents (á, é, í, ó, ú, ñ) are folded to their ASCII base.
 */
public final class SlugUtils {

    private SlugUtils() {
    }

    public static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized;
    }

    public static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the given base slug, or {@code base-2}, {@code base-3}, ... until
     * {@code taken} reports the candidate is available.
     */
    public static String uniqueSlug(String base, Predicate<String> taken) {
        String candidate = base;
        int suffix = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
