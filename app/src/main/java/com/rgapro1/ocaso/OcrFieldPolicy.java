package com.rgapro1.ocaso;

/**
 * Defines the minimum identity data that OCR must recover from a DNI/NIE.
 * Contact data is intentionally optional and can be completed later by the user.
 */
public final class OcrFieldPolicy {
    private OcrFieldPolicy() {}

    public static final String[] REQUIRED_DNI_FIELDS = {
            "name",
            "surname",
            "identityNumber",
            "birthDate"
    };

    public static final String[] OPTIONAL_CONTACT_FIELDS = {
            "phone",
            "address",
            "email"
    };

    public static boolean hasCoreDniData(DniOcrParser.Result result) {
        return result != null
                && !result.name.trim().isEmpty()
                && !result.surname.trim().isEmpty()
                && !result.dni.trim().isEmpty()
                && !result.birthDate.trim().isEmpty();
    }
}
