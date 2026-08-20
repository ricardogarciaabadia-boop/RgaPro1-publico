package com.rgapro1.ocaso;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExpiryNotificationReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "rgapro_expiry";

    @Override public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            ExpiryAlarmScheduler.scheduleAll(context);
            return;
        }
        if (!"com.rgapro1.ocaso.EXPIRY".equals(intent.getAction())) return;

        String user = intent.getStringExtra("user");
        String policyId = intent.getStringExtra("policy_id");
        int days = intent.getIntExtra("days", -1);
        if (user == null || policyId == null || days < 0) return;

        JSONObject policy = findPolicy(context, user, policyId);
        if (policy == null) return;
        showNotification(context, user, policy, days);
        ExpiryAlarmScheduler.schedulePolicy(context, user, policy);
    }

    private JSONObject findPolicy(Context context, String user, String id) {
        try {
            JSONObject users = new JSONObject(context.getSharedPreferences("rgapro_local", Context.MODE_PRIVATE)
                    .getString("users_json", "{}"));
            JSONObject account = users.optJSONObject(user);
            JSONArray policies = account == null ? null : account.optJSONArray("policies");
            if (policies == null) return null;
            for (int i = 0; i < policies.length(); i++) {
                JSONObject p = policies.optJSONObject(i);
                if (p != null && id.equals(p.optString("id", user + "_" + p.optString("number")))) return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void showNotification(Context context, String user, JSONObject p, int days) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Vencimientos de pólizas", NotificationManager.IMPORTANCE_DEFAULT));
        }
        String noticeKey = "notice_" + user + "_" + p.optString("id", p.optString("number")) + "_" + days + "_" +
                new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (context.getSharedPreferences("rgapro_local", Context.MODE_PRIVATE).getBoolean(noticeKey, false)) return;

        int id = Math.abs(noticeKey.hashCode());
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String when = days + " días";
        String text = p.optString("holder") + " · " + p.optString("number") + " · vence en " + when;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("RgaPro · Vencimiento")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText("La póliza de " + p.optString("holder") +
                        " (" + p.optString("type") + ") vence en " + when + ". Usuario: " + user + "."))
                .setAutoCancel(true).setContentIntent(pi);
        nm.notify(id, builder.build());
        context.getSharedPreferences("rgapro_local", Context.MODE_PRIVATE).edit().putBoolean(noticeKey, true).apply();
    }
}
