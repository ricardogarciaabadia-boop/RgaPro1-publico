package com.rgapro1.ocaso.search;

import org.json.JSONObject;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Centralized, accent-insensitive client/product search.
 * Matches partial values across identity, contact, address, policy and OCR fields.
 */
public final class ClientSearchEngine {
    private static final String[] FIELDS = {
            "holder", "name", "surname", "birthDate", "holderDni",
            "identityNumber", "identityType", "cif", "phone", "email",
            "address", "population", "postalCode", "birthPlace", "nationality",
            "sex", "parents", "supportNumber", "issueDate", "expiry",
            "validityDate", "type", "number", "company", "ocrText",
            "members", "documentPhotos"
    };

    private ClientSearchEngine() { }

    public static boolean matches(JSONObject item, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return true;
        for (String field : FIELDS) {
            if (normalize(item.optString(field, "")).contains(q)) return true;
        }
        return false;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }
}
