package com.rgapro1.ocaso;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/** Schedules the four requested expiry warnings independently for every user's private cartera. */
public final class ExpiryAlarmScheduler {
    private static final int[] DAYS = {60,45,30,15};
    private ExpiryAlarmScheduler() {}

    public static void scheduleAll(Context context) {
        try {
            JSONObject users = new JSONObject(context.getSharedPreferences("rgapro_local", Context.MODE_PRIVATE)
                    .getString("users_json", "{}"));
            Iterator<String> names = users.keys();
            while (names.hasNext()) {
                String user = names.next();
                JSONObject account = users.optJSONObject(user);
                if (account == null) continue;
                JSONArray policies = account.optJSONArray("policies");
                if (policies == null) continue;
                for (int i = 0; i < policies.length(); i++) {
                    JSONObject policy = policies.optJSONObject(i);
                    if (policy == null) continue;
                    schedulePolicy(context, user, policy);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void schedulePolicy(Context context, String user, JSONObject policy) {
        String expiry = policy.optString("expiry", "");
        long expiryMillis = parseDate(expiry);
        if (expiryMillis <= 0) return;
        String id = policy.optString("id", user + "_" + policy.optString("number"));
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        for (int days : DAYS) {
            long trigger = expiryMillis - days * 24L * 60L * 60L * 1000L;
            if (trigger <= System.currentTimeMillis()) continue;
            Intent intent = new Intent(context, ExpiryNotificationReceiver.class)
                    .setAction("com.rgapro1.ocaso.EXPIRY")
                    .putExtra("user", user)
                    .putExtra("policy_id", id)
                    .putExtra("days", days);
            int requestCode = Math.abs((user + "|" + id + "|" + days).hashCode());
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 23) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        }
    }

    private static long parseDate(String value) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value).getTime();
        } catch (Exception e) {
            return -1;
        }
    }
}
