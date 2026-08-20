package com.rgapro1.ocaso.core;

import org.json.JSONObject;

/** Builds a minimal export containing only fields explicitly selected by the user. */
public final class ShareProfile {
    private ShareProfile() {}

    public static JSONObject build(JSONObject source, boolean identity, boolean contact, boolean address, boolean policies) {
        JSONObject out = new JSONObject();
        try {
            if (identity) {
                copy(source, out, "holder"); copy(source, out, "name"); copy(source, out, "surname");
                copy(source, out, "identityType"); copy(source, out, "identityNumber"); copy(source, out, "birthDate");
            }
            if (contact) { copy(source, out, "phone"); copy(source, out, "email"); }
            if (address) { copy(source, out, "address"); copy(source, out, "city"); copy(source, out, "population"); copy(source, out, "postalCode"); }
            if (policies) { copy(source, out, "type"); copy(source, out, "number"); copy(source, out, "company"); copy(source, out, "expiry"); }
        } catch (Exception ignored) {}
        return out;
    }

    private static void copy(JSONObject from, JSONObject to, String key) throws Exception {
        if (from.has(key) && !from.isNull(key)) to.put(key, from.get(key));
    }
}
