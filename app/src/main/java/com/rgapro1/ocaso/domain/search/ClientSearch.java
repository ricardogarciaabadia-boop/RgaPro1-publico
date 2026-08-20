package com.rgapro1.ocaso.domain.search;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizes free-text queries so client search is accent/case insensitive. */
public final class ClientSearch {
    private ClientSearch() {}

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean contains(String field, String query) {
        String q = normalize(query);
        return q.isEmpty() || normalize(field).contains(q);
    }
}
