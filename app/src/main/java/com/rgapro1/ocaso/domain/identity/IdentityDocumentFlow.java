package com.rgapro1.ocaso.domain.identity;

import java.util.Locale;

/** Pure decision helpers for identity-document capture and client matching. */
public final class IdentityDocumentFlow {
    private IdentityDocumentFlow() {}

    public static boolean isDni(String type) {
        return type != null && "DNI".equalsIgnoreCase(type.trim());
    }

    public static boolean isNie(String type) {
        return type != null && "NIE".equalsIgnoreCase(type.trim());
    }

    public static boolean isCif(String type) {
        return type != null && "CIF".equalsIgnoreCase(type.trim());
    }

    public static int requiredSides(String type) {
        return isDni(type) ? 2 : 1;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean sameIdentity(String a, String b) {
        String left = normalize(a).replace(" ", "");
        String right = normalize(b).replace(" ", "");
        return !left.isEmpty() && left.equals(right);
    }
}
