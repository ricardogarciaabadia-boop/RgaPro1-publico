package com.rgapro1.ocaso.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative extractor for common home-policy fields from OCR text. */
public final class HomePolicyParser {
    private static final Pattern POLICY = Pattern.compile("(?:P[ÓO]LIZA|POLIZA|N[ÚU]MERO DE P[ÓO]LIZA|N[º°])[^A-Z0-9]{0,8}([A-Z0-9][A-Z0-9./_-]{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CP = Pattern.compile("\\b([0-5]\\d{4})\\b");
    private static final Pattern MONEY = Pattern.compile("(?:TOTAL|RECIBO|PRIMA)[^0-9]{0,12}([0-9]{1,8}(?:[.,][0-9]{1,2})?)\\s*€?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DNI = Pattern.compile("\\b([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b", Pattern.CASE_INSENSITIVE);
    private HomePolicyParser() {}

    public static Result parse(String text) {
        String t = text == null ? "" : text;
        Result r = new Result();
        Matcher m = POLICY.matcher(t); if (m.find()) r.policyNumber = m.group(1);
        m = CP.matcher(t); if (m.find()) r.postalCode = m.group(1);
        m = MONEY.matcher(t); if (m.find()) r.totalReceipt = m.group(1).replace(',', '.');
        m = DNI.matcher(t); if (m.find()) r.insuredId = m.group(1).toUpperCase();
        r.holder = labeled(t, "TOMADOR", "ASEGURADO", "CLIENTE");
        r.address = labeled(t, "DIRECCIÓN", "DOMICILIO", "RIESGO");
        r.phone = labeled(t, "TELÉFONO", "TELEFONO", "MÓVIL", "MOVIL");
        r.email = labeled(t, "EMAIL", "E-MAIL", "CORREO");
        r.city = labeled(t, "POBLACIÓN", "POBLACION", "LOCALIDAD");
        r.effectiveDate = labeled(t, "FECHA DE EFECTO", "EFECTO");
        r.expiryDate = labeled(t, "FECHA DE VENCIMIENTO", "VENCIMIENTO", "CADUCIDAD");
        r.propertyType = labeled(t, "TIPO DE VIVIENDA");
        r.surface = labeled(t, "SUPERFICIE");
        r.constructionYear = labeled(t, "AÑO DE CONSTRUCCIÓN", "AÑO CONSTRUCCION");
        r.rooms = labeled(t, "HABITACIONES");
        r.bathrooms = labeled(t, "BAÑOS");
        r.coverageContinental = labeled(t, "CONTINENTE");
        r.coverageContents = labeled(t, "CONTENIDO");
        return r;
    }

    private static String labeled(String text, String... labels) {
        for (String line : text.split("\\n")) {
            String u = line.toUpperCase();
            for (String label : labels) {
                int p = u.indexOf(label);
                if (p >= 0) return line.substring(Math.min(line.length(), p + label.length())).replaceFirst("^[\\s:.-]+", "").trim();
            }
        }
        return "";
    }

    public static final class Result {
        public String policyNumber="", holder="", insuredId="", address="", city="", postalCode="", phone="", email="";
        public String effectiveDate="", expiryDate="", totalReceipt="", propertyType="", surface="", constructionYear="";
        public String rooms="", bathrooms="", coverageContinental="", coverageContents="";
    }
}
