package com.rgapro1.ocaso.domain.search;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Centralized client search. Matching is partial, case-insensitive and accent-insensitive.
 * It is deliberately independent from Android UI so it can be unit-tested and reused by
 * XML/Compose screens and later by a Room repository.
 */
public final class ClientSearchEngine {
    private static final String[] FIELDS = {
        "holder", "name", "surname", "birthDate", "holderDni", "identityNumber",
        "identityType", "cif", "phone", "email", "address", "birthPlace", "nationality",
        "sex", "parents", "supportNumber", "issueDate", "expiry", "validityDate",
        "type", "number", "ocrText", "members", "documentPhotos", "company", "population",
        "postalCode"
    };

    private ClientSearchEngine() {}

    public static List<JSONObject> filter(JSONArray source, String query) {
        List<JSONObject> result = new ArrayList<>();
        String q = normalize(query);
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && matches(item, q)) result.add(item);
        }
        return result;
    }

    public static boolean matches(JSONObject item, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return true;
        for (String field : FIELDS) {
            if (normalize(item.optString(field, "")).contains(q)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
