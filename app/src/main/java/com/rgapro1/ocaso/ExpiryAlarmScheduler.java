package com.rgapro1.ocaso;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
            // La interfaz actual guarda la cartera en rgapro_clients, fuera del
            // almacén antiguo users_json. También programamos esas pólizas.
            SharedPreferences localPrefs=context.getSharedPreferences("rgapro_local",Context.MODE_PRIVATE);
            JSONArray clients = new JSONArray(SecureLocalDataStore.read(context,localPrefs,"rgapro_clients","[]"));
            for (int i = 0; i < clients.length(); i++) {
                JSONObject client = clients.optJSONObject(i);
                JSONArray policies = client == null ? null : client.optJSONArray("policies");
                if (policies == null) continue;
                for (int j = 0; j < policies.length(); j++) {
                    JSONObject policy = policies.optJSONObject(j);
                    if (policy != null && !"PROPUESTA".equalsIgnoreCase(policy.optString("documentStatus", "")))
                        schedulePolicy(context, "local", policy);
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
        for (String pattern : new String[]{"dd/MM/yyyy", "yyyy-MM-dd"}) try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
            format.setLenient(false);
            return format.parse(value).getTime();
        } catch (Exception ignored) {}
        return -1;
    }
}
