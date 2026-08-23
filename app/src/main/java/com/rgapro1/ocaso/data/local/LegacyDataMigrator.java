package com.rgapro1.ocaso.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Idempotent bridge from the legacy SharedPreferences JSON store to Room.
 * The legacy store remains intact so an interrupted migration cannot lose data.
 */
public final class LegacyDataMigrator {
    private static final String PREFS = "rgapro_local";
    private static final String KEY = "policies";
    private static final String DONE = "room_migration_v1_done";

    private LegacyDataMigrator() {}

    public static void migrate(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray source;
        try {
            source = new JSONArray(prefs.getString(KEY, "[]"));
        } catch (Exception ignored) {
            return;
        }

        ClientRecordRepository repository = new ClientRecordRepository(app);
        for (int i = 0; i < source.length(); i++) {
            JSONObject record = source.optJSONObject(i);
            if (record == null) continue;
            String recordId = stableId(record, i);
            repository.upsertJson(recordId, record);
        }
        prefs.edit().putBoolean(DONE, true).apply();
    }

    public static boolean isComplete(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(DONE, false);
    }

    static String stableId(JSONObject record, int index) {
        String[] keys = {"recordId", "id", "identityNumber", "holderDni", "number", "holder"};
        for (String key : keys) {
            String value = record.optString(key, "").trim();
            if (!value.isEmpty()) return "legacy:" + key + ":" + value;
        }
        return "legacy:index:" + index;
    }
}
