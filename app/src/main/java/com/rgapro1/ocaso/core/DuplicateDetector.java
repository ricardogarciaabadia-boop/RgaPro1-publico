package com.rgapro1.ocaso.core;

import org.json.JSONArray;
import org.json.JSONObject;

public final class DuplicateDetector {
    private DuplicateDetector() {}

    public static JSONObject find(JSONArray data, String identity, String phone, String email) {
        String id = ClientSearchEngine.normalize(identity);
        String ph = ClientSearchEngine.normalize(phone);
        String em = ClientSearchEngine.normalize(email);
        for (int i = 0; i < data.length(); i++) {
            JSONObject p = data.optJSONObject(i);
            if (p == null) continue;
            if (!id.isEmpty() && id.equals(ClientSearchEngine.normalize(p.optString("identityNumber", p.optString("holderDni"))))) return p;
            if (!ph.isEmpty() && ph.equals(ClientSearchEngine.normalize(p.optString("phone")))) return p;
            if (!em.isEmpty() && em.equals(ClientSearchEngine.normalize(p.optString("email")))) return p;
        }
        return null;
    }
}
