package com.rgapro1.ocaso.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Centralized, accent-insensitive client/policy search. */
public final class ClientSearchEngine {
    private ClientSearchEngine() {}

    public static List<JSONObject> filter(JSONArray data, String query) {
        List<JSONObject> result = new ArrayList<>();
        String q = normalize(query);
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item != null && matches(item, q)) result.add(item);
        }
        return result;
    }

    public static boolean matches(JSONObject item, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return true;
        String[] fields = {
                "holder", "name", "surname", "birthDate", "holderDni",
                "identityNumber", "identityType", "cif", "phone", "email",
                "address", "city", "population", "postalCode", "birthPlace",
                "nationality", "sex", "parents", "supportNumber", "issueDate",
                "expiry", "validityDate", "type", "number", "company", "ocrText",
                "members", "documentPhotos", "documentPaths", "notes"
        };
        for (String field : fields) {
            if (normalize(item.optString(field, "")).contains(q)) return true;
        }
        return false;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9@._+\\- /]", "").trim();
    }
}
