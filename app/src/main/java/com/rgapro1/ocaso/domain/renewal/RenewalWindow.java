package com.rgapro1.ocaso.domain.renewal;

/** Renewal buckets used by the dashboard without coupling it to Android UI. */
public final class RenewalWindow {
    private RenewalWindow() {}

    public static boolean isWithinDays(long daysRemaining, int days) {
        return daysRemaining >= 0 && daysRemaining <= days;
    }

    public static String bucket(long daysRemaining) {
        if (daysRemaining < 0) return "expired";
        if (daysRemaining <= 15) return "15";
        if (daysRemaining <= 30) return "30";
        if (daysRemaining <= 45) return "45";
        if (daysRemaining <= 60) return "60";
        return "later";
    }
}
