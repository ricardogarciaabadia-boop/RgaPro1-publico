package com.rgapro1.ocaso;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Product-aware parser for Ocaso policy OCR. Never promotes unlabeled OCR numbers to policy number. */
public final class PolicyOcrParser {
    private static final Pattern POLICY_NUMBER = Pattern.compile("(?im)(?:N[º°.]?\\s*(?:DE\\s*)?P[ÓO]LIZA|N[ÚU]M(?:ERO)?\\s*(?:DE\\s*)?P[ÓO]LIZA|P[ÓO]LIZA\\s*(?:N[º°.]?|N[UÚ]M(?:ERO)?))\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9./_-]{4,})\\b");
    private static final Pattern DNI = Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b", Pattern.CASE_INSENSITIVE);
    private PolicyOcrParser() {}

    public static JSONObject parse(String raw) {
        JSONObject out = new JSONObject();
        String text = normalize(raw), upper = text.toUpperCase(Locale.ROOT);
        try {
            String product = product(upper);
            if (!product.isEmpty()) out.put("type", product);
            String number = firstPolicyNumber(text);
            if (!number.isEmpty()) out.put("number", number);
            copy(out, extractHolder(text), "holder", "holderDni", "identityType");
            if ("Decesos Integral".equals(product)) {
                JSONArray insureds = extractDecesosInsureds(text);
                out.put("insureds", insureds);
                out.put("insuredCount", insureds.length());
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String product(String s) {
        if (s.contains("DECESOS INTEGRAL") || s.contains("POLIZA DE SEGURO DECESOS")) return "Decesos Integral";
        if (s.contains("ASISTENCIA FAMILIAR XXI")) return "Asistencia Familiar XXI";
        if (s.contains("ACCIDENTES DE LA MUJER")) return "Ocaso Accidentes de la Mujer";
        if (s.contains("VIDA A PRIMA PERIODICA") || s.contains("AHORRO GARANTIZADO FLEXIBLE")) return "Ocaso Ahorro Garantizado Flexible";
        if (s.contains("OCASO COMUNIDADES") || s.contains("OCASO COMUNIDAD")) return "Ocaso Comunidades";
        if (s.contains("OCASO HOGAR SENIOR")) return "Ocaso Hogar Senior";
        if (s.contains("OCASO HOGAR PROTECCION") || s.contains("OCASO HOGAR PROTECCIÓN")) return "Ocaso Hogar Protección";
        if (s.contains("OCASO HOGAR")) return "Ocaso Hogar";
        return "";
    }

    private static String firstPolicyNumber(String text) {
        Matcher m = POLICY_NUMBER.matcher(text == null ? "" : text);
        while (m.find()) {
            String value = clean(m.group(1));
            String u = value.toUpperCase(Locale.ROOT);
            if (u.matches("[A-Z0-9][A-Z0-9./_-]{4,}") && !u.matches("(?i)(DE|DEL|SEGURO|OCASO|POLIZA|PÓLIZA)")) return value;
        }
        return "";
    }

    private static JSONObject extractHolder(String text) throws Exception {
        JSONObject out = new JSONObject();
        String[] lines = text.replace('\r', '\n').split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = clean(lines[i]);
            if (!line.toUpperCase(Locale.ROOT).contains("TOMADOR")) continue;
            String name = line.replaceFirst("(?i).*TOMADOR(?: DEL SEGURO)?\\s*:?\\s*", "").trim();
            if (name.isEmpty() || name.toUpperCase(Locale.ROOT).contains("DOMICILIO")) if (i + 1 < lines.length) name = clean(lines[i + 1]);
            if (!name.isEmpty() && !name.matches(".*\\d{5,}.*") && !name.toUpperCase(Locale.ROOT).contains("DOMICILIO")) out.put("holder", name);
            StringBuilder block = new StringBuilder();
            for (int j = i; j < Math.min(lines.length, i + 7); j++) block.append(lines[j]).append('\n');
            Matcher id = DNI.matcher(block.toString().toUpperCase(Locale.ROOT));
            if (id.find()) {
                String value = id.group().toUpperCase(Locale.ROOT);
                out.put("holderDni", value);
                out.put("identityType", value.matches("[XYZ].*") ? "NIE" : "DNI");
            }
            break;
        }
        return out;
    }

    private static JSONArray extractDecesosInsureds(String text) throws Exception {
        JSONArray out = new JSONArray();
        String upper = text.toUpperCase(Locale.ROOT);
        int start = upper.indexOf("RELACION DE ASEGURADOS");
        if (start < 0) start = upper.indexOf("RELACIÓN DE ASEGURADOS");
        if (start < 0) return out;
        int end = upper.indexOf("GARANTIAS", start);
        if (end < 0) end = upper.indexOf("GARANTÍAS", start);
        if (end < 0) end = Math.min(text.length(), start + 5000);
        String[] lines = text.substring(start, Math.min(end, text.length())).replace('\r', '\n').split("\\n");
        Pattern row = Pattern.compile("^\\s*(\\d{3})\\s+(\\d{8}[A-Z]|[XYZ]\\d{7}[A-Z])\\s+(.+?)\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+([A-Z])(?:\\s+(\\d{1,2}/\\d{1,2}/\\d{4}))?\\s*$", Pattern.CASE_INSENSITIVE);
        for (String rawLine : lines) {
            Matcher m = row.matcher(clean(rawLine));
            if (!m.matches()) continue;
            JSONObject person = new JSONObject();
            person.put("insuredIndex", Integer.parseInt(m.group(1)));
            person.put("name", clean(m.group(3)));
            person.put("holder", clean(m.group(3)));
            person.put("identityNumber", m.group(2).toUpperCase(Locale.ROOT));
            person.put("identityType", m.group(2).toUpperCase(Locale.ROOT).matches("[XYZ].*") ? "NIE" : "DNI");
            person.put("birthDate", normalizeDate(m.group(4)));
            person.put("sex", m.group(5).toUpperCase(Locale.ROOT));
            if (m.group(6) != null) person.put("effectiveDeathDate", normalizeDate(m.group(6)));
            out.put(person);
        }
        return out;
    }

    private static void copy(JSONObject dst, JSONObject src, String... keys) throws Exception { for (String k : keys) if (src.has(k)) dst.put(k, src.get(k)); }
    private static String normalize(String s) { if (s == null) return ""; return s.replace('\r', '\n').replace("P0LIZA", "POLIZA").replace("DECES0S", "DECESOS").replace("ASEGURAD0S", "ASEGURADOS").replace("T0MADOR", "TOMADOR"); }
    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private static String normalizeDate(String s) { return s == null ? "" : s.replace('-', '/').replace('.', '/'); }
}
