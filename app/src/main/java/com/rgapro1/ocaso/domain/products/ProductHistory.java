package com.rgapro1.ocaso.domain.products;

/** Product lifecycle states used to preserve current and historical products. */
public final class ProductHistory {
    private ProductHistory() {}

    public static boolean isHistorical(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase();
        return s.equals("cancelada") || s.equals("anulada") || s.equals("histórica") || s.equals("historica");
    }

    public static boolean isActive(String status) {
        return status != null && status.trim().equalsIgnoreCase("activa");
    }
}
