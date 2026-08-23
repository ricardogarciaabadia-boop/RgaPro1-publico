package com.rgapro1.ocaso;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Product-aware parser for Ocaso policy OCR. It deliberately separates the
 * policyholder from each insured person, especially for Decesos policies.
 */
public final class PolicyOcrParser {
    private static final Pattern POLICY = Pattern.compile("(?:N[º°.]?\\s*)?(?:DE\\s*)?(?:P[ÓO]LIZA|POLIZA)\\s*(?:N[º°.]?|NUM(?:ERO)?)?\\s*[:#-]?\\s*([A-Z0-9./_-]{5,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DNI = Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\b\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{2,4}\\b");

    private PolicyOcrParser() {}

    public static JSONObject parse(String raw) {
        JSONObject out = new JSONObject();
        String text = normalize(raw);
        String upper = text.toUpperCase(Locale.ROOT);
        try {
            String product = product(upper);
            if (!product.isEmpty()) out.put("type", product);
            String number = first(POLICY, upper);
            if (!number.isEmpty()) out.put("number", number);

            JSONObject holder = extractHolder(text, upper);
            copy(out, holder, "holder", "holderDni", "identityType");

            if ("Decesos Integral".equals(product)) {
                JSONArray insureds = extractDecesosInsureds(text);
                out.put("insureds", insureds);
                out.put("insuredCount", insureds.length());
            }
        } catch (Exception ignored) {
            // OCR is best-effort; caller keeps the original text when parsing fails.
        }
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

    private static JSONObject extractHolder(String text, String upper) throws Exception {
        JSONObject out = new JSONObject();
        Matcher m = Pattern.compile("TOMADOR(?: DEL SEGURO)?\\s*:?\\s*([^\\n]{3,100})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            String name = clean(m.group(1));
            if (!name.isEmpty() && !name.matches(".*\\d{5,}.*")) out.put("holder", name);
            int end = Math.min(text.length(), m.end() + 220);
            String block = text.substring(m.start(), end).toUpperCase(Locale.ROOT);
            String id = first(DNI, block);
            if (!id.isEmpty()) {
                out.put("holderDni", id.toUpperCase(Locale.ROOT));
                out.put("identityType", id.matches("[XYZ].*") ? "NIE" : "DNI");
            }
        }
        if (!out.has("holder")) {
            Matcher simple = Pattern.compile("TOMADOR DEL SEGURO\\s+([^\\n]+)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (simple.find()) out.put("holder", clean(simple.group(1)));
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
        String table = text.substring(start, Math.min(end, text.length()));

        String[] lines = table.replace('\r', '\n').split("\\n");
        Pattern row = Pattern.compile("^\\s*(\\d{3})\\s+(.+?)\\s+(\\d{8}[A-Z]|[XYZ]\\d{7}[A-Z])\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+([A-Z])(?:\\s+(\\d{1,2}/\\d{1,2}/\\d{4}))?\\s*$", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            Matcher m = row.matcher(clean(line));
            if (!m.matches()) continue;
            JSONObject person = new JSONObject();
            person.put("insuredIndex", Integer.parseInt(m.group(1)));
            person.put("name", clean(m.group(2)));
            person.put("holder", clean(m.group(2)));
            person.put("identityNumber", m.group(3).toUpperCase(Locale.ROOT));
            person.put("identityType", m.group(3).toUpperCase(Locale.ROOT).matches("[XYZ].*") ? "NIE" : "DNI");
            person.put("birthDate", normalizeDate(m.group(4)));
            person.put("sex", m.group(5).toUpperCase(Locale.ROOT));
            if (m.group(6) != null) person.put("effectiveDeathDate", normalizeDate(m.group(6)));
            out.put(person);
        }
        return out;
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s == null ? "" : s);
        return m.find() ? m.group(1) : "";
    }

    private static void copy(JSONObject dst, JSONObject src, String... keys) throws Exception {
        for (String key : keys) if (src.has(key)) dst.put(key, src.get(key));
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\r', '\n')
                .replace("Nº", "Nº")
                .replace("N0", "Nº")
                .replace("P0LIZA", "POLIZA")
                .replace("DECES0S", "DECESOS")
                .replace("ASEGURAD0S", "ASEGURADOS")
                .replace("T0MADOR", "TOMADOR");
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeDate(String s) {
        return s == null ? "" : s.replace('-', '/').replace('.', '/');
    }
}
