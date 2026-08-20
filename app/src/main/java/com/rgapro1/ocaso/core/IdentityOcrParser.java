package com.rgapro1.ocaso.core;

import com.rgapro1.ocaso.DniOcrParser;
import java.util.Locale;

/** Identity parser facade. Keeps one DNI/NIE parser as the source of truth. */
public final class IdentityOcrParser {
    private IdentityOcrParser() {}

    public static Result parse(String rawText) {
        DniOcrParser.Result source = DniOcrParser.parse(rawText);
        Result r = new Result();
        r.number = source.dni == null ? "" : source.dni.toUpperCase(Locale.ROOT);
        if (!r.number.isEmpty()) {
            r.type = r.number.matches("[XYZ][0-9]{7}[A-Z]") ? "NIE" : "DNI";
        }
        r.name = safe(source.name);
        r.surname = safe(source.surname);
        r.birthDate = safe(source.birthDate);
        r.nationality = safe(source.nationality);
        r.sex = safe(source.sex);
        r.expiry = safe(source.validityDate);
        r.mrz = safe(source.mrz);
        r.confidence = source.confidence;
        return r;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static final class Result {
        public String type = "";
        public String number = "";
        public String name = "";
        public String surname = "";
        public String birthDate = "";
        public String nationality = "";
        public String sex = "";
        public String expiry = "";
        public String firstDate = "";
        public String mrz = "";
        public int confidence;
    }
}
