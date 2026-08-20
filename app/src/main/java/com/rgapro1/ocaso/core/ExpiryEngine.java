package com.rgapro1.ocaso.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ExpiryEngine {
    private static final int[] WINDOWS = {15, 30, 45, 60};
    private ExpiryEngine() {}

    public static List<Alert> find(JSONArray data, Date now) {
        List<Alert> result = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        for (int i = 0; i < data.length(); i++) {
            JSONObject p = data.optJSONObject(i);
            if (p == null) continue;
            String value = p.optString("expiry", p.optString("validityDate", ""));
            try {
                long days = (f.parse(value).getTime() - now.getTime()) / 86400000L;
                if (days >= 0 && days <= 60) {
                    int bucket = 60;
                    for (int w : WINDOWS) if (days <= w) { bucket = w; break; }
                    result.add(new Alert(p, days, bucket));
                }
            } catch (ParseException ignored) {}
        }
        return result;
    }

    public static final class Alert {
        public final JSONObject item;
        public final long days;
        public final int bucket;
        Alert(JSONObject item, long days, int bucket) { this.item = item; this.days = days; this.bucket = bucket; }
    }
}
