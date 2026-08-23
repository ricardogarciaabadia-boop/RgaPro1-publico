package com.rgapro1.ocaso;

/** Single source of truth for the fields OCR is expected to surface first. */
public final class OcrFieldPolicy {
    private OcrFieldPolicy() {}
    public static final String[] REQUIRED_DNI_FIELDS={"name","surname","identityNumber","birthDate"};
    public static final String[] OPTIONAL_CONTACT_FIELDS={"phone","address","email"};
    public static final String[] REQUIRED_POLICY_FIELDS={"type","number","holder"};
    public static boolean hasCoreDniData(DniOcrParser.Result r){return r!=null&&!r.name.trim().isEmpty()&&!r.surname.trim().isEmpty()&&!r.dni.trim().isEmpty()&&!r.birthDate.trim().isEmpty();}
}
