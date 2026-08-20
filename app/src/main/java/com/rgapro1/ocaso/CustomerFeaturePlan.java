package com.rgapro1.ocaso;

/**
 * Shared model/constants for the customer-360 workflow.
 * The activity can use these constants when presenting current/history products,
 * document groups, duplicate checks and selective sharing.
 */
public final class CustomerFeaturePlan {
    private CustomerFeaturePlan() {}

    public static final String[] SEARCH_FIELDS = {
            "holder", "name", "surname", "identityNumber", "cif",
            "phone", "email", "address", "postalCode", "city",
            "number", "company", "type"
    };

    public static final String[] PRODUCT_STATES = {
            "VIGENTE", "PROXIMO", "VENCIDO", "CANCELADO", "HISTORICO"
    };

    public static final String[] DOCUMENT_GROUPS = {
            "DNI_ANVERSO", "DNI_REVERSO", "POLIZA", "RECIBO", "CONTRATO", "OTRO"
    };

    public static final int[] EXPIRY_ALERT_DAYS = {60, 45, 30, 15};

    public static boolean sameIdentity(String typeA, String idA, String typeB, String idB) {
        if (idA == null || idB == null) return false;
        String a = normalize(idA);
        String b = normalize(idB);
        if (!a.equals(b)) return false;
        return typeA == null || typeB == null || typeA.equalsIgnoreCase(typeB);
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(java.util.Locale.ROOT)
                .replace(" ", "").replace("-", "");
    }
}
