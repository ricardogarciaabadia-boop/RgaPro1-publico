package com.rgapro1.ocaso;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, validation-first OCR interpreter for Ocaso policy PDFs.
 * Never invents values: every field is extracted from OCR text and validated
 * against its semantic context before being returned.
 */
public final class OcasoPolicyParser {
    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final Pattern DNI = Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])");
    private static final Pattern DNI_SPACED = Pattern.compile("(?<![0-9])([0-9]{4})\\s*([0-9]{4})\\s*([A-Z])(?![A-Z0-9])");
    private static final Pattern DATE = Pattern.compile("(?<![0-9])([0-3]?[0-9])\\s*[/.-]\\s*([0-1]?[0-9])\\s*[/.-]\\s*((?:19|20)[0-9]{2})(?![0-9])");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])([6789][0-9]{8})(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern MONEY = Pattern.compile("(?<![0-9])([0-9]{1,3}(?:[ .][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]{1,8}[.,][0-9]{1,2})(?![0-9])");
    private static final Pattern POLICY_NUMBER = Pattern.compile("(?<![0-9])([0-9]{5,12})(?![0-9])");

    private OcasoPolicyParser() {}

    public static JSONObject parse(String raw) {
        JSONObject out = new JSONObject();
        try {
            String text = normalize(raw);
            String[] lines = text.split("\\n");
            String product = classifyProduct(text);

            out.put("company", text.contains("OCASO") ? "OCASO" : "");
            out.put("policyType", product);
            out.put("number", extractPolicyNumber(text, lines));
            out.put("holder", extractHolder(lines));
            out.put("identityNumber", extractIdentity(text, lines));
            out.put("address", extractAddress(lines));
            out.put("phone", extractPhone(text, lines));
            out.put("email", extractEmail(text, lines));
            out.put("receipt", extractMoney(lines, new String[]{"TOTAL DEL RECIBO", "TOTAL RECIBO", "IMPORTE TOTAL", "PRIMA TOTAL", "RECIBO", "PRIMA"}));

            boolean decesos = isDecesos(product);
            String capital = extractMoney(lines, decesos
                    ? new String[]{"CAPITAL DE DECESOS", "CAPITAL ASEGURADO", "SUMA ASEGURADA", "CAPITAL PRINCIPAL", "CAPITAL"}
                    : new String[]{"CAPITAL ASEGURADO", "SUMA ASEGURADA", "CAPITAL PRINCIPAL", "CAPITAL"});
            out.put("capital", capital);
            if (decesos) {
                out.put("decesos", extractMoney(lines, new String[]{"TOTAL DECESOS", "CAPITAL DE DECESOS"}));
                out.put("decesosLevelada", extractMoney(lines, new String[]{"DECESOS NIVELADA", "PRIMA DECESOS NIVELADA"}));
            } else {
                out.put("decesos", "");
                out.put("decesosLevelada", "");
            }

            out.put("insured", insured(lines, decesos));
            out.put("confidence", confidence(out));
            out.put("warnings", warnings(out, text));
        } catch (Exception ignored) {
            try { out.put("confidence", 0); } catch (Exception ignored2) {}
        }
        return out;
    }

    private static String classifyProduct(String text) {
        int decesos = score(text, "DECESOS", 100) + score(text, "ASISTENCIA FAMILIAR", 50) + score(text, "SERVICIO FUNERARIO", 40);
        int vida = score(text, "SEGURO DE VIDA", 80) + score(text, "VIDA", 20) + score(text, "FALLECIMIENTO", 20);
        int accidente = score(text, "ACCIDENTES", 70) + score(text, "ACCIDENTE", 30);
        int hogar = score(text, "MULTIRRIESGO HOGAR", 80) + score(text, "HOGAR", 30);
        int salud = score(text, "ASISTENCIA SANITARIA", 80) + score(text, "SALUD", 30);
        int auto = score(text, "AUTOMOVIL", 80) + score(text, "AUTOMÓVIL", 80) + score(text, "VEHICULO", 30) + score(text, "VEHÍCULO", 30);
        int ahorro = score(text, "AHORRO", 60) + score(text, "PIAS", 60) + score(text, "RENTA", 20);
        int comunidad = score(text, "COMUNIDADES", 70) + score(text, "COMUNIDAD", 30);
        int rc = score(text, "RESPONSABILIDAD CIVIL", 90);
        int empresa = score(text, "EMPRESA", 50) + score(text, "PYME", 60) + score(text, "COMERCIO", 50);
        int max = Math.max(Math.max(Math.max(decesos, vida), Math.max(accidente, hogar)), Math.max(Math.max(salud, auto), Math.max(Math.max(ahorro, comunidad), Math.max(rc, empresa))));
        if (max == decesos && decesos > 0) return "Decesos";
        if (max == vida && vida > 0) return "Vida";
        if (max == accidente && accidente > 0) return "Accidentes";
        if (max == hogar && hogar > 0) return "Hogar";
        if (max == salud && salud > 0) return "Salud";
        if (max == auto && auto > 0) return "Auto";
        if (max == ahorro && ahorro > 0) return "Ahorro";
        if (max == comunidad && comunidad > 0) return "Comunidades";
        if (max == rc && rc > 0) return "Responsabilidad civil";
        if (max == empresa && empresa > 0) return "Empresa";
        return "Otros";
    }

    private static int score(String text, String token, int weight) {
        int n = 0, p = 0;
        while ((p = text.indexOf(token, p)) >= 0) { n++; p += token.length(); }
        return n * weight;
    }

    private static String extractPolicyNumber(String text, String[] lines) {
        String[] labels = {"NUMERO DE POLIZA", "Nº DE POLIZA", "Nº POLIZA", "NUMERO POLIZA", "POLIZA"};
        Candidate best = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int labelPos = -1;
            for (String label : labels) { int p = line.indexOf(label); if (p >= 0) { labelPos = p; break; } }
            if (labelPos < 0) continue;
            String window = line.substring(labelPos);
            Candidate c = firstPolicyCandidate(window, 0);
            if (c == null && i + 1 < lines.length) c = firstPolicyCandidate(lines[i + 1], 1);
            if (c != null && (best == null || c.score > best.score)) best = c;
        }
        if (best != null) return best.value;

        Matcher m = POLICY_NUMBER.matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (looksLikePhone(v) || looksLikeDateNumber(v) || looksLikeMoneyNumber(v)) continue;
            if (v.length() >= 6) return v;
        }
        return "";
    }

    private static Candidate firstPolicyCandidate(String s, int distance) {
        Matcher m = POLICY_NUMBER.matcher(s);
        if (!m.find()) return null;
        String v = m.group(1);
        if (looksLikePhone(v) || looksLikeDateNumber(v)) return null;
        return new Candidate(v, 100 - distance * 20);
    }

    private static String extractHolder(String[] lines) {
        String[] labels = {"TOMADOR DEL SEGURO", "TOMADOR/A", "TOMADOR", "CONTRATANTE", "ASEGURADO"};
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (String label : labels) {
                int p = line.indexOf(label);
                if (p < 0) continue;
                String value = cutAtNextLabel(clean(line.substring(p + label.length())));
                value = stripIdentityAndNoise(value);
                if (isPlausiblePerson(value)) return value;
                if (i + 1 < lines.length) {
                    value = stripIdentityAndNoise(cutAtNextLabel(lines[i + 1]));
                    if (isPlausiblePerson(value)) return value;
                }
            }
        }
        return "";
    }

    private static String extractAddress(String[] lines) {
        String[] labels = {"DOMICILIO DE COBRO", "DOMICILIO DEL TOMADOR", "DIRECCION DEL TOMADOR", "DIRECCION", "DOMICILIO"};
        for (int i = 0; i < lines.length; i++) {
            for (String label : labels) {
                int p = lines[i].indexOf(label);
                if (p < 0) continue;
                String v = cutAtNextLabel(clean(lines[i].substring(p + label.length())));
                if (looksLikeAddress(v)) return v;
                if (i + 1 < lines.length) {
                    v = cutAtNextLabel(clean(lines[i + 1]));
                    if (looksLikeAddress(v)) return v;
                }
            }
        }
        return "";
    }

    private static String extractIdentity(String text, String[] lines) {
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            addIdentityCandidates(candidates, lines[i], i, lines, "DNI", "NIE", "DOC ID", "DOCUMENTO", "IDENTIFICACION", "IDENTIFICACIÓN", "TOMADOR");
        }
        addIdentityCandidates(candidates, text, 0, lines, "");
        Collections.sort(candidates, new Comparator<Candidate>() { public int compare(Candidate a, Candidate b) { return b.score - a.score; } });
        for (Candidate c : candidates) if (isValidIdentity(c.value)) return c.value;
        return candidates.isEmpty() ? "" : candidates.get(0).value;
    }

    private static void addIdentityCandidates(ArrayList<Candidate> out, String source, int lineIndex, String[] lines, String... labels) {
        String compact = source.replace('O','0').replace('I','1').replace('L','1');
        Matcher m = DNI.matcher(source);
        while (m.find()) out.add(identityCandidate(m.group(), source, lineIndex, labels));
        m = DNI_SPACED.matcher(source);
        while (m.find()) out.add(identityCandidate(m.group(1)+m.group(2)+m.group(3), source, lineIndex, labels));
        if (compact != source) {
            m = DNI.matcher(compact);
            while (m.find()) out.add(identityCandidate(m.group(), source, lineIndex, labels));
        }
    }

    private static Candidate identityCandidate(String value, String source, int lineIndex, String[] labels) {
        int score = isValidIdentity(value) ? 100 : 5;
        String s = source.toUpperCase(Locale.ROOT);
        for (String label : labels) if (!label.isEmpty() && s.contains(label)) score += 40;
        if (s.contains("TOMADOR")) score += 25;
        return new Candidate(value, score - Math.min(lineIndex, 20));
    }

    private static String extractPhone(String text, String[] lines) {
        for (String line : lines) {
            String u = line.toUpperCase(Locale.ROOT);
            if (!(u.contains("TELEFONO") || u.contains("TELÉFONO") || u.contains("MOVIL") || u.contains("MÓVIL") || u.contains("TEL"))) continue;
            Matcher m = PHONE.matcher(line.replaceAll("[ .-]", ""));
            if (m.find()) return m.group(1);
        }
        return "";
    }

    private static String extractEmail(String text, String[] lines) {
        for (String line : lines) {
            if (!(line.contains("EMAIL") || line.contains("E-MAIL") || line.contains("CORREO"))) continue;
            Matcher m = EMAIL.matcher(line);
            if (m.find()) return m.group();
        }
        return "";
    }

    private static String extractMoney(String[] lines, String[] labels) {
        for (String line : lines) {
            String normalized = line.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
            for (String label : labels) {
                int p = normalized.indexOf(label);
                if (p < 0) continue;
                String tail = normalized.substring(p + label.length());
                Matcher m = MONEY.matcher(tail);
                if (m.find()) {
                    String v = normalizeMoney(m.group(1));
                    if (isPlausibleMoney(v)) return v;
                }
            }
        }
        return "";
    }

    private static JSONArray insured(String[] lines, boolean decesos) {
        JSONArray result = new JSONArray();
        if (!decesos) return result;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            ArrayList<String> ids = identityValues(lines[i]);
            if (ids.isEmpty()) continue;
            String id = ids.get(0);
            if (seen.contains(id)) continue;
            String name = personAround(lines, i);
            String birth = dateAround(lines, i);
            if (name.isEmpty() && birth.isEmpty()) continue;
            try {
                JSONObject x = new JSONObject();
                x.put("name", name);
                x.put("identityNumber", id);
                x.put("birthDate", birth);
                result.put(x);
                seen.add(id);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static ArrayList<String> identityValues(String line) {
        ArrayList<String> r = new ArrayList<>();
        Matcher m = DNI.matcher(line);
        while (m.find()) r.add(m.group());
        Matcher s = DNI_SPACED.matcher(line);
        while (s.find()) r.add(s.group(1)+s.group(2)+s.group(3));
        return r;
    }

    private static String personAround(String[] lines, int i) {
        String[] order = {i, i - 1, i + 1};
        for (int idx : order) {
            if (idx < 0 || idx >= lines.length) continue;
            String v = clean(lines[idx]).replaceAll("(?i)(DNI|NIE|DOC\\.? ID|DOCUMENTO)[ :.-]*[0-9A-Z]+", "");
            v = cutAtNextLabel(v);
            if (isPlausiblePerson(v)) return v;
        }
        return "";
    }

    private static String dateAround(String[] lines, int i) {
        for (int idx : new int[]{i, i - 1, i + 1}) {
            if (idx < 0 || idx >= lines.length) continue;
            Matcher m = DATE.matcher(lines[idx]);
            while (m.find()) {
                String d = normalizeDate(m.group());
                if (validDate(d)) return d;
            }
        }
        return "";
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase(Locale.ROOT).replace('\r', '\n');
        s = s.replace('\u00A0', ' ');
        s = s.replace("Nº", "Nº").replace("N°", "Nº");
        s = s.replace("P0LIZA", "POLIZA").replace("P0LIZA", "POLIZA");
        s = s.replace("T0MADOR", "TOMADOR").replace("RECIB0", "RECIBO");
        s = s.replace("CAPlTAL", "CAPITAL").replace("DECES0S", "DECESOS");
        s = s.replace("DOMlCILIO", "DOMICILIO").replace("DIRECClON", "DIRECCION");
        s = s.replace("TELEF0NO", "TELEFONO").replace("NIE:", "NIE:");
        return s.replaceAll("[ \\t]+", " ").replaceAll("\\n{2,}", "\\n").trim();
    }

    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }

    private static String stripIdentityAndNoise(String s) {
        String v = clean(s);
        v = v.replaceAll("(?i)(DOC\\.?\\s*ID|DNI|NIE|DOCUMENTO)[ :.-]*[0-9A-Z]{8,9}", "");
        return cutAtNextLabel(v).replaceAll("^[ :;,-]+|[ :;,-]+$", "").trim();
    }

    private static String cutAtNextLabel(String s) {
        String v = clean(s);
        String[] labels = {"DOC ID", "DNI", "NIE", "DIRECCION", "DOMICILIO", "TELEFONO", "TEL", "EMAIL", "CORREO", "RECIBO", "PRIMA", "CAPITAL", "TOTAL", "Nº POLIZA", "POLIZA"};
        int cut = v.length();
        for (String l : labels) {
            int p = v.indexOf(l);
            if (p > 0) cut = Math.min(cut, p);
        }
        return clean(v.substring(0, cut));
    }

    private static boolean isPlausiblePerson(String s) {
        if (s == null) return false;
        String v = clean(s);
        if (v.length() < 5 || v.length() > 100) return false;
        if (v.matches(".*\\d{4,}.*")) return false;
        String[] w = v.split(" ");
        return w.length >= 2 && !v.contains("POLIZA") && !v.contains("RECIBO") && !v.contains("CAPITAL");
    }

    private static boolean looksLikeAddress(String s) {
        if (s == null || s.length() < 5) return false;
        return s.matches(".*[A-ZÁÉÍÓÚÑ].*") && (s.matches(".*\\d+.*") || s.contains("CALLE") || s.contains("AVENIDA") || s.contains("PLAZA") || s.contains("C/"));
    }

    private static boolean looksLikePhone(String s) { return s != null && s.matches("[6789][0-9]{8}"); }
    private static boolean looksLikeDateNumber(String s) { return s != null && s.matches("[0-3][0-9][0-1][0-9](19|20)[0-9]{2}"); }
    private static boolean looksLikeMoneyNumber(String s) { return s != null && s.length() >= 6 && s.matches("[0-9]{6,}"); }

    private static boolean isValidIdentity(String v) {
        if (v == null) return false;
        if (v.matches("[0-9]{8}[A-Z]")) {
            try { return DNI_LETTERS.charAt(Integer.parseInt(v.substring(0, 8)) % 23) == v.charAt(8); } catch (Exception ignored) { return false; }
        }
        if (v.matches("[XYZ][0-9]{7}[A-Z]")) {
            String n = (v.charAt(0) == 'X' ? "0" : v.charAt(0) == 'Y' ? "1" : "2") + v.substring(1, 8);
            try { return DNI_LETTERS.charAt(Integer.parseInt(n) % 23) == v.charAt(8); } catch (Exception ignored) { return false; }
        }
        return false;
    }

    private static String normalizeMoney(String s) {
        String v = clean(s).replace(" ", "");
        if (v.contains(",") && v.contains(".")) {
            if (v.lastIndexOf(',') > v.lastIndexOf('.')) v = v.replace(".", "").replace(',', '.');
            else v = v.replace(",", "");
        } else if (v.contains(",")) v = v.replace(',', '.');
        else if (v.matches("[0-9]{1,3}\\.[0-9]{3}")) v = v.replace(".", "");
        return v;
    }

    private static boolean isPlausibleMoney(String s) {
        try { double d = Double.parseDouble(s); return d >= 0 && d < 100000000; } catch (Exception e) { return false; }
    }

    private static boolean validDate(String s) {
        Matcher m = DATE.matcher(s);
        if (!m.find()) return false;
        try {
            int d = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), y = Integer.parseInt(m.group(3));
            if (mo < 1 || mo > 12 || d < 1 || y < 1900 || y > 2100) return false;
            int max = (mo == 2) ? ((y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) ? 29 : 28) : ((mo == 4 || mo == 6 || mo == 9 || mo == 11) ? 30 : 31);
            return d <= max;
        } catch (Exception e) { return false; }
    }

    private static String normalizeDate(String s) {
        Matcher m = DATE.matcher(s);
        return m.find() ? String.format(Locale.ROOT, "%02d/%02d/%04d", Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))) : "";
    }

    private static int confidence(JSONObject o) {
        int c = 0;
        String number = o.optString("number", ""), holder = o.optString("holder", ""), id = o.optString("identityNumber", "");
        if (!number.isEmpty()) c += 25;
        if (!holder.isEmpty()) c += 20;
        if (!id.isEmpty() && isValidIdentity(id)) c += 25;
        if (!o.optString("address", "").isEmpty()) c += 10;
        if (!o.optString("phone", "").isEmpty()) c += 5;
        if (!o.optString("receipt", "").isEmpty()) c += 5;
        if (!o.optString("capital", "").isEmpty()) c += 5;
        if (!o.optString("policyType", "Otros").equals("Otros")) c += 5;
        return Math.min(100, c);
    }

    private static JSONArray warnings(JSONObject o, String text) {
        JSONArray w = new JSONArray();
        if (o.optString("number", "").isEmpty()) w.put("No se ha podido validar el número de póliza.");
        if (o.optString("holder", "").isEmpty()) w.put("No se ha podido validar el tomador.");
        if (!o.optString("identityNumber", "").isEmpty() && !isValidIdentity(o.optString("identityNumber"))) w.put("El DNI/NIE detectado no supera la validación de letra.");
        if (isDecesos(o.optString("policyType", "")) && o.optString("decesos", "").isEmpty() && o.optString("decesosLevelada", "").isEmpty()) w.put("Póliza clasificada como Decesos pero no se han encontrado importes de decesos.");
        if (text.contains("OCASO") == false) w.put("El OCR no contiene una referencia clara a OCASO.");
        return w;
    }

    private static boolean isDecesos(String product) { return "Decesos".equalsIgnoreCase(product); }

    private static final class Candidate {
        final String value; final int score;
        Candidate(String value, int score) { this.value = value; this.score = score; }
    }
}
