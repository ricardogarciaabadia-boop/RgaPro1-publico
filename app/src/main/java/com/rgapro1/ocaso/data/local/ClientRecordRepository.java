package com.rgapro1.ocaso.data.local;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Transitional repository used to move JSON records into Room without changing
 * the current UI contract in one step.
 */
public final class ClientRecordRepository {
    private final ClientRecordDao dao;

    public ClientRecordRepository(Context context) {
        dao = RgaDatabase.getInstance(context).clientRecordDao();
    }

    public void upsertJson(String recordId, JSONObject json) {
        if (recordId == null || recordId.trim().isEmpty() || json == null) return;
        ClientRecordEntity entity = new ClientRecordEntity();
        entity.recordId = recordId;
        entity.identityNumber = json.optString("identityNumber", json.optString("holderDni", ""));
        entity.holder = json.optString("holder", "");
        entity.email = json.optString("email", "");
        entity.phone = json.optString("phone", "");
        entity.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
        entity.rawJson = json.toString();
        dao.upsert(entity);
    }

    public List<JSONObject> loadJsonRecords() {
        List<JSONObject> result = new ArrayList<>();
        for (ClientRecordEntity entity : dao.getAll()) {
            try {
                result.add(new JSONObject(entity.rawJson));
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
