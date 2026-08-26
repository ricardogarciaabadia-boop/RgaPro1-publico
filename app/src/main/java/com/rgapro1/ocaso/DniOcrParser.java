package com.rgapro1.ocaso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validation-first parser for Spanish DNI/NIE OCR from both sides and MRZ. */
public final class DniOcrParser {
    private static final String LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final Pattern DATE = Pattern.compile("(?<![0-9])([0-3]?[0-9])[/.-]([0-1]?[0-9])[/.-]((?:19|20)[0-9]{2})(?![0-9])");
    private static final Pattern ID = Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])");
    private static final Pattern ID_SPACED = Pattern.compile("(?<![0-9])([0-9]{4})\\s*([0-9]{4})\\s*([A-Z])(?![A-Z0-9])");

    public static final class Result {
        public String holder="", surname="", name="", dni="", birthDate="", nationality="", sex="";
        public String address="", birthPlace="", parents="", supportNumber="", issueDate="", validityDate="";
        public String mrz="";
        public int confidence=0;
    }

    private DniOcrParser() {}

    public static Result parse(String raw) {
        Result r = new Result();
        String text = normalize(raw);
        String[] lines = text.split("\\n");

        r.surname = value(lines, "APELLIDOS", "APELLIDO");
        r.name = value(lines, "NOMBRE", "NOMBRES");
        r.nationality = value(lines, "NACIONALIDAD");
        r.sex = value(lines, "SEXO");
        r.address = value(lines, "DOMICILIO", "DOMICILIO DEL TITULAR");
        r.birthPlace = value(lines, "LUGAR DE NACIMIENTO");
        r.parents = value(lines, "HIJO/A DE", "HIJO DE", "HIJA DE");
        r.supportNumber = value(lines, "NUM SOPORTE", "Nº SOPORTE", "N° SOPORTE", "NUMERO SOPORTE");
        r.issueDate = dateAfter(lines, "EMISION", "EMISIÓN", "FECHA DE EMISION", "FECHA DE EMISIÓN");
        r.validityDate = dateAfter(lines, "VALIDEZ", "CADUCIDAD", "FECHA DE CADUCIDAD");

        ArrayList<Candidate> ids = collectIds(text, lines);
        Collections.sort(ids, new Comparator<Candidate>() { public int compare(Candidate a, Candidate b) { return b.score - a.score; } });
        for (Candidate c : ids) if (validIdentity(c.value)) { r.dni = c.value; break; }
        if (r.dni.isEmpty() && !ids.isEmpty()) r.dni = ids.get(0).value;

        r.birthDate = bestBirthDate(text, r.issueDate, r.validityDate);

        String mrz = extractMrz(lines);
        r.mrz = mrz;
        if (!mrz.isEmpty()) parseMrz(mrz, r);

        if (r.name.isEmpty() || r.surname.isEmpty()) {
            String[] names = mrzNames(mrz);
            if (r.surname.isEmpty()) r.surname = names[0];
            if (r.name.isEmpty()) r.name = names[1];
        }
        if (!r.name.isEmpty() || !r.surname.isEmpty()) r.holder = clean((r.name + " " + r.surname).trim());
        if (r.holder.isEmpty()) r.holder = fallbackPerson(lines);

        int c = 0;
        if (!r.dni.isEmpty() && validIdentity(r.dni)) c += 35;
        if (!r.name.isEmpty()) c += 20;
        if (!r.surname.isEmpty()) c += 20;
        if (validDate(r.birthDate)) c += 15;
        if (validDate(r.validityDate)) c += 5;
        if (!r.nationality.isEmpty()) c += 5;
        r.confidence = Math.min(100, c);
        return r;
    }

    private static ArrayList<Candidate> collectIds(String text, String[] lines) {
        ArrayList<Candidate> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = ID.matcher(line);
            while (m.find()) out.add(scoreId(m.group(), line, i));
            m = ID_SPACED.matcher(line);
            while (m.find()) out.add(scoreId(m.group(1)+m.group(2)+m.group(3), line, i));
            String repaired = repairIdOcr(line);
            m = ID.matcher(repaired);
            while (m.find()) out.add(new Candidate(m.group(), scoreId(m.group(), line, i).score + 12));
        }
        Matcher all = ID.matcher(text);
        while (all.find()) out.add(scoreId(all.group(), text, 0));
        return out;
    }

    private static Candidate scoreId(String id, String context, int line) {
        int score = validIdentity(id) ? 100 : 1;
        String u = context.toUpperCase(Locale.ROOT);
        if (u.contains("DNI") || u.contains("NIE") || u.contains("DOCUMENTO") || u.contains("NUMERO DE IDENTIDAD")) score += 45;
        if (u.contains("IDESP")) score += 35;
        if (u.contains("TOMADOR") || u.contains("TITULAR")) score += 10;
        return new Candidate(id, score - Math.min(line, 15));
    }

    private static String repairIdOcr(String line) {
        String s = line.toUpperCase(Locale.ROOT);
        // Only repair OCR confusions inside candidate-like alphanumeric runs; never alter free-form names.
        s = s.replaceAll("(?<![A-Z0-9])[OQ](?=[0-9]{7,8}[A-Z])(?![A-Z0-9])", "0");
        s = s.replaceAll("(?<![A-Z0-9])([0-9]{4})\\s*([0-9]{4})[OQ](?![A-Z0-9])", "$1$2O");
        s = s.replaceAll("(?<![A-Z0-9])([XYZ])([0-9]{7})0(?![A-Z0-9])", "$1$2O");
        return s.replace("ID ESP", "IDESP").replace("ID-ESP", "IDESP");
    }

    private static String bestBirthDate(String text, String issue, String validity) {
        Matcher labeled = Pattern.compile("(?:FECHA DE NACIMIENTO|NACIMIENTO|NAC)[^0-9]{0,40}("+DATE.pattern().substring(1, DATE.pattern().length()-1)+")").matcher(text);
        if (labeled.find()) {
            String d = normalizeDate(labeled.group(1));
            if (validDate(d)) return d;
        }
        Matcher m = DATE.matcher(text);
        String best = "";
        while (m.find()) {
            String d = normalizeDate(m.group());
            if (!validDate(d) || d.equals(issue) || d.equals(validity)) continue;
            if (year(d) <= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 10) {
                best = d;
                break;
            }
        }
        return best;
    }

    private static void parseMrz(String mrz, Result r) {
        String compact = mrz.replace(" ", "");
        Matcher mid = Pattern.compile("IDESP[^0-9]{0,4}([0-9]{8}[A-Z])").matcher(compact);
        while (mid.find()) if (validIdentity(mid.group(1))) { r.dni = mid.group(1); break; }

        String[] names = mrzNames(mrz);
        if (!names[0].isEmpty()) r.surname = names[0];
        if (!names[1].isEmpty()) r.name = names[1];

        String compactDigits = compact.replace('<', ' ');
        Matcher dates = Pattern.compile("([0-9]{6})[0-9]([MF<])([0-9]{6})[0-9]").matcher(compactDigits);
        if (dates.find()) {
            String b = mrzDate(dates.group(1));
            String v = mrzDate(dates.group(3));
            if (validDate(b)) r.birthDate = b;
            if (validDate(v)) r.validityDate = v;
            if (!"<".equals(dates.group(2))) r.sex = dates.group(2);
        }
        if (r.nationality.isEmpty() && compact.contains("ESP")) r.nationality = "ESP";
    }

    private static String[] mrzNames(String mrz) {
        String[] result = {"", ""};
        if (mrz == null || mrz.isEmpty()) return result;
        Matcher m = Pattern.compile("([A-ZÑ]+(?:<[A-ZÑ]+)*)<<([A-ZÑ]+(?:<[A-ZÑ]+)*)").matcher(mrz.replace(" ", ""));
        if (!m.find()) return result;
        result[0] = clean(m.group(1).replace('<', ' '));
        result[1] = clean(m.group(2).replace('<', ' '));
        return result;
    }

    private static String extractMrz(String[] lines) {
        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            String x = line.toUpperCase(Locale.ROOT).replace(" ", "");
            x = x.replace("ID ESP", "IDESP").replace("ID-ESP", "IDESP");
            if (x.contains("IDESP") || x.contains("<<")) b.append(x.replaceAll("[^A-Z0-9<]", "")).append('\n');
        }
        return b.toString().trim();
    }

    private static String fallbackPerson(String[] lines) {
        for (String line : lines) {
            String v = clean(line);
            if (v.matches("[A-ZÁÉÍÓÚÑ]{3,}(?: [A-ZÁÉÍÓÚÑ]{3,}){2,4}")) return v;
        }
        return "";
    }

    private static String value(String[] lines, String... labels) {
        for (int i = 0; i < lines.length; i++) {
            String line = clean(lines[i]);
            for (String label : labels) {
                int p = line.indexOf(label.toUpperCase(Locale.ROOT));
                if (p < 0) continue;
                String v = clean(line.substring(p + label.length()).replaceFirst("^[ :.-]+", ""));
                if (!v.isEmpty()) return cut(v);
                if (i + 1 < lines.length) return cut(lines[i + 1]);
            }
        }
        return "";
    }

    private static String dateAfter(String[] lines, String... labels) {
        for (String label : labels) {
            String v = value(lines, label);
            Matcher m = DATE.matcher(v);
            if (m.find()) return normalizeDate(m.group());
        }
        return "";
    }

    private static String cut(String s) {
        String v = clean(s);
        String[] labels = {"DNI", "NIE", "NACIONALIDAD", "SEXO", "NACIMIENTO", "DOMICILIO", "VALIDEZ", "CADUCIDAD", "EMISION", "SOPORTE"};
        int cut = v.length();
        for (String l : labels) { int p = v.indexOf(l); if (p > 0) cut = Math.min(cut, p); }
        return clean(v.substring(0, cut));
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toUpperCase(Locale.ROOT).replace('\r','\n')
                .replace("N0MBRE", "NOMBRE").replace("N0MBRES", "NOMBRES")
                .replace("APELLlDOS", "APELLIDOS").replace("APELLlDO", "APELLIDO")
                .replace("NACIMlENTO", "NACIMIENTO").replace("NACIMlENT0", "NACIMIENTO")
                .replace("VALlDEZ", "VALIDEZ").replace("EMlSION", "EMISION")
                .replace("NAC10NALIDAD", "NACIONALIDAD").replace("DOMIC1LIO", "DOMICILIO")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{2,}", "\\n");
    }

    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private static String normalizeDate(String s) { Matcher m=DATE.matcher(s); return m.find()?String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))):""; }
    private static int year(String d) { return Integer.parseInt(d.substring(6)); }

    private static boolean validIdentity(String value) {
        if (value == null) return false;
        if (value.matches("[0-9]{8}[A-Z]")) try { return LETTERS.charAt(Integer.parseInt(value.substring(0,8)) % 23) == value.charAt(8); } catch (Exception ignored) { return false; }
        if (value.matches("[XYZ][0-9]{7}[A-Z]")) {
            String n=(value.charAt(0)=='X'?"0":value.charAt(0)=='Y'?"1":"2")+value.substring(1,8);
            try { return LETTERS.charAt(Integer.parseInt(n)%23)==value.charAt(8); } catch(Exception ignored){return false;}
        }
        return false;
    }

    private static boolean validDate(String s) {
        Matcher m=DATE.matcher(s);if(!m.find())return false;
        try { int d=Integer.parseInt(m.group(1)),mo=Integer.parseInt(m.group(2)),y=Integer.parseInt(m.group(3)); if(mo<1||mo>12||d<1||y<1900||y>2100)return false; int max=mo==2?((y%4==0&&(y%100!=0||y%400==0))?29:28):((mo==4||mo==6||mo==9||mo==11)?30:31);return d<=max; } catch(Exception e){return false;}
    }

    private static String mrzDate(String yyMMdd) { try { int yy=Integer.parseInt(yyMMdd.substring(0,2)); int y=yy<=30?2000+yy:1900+yy; return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(yyMMdd.substring(4,6)),Integer.parseInt(yyMMdd.substring(2,4)),y); } catch(Exception e){return "";} }

    private static final class Candidate { final String value; final int score; Candidate(String v,int s){value=v;score=s;} }
}
