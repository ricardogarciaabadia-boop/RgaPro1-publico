package com.rgapro1.ocaso.domain.duplicate;

import java.util.Locale;

/** Conservative duplicate checks for identity/contact values. */
public final class DuplicateClientGuard {
    private DuplicateClientGuard() {}

    public static boolean sameIdentity(String first, String second) {
        return normalize(first).equals(normalize(second)) && !normalize(first).isEmpty();
    }

    public static boolean sameContact(String first, String second) {
        String a = normalize(first).replaceAll("\\D", "");
        String b = normalize(second).replaceAll("\\D", "");
        return !a.isEmpty() && a.equals(b);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }
}
