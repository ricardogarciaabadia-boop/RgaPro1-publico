package com.rgapro1.ocaso;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extractor estructurado para pólizas Ocaso. No sustituye la revisión humana. */
public final class OcasoPolicyParser {
    private OcasoPolicyParser() {}

    public static JSONObject parse(String raw) {
        JSONObject out = new JSONObject();
        try {
            String text = normalize(raw);
            out.put("company", text.contains("OCASO") ? "OCASO" : "");
            out.put("number", first(text, "(?:N[ÚU]MERO[ ]*(?:DE[ ]*)?P[ÓO]LIZA|N[º°]?[ ]*P[ÓO]LIZA|P[ÓO]LIZA)[ ]*[:#-]?[ ]*([0-9]{5,12})"));
            out.put("holder", valueAfterLabel(text, "TOMADOR", "TOMADOR/A", "CONTRATANTE"));
            out.put("identityNumber", firstIdNear(text, "TOMADOR", "DNI", "NIE", "DOCUMENTO"));
            out.put("address", valueAfterLabel(text, "DIRECCI[ÓO]N", "DOMICILIO"));
            out.put("phone", first(text, "(?:TEL[ÉE]FONO|M[ÓO]VIL|TEL)[ ]*[:#-]?[ ]*([0-9]{9})"));
            out.put("email", first(text, "(?:EMAIL|CORREO ELECTR[ÓO]NICO)[ ]*[:#-]?[ ]*([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})"));
            out.put("receipt", moneyNear(text, "(?:TOTAL[ ]+DEL[ ]+RECIBO|TOTAL[ ]+RECIBO|RECIBO|PRIMA)"));
            out.put("capital", moneyNear(text, "(?:CAPITAL[ ]+ASEGURADO|CAPITAL)"));
            out.put("decesos", moneyNear(text, "(?:TOTAL[ ]+DECESOS|DECESOS)"));
            out.put("decesosLevelada", moneyNear(text, "DECESOS[ ]+NIVELADA"));
            out.put("insured", insured(text));
        } catch (Exception ignored) {}
        return out;
    }

    private static JSONArray insured(String text) {
        JSONArray result = new JSONArray();
        String[] lines = text.split("\\n");
        Pattern dni = Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])");
        Pattern date = Pattern.compile("(?<![0-9])([0-3][0-9])[/.-]([0-1][0-9])[/.-]((?:19|20)[0-9]{2})(?![0-9])");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            Matcher dm = dni.matcher(line);
            if (!dm.find()) continue;
            String id = dm.group();
            String name = line.replace(id, "").replaceAll("[:;,-]+", " ").trim();
            String birth = "";
            Matcher bm = date.matcher(line);
            if (bm.find()) birth = bm.group(1)+"/"+bm.group(2)+"/"+bm.group(3);
            if (name.isEmpty() && i > 0) name = lines[i-1].trim();
            if (birth.isEmpty() && i + 1 < lines.length) {
                bm = date.matcher(lines[i+1]);
                if (bm.find()) birth = bm.group(1)+"/"+bm.group(2)+"/"+bm.group(3);
            }
            try { JSONObject x = new JSONObject(); x.put("name", clean(name)); x.put("identityNumber", id); x.put("birthDate", birth); result.put(x); } catch (Exception ignored) {}
        }
        return result;
    }

    private static String firstIdNear(String text, String... labels) {
        for (String label : labels) {
            int p = text.indexOf(label);
            if (p < 0) continue;
            int end = Math.min(text.length(), p + 500);
            Matcher m = Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])").matcher(text.substring(p, end));
            if (m.find()) return m.group();
        }
        Matcher m = Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])").matcher(text);
        return m.find() ? m.group() : "";
    }

    private static String valueAfterLabel(String text, String... labels) {
        String[] lines = text.split("\\n");
        for (int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            for(String label:labels) {
                Matcher m=Pattern.compile(label+"[ ]*[:#-]?[ ]*(.*)").matcher(line);
                if(m.find() && !m.group(1).trim().isEmpty()) return clean(m.group(1));
                if(line.equals(label) && i+1<lines.length) return clean(lines[i+1]);
            }
        }
        return "";
    }

    private static String moneyNear(String text, String label) {
        String[] lines=text.split("\\n");
        for(String line:lines) {
            if(!Pattern.compile(label,Pattern.CASE_INSENSITIVE).matcher(line).find()) continue;
            Matcher m=Pattern.compile("([0-9]{1,8}(?:[.,][0-9]{1,2})?)[ ]*(?:€|EUR)?",Pattern.CASE_INSENSITIVE).matcher(line);
            if(m.find()) return normalizeMoney(m.group(1));
        }
        return "";
    }

    private static String first(String text, String regex) { Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE).matcher(text); return m.find()?clean(m.group(1)):""; }
    private static String normalize(String raw) { return raw==null?"":raw.toUpperCase(Locale.ROOT).replace('\r','\n').replaceAll("\\n+","\\n").replaceAll("[ \\t]+"," "); }
    private static String clean(String s) { return s==null?"":s.trim().replaceAll("\\s+"," "); }
    private static String normalizeMoney(String s) { return clean(s).replace(',','.'); }
}
