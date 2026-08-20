package com.rgapro1.ocaso.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;

/** Transitional local store. Keeps persistence behind a repository boundary. */
public final class LocalStore {
    private final SharedPreferences prefs;

    public LocalStore(Context context) {
        prefs = context.getSharedPreferences("rgapro_local", Context.MODE_PRIVATE);
    }

    public JSONArray readPolicies() {
        try { return new JSONArray(prefs.getString("policies", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    public void writePolicies(JSONArray policies) {
        prefs.edit().putString("policies", policies.toString()).apply();
    }
}
