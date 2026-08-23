package com.rgapro1.ocaso;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser específico para DNI/NIE español. Prioriza nombre, apellidos, DNI/NIE y nacimiento. */
public final class DniOcrParser {
    public static final class Result {
        public String holder="", surname="", name="", dni="", birthDate="", nationality="", sex="";
        public String address="", birthPlace="", parents="", supportNumber="", issueDate="", validityDate="";
        public String mrz="";
        public int confidence=0;
    }

    private static final String DNI_LETTERS="TRWAGMYFPDXBNJZSQVHLCKE";
    private static final Pattern ID_COMPACT=Pattern.compile("(?<![A-Z0-9])([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])");
    private static final Pattern ID_SPACED=Pattern.compile("(?<![A-Z0-9])([0-9]{2}\\s?[0-9]{6}[A-Z]|[XYZ]\\s?[0-9]{7}[A-Z])(?![A-Z0-9])");
    private static final Pattern DATE=Pattern.compile("\\b(\\d{1,2})[ /.-](\\d{1,2})[ /.-](\\d{4})\\b");

    private DniOcrParser() {}

    public static Result parse(String raw) {
        Result r = new Result();
        String text = raw == null ? "" : raw.replace('\r','\n');
        String upper = normalizeOcr(text);
        String[] lines = upper.split("\\n");

        r.surname = labeled(lines, "APELLIDOS", "APELLIDO");
        r.name = labeled(lines, "NOMBRE", "NOMBRES");
        r.nationality = labeled(lines, "NACIONALIDAD");
        r.sex = labeled(lines, "SEXO");
        r.address = labeledOrNext(lines, "DOMICILIO", "DIRECCION", "DIRECCIÓN");
        r.birthPlace = labeledOrNext(lines, "LUGAR DE NACIMIENTO");
        r.parents = labeledOrNext(lines, "HIJO/A DE", "HIJO DE");
        r.supportNumber = labeledOrNext(lines, "NUM SOPORTE", "Nº SOPORTE", "N° SOPORTE", "NUMERO SOPORTE");
        r.issueDate = dateAfterLabel(lines, "EMISION", "EMISIÓN", "FECHA DE EMISION", "FECHA DE EMISIÓN");
        r.validityDate = dateAfterLabel(lines, "VALIDEZ", "CADUCIDAD", "FECHA DE CADUCIDAD");

        // Primero buscamos el DNI/NIE junto a su etiqueta. Esto evita confundirlo
        // con números de soporte, expedición u otros números presentes en el documento.
        r.dni = identityAfterLabel(upper, "DNI", "NIE");
        if (r.dni.isEmpty()) r.dni = firstValidIdentity(upper);

        // La fecha de nacimiento también se busca primero junto a su etiqueta.
        r.birthDate = dateAfterText(upper, "FECHA DE NACIMIENTO", "NACIMIENTO", "NAC");
        if (r.birthDate.isEmpty()) {
            Matcher allDates = DATE.matcher(upper);
            while (allDates.find()) {
                String candidate = normalizeDate(allDates.group());
                if (!candidate.equals(r.issueDate) && !candidate.equals(r.validityDate)) {
                    r.birthDate = candidate;
                    break;
                }
            }
        }

        StringBuilder mrzLines = new StringBuilder();
        for (String line : lines) {
            String compact = compactMrz(line);
            if (compact.startsWith("IDESP") || compact.contains("IDESP") || compact.contains("<<")) {
                mrzLines.append(compact).append('\n');
            }
        }
        r.mrz = mrzLines.toString().trim();

        if (!r.mrz.isEmpty()) {
            Matcher mid = Pattern.compile("IDESP(?:C)?(?:ID)?([0-9]{8}[A-Z])").matcher(r.mrz);
            while (mid.find()) {
                String candidate = mid.group(1);
                if (isValidDni(candidate)) { r.dni = candidate; break; }
            }

            // La MRZ es la mejor fuente secundaria para nombres y nacimiento.
            Matcher names = Pattern.compile("([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)<<([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)").matcher(r.mrz);
            if (names.find()) {
                String s = names.group(1).replace('<',' ').trim().replaceAll("\\s+"," ");
                String n = names.group(2).replace('<',' ').trim().replaceAll("\\s+"," ");
                if (!s.isEmpty()) r.surname = s;
                if (!n.isEmpty()) r.name = n;
            }

            Matcher datesMrz = Pattern.compile("(\\d{6})\\d([MF<])(\\d{6})\\d").matcher(r.mrz.replace("<", ""));
            if (datesMrz.find()) {
                r.birthDate = mrzDate(datesMrz.group(1));
                if (!"<".equals(datesMrz.group(2))) r.sex = datesMrz.group(2);
                r.validityDate = mrzDate(datesMrz.group(3));
            } else {
                Matcher loose = Pattern.compile("(\\d{6})\\d?([MF])(\\d{6})").matcher(r.mrz.replace("<", ""));
                if (loose.find()) {
                    r.birthDate = mrzDate(loose.group(1));
                    r.sex = loose.group(2);
                    r.validityDate = mrzDate(loose.group(3));
                }
            }
            if (r.nationality.isEmpty() && r.mrz.contains("ESP")) r.nationality = "ESP";
        }

        if (!r.name.isEmpty() || !r.surname.isEmpty()) r.holder = (r.name + " " + r.surname).trim();
        if (r.holder.isEmpty()) {
            Matcher fallback = Pattern.compile("\\b([A-ZÁÉÍÓÚÑ]{3,})\\s+([A-ZÁÉÍÓÚÑ]{3,})\\s+([A-ZÁÉÍÓÚÑ]{3,})\\b").matcher(upper);
            if (fallback.find()) {
                r.surname = fallback.group(1) + " " + fallback.group(2);
                r.name = fallback.group(3);
                r.holder = r.name + " " + r.surname;
            }
        }

        int total=0;
        if(!r.dni.isEmpty()) total+=35;
        if(!r.name.isEmpty()) total+=25;
        if(!r.surname.isEmpty()) total+=20;
        if(!r.birthDate.isEmpty()) total+=20;
        r.confidence=Math.min(100,total);
        return r;
    }

    private static String identityAfterLabel(String text, String... labels) {
        for (String label : labels) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(label) + "\\b[^\\n]{0,24}?" + ID_SPACED.pattern().replace("(?<![A-Z0-9])", "").replace("(?![A-Z0-9])", ""), Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                String candidate = m.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
                if (isValidIdentity(candidate)) return candidate;
            }
        }
        return "";
    }

    private static String firstValidIdentity(String text) {
        Matcher m = ID_COMPACT.matcher(text);
        while (m.find()) {
            String candidate=m.group(1).toUpperCase(Locale.ROOT);
            if (isValidIdentity(candidate)) return candidate;
        }
        Matcher spaced = ID_SPACED.matcher(text);
        while (spaced.find()) {
            String candidate=spaced.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (isValidIdentity(candidate)) return candidate;
        }
        return "";
    }

    private static boolean isValidIdentity(String value) {
        return isValidDni(value) || isValidNie(value);
    }

    private static String dateAfterText(String text, String... labels) {
        for (String label : labels) {
            Pattern p = Pattern.compile(Pattern.quote(label) + "[^\\n]{0,40}?" + DATE.pattern(), Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                Matcher d = DATE.matcher(m.group());
                if (d.find()) return normalizeDate(d.group());
            }
        }
        return "";
    }

    private static String normalizeOcr(String s) {
        return s.toUpperCase(Locale.ROOT)
                .replace("N0MBRE", "NOMBRE")
                .replace("N0MBRES", "NOMBRES")
                .replace("APELLlDOS", "APELLIDOS")
                .replace("APELLlDO", "APELLIDO")
                .replace("NACIMlENTO", "NACIMIENTO")
                .replace("NACIMlENT0", "NACIMIENTO")
                .replace("VALlDEZ", "VALIDEZ")
                .replace("EMlSION", "EMISION")
                .replace("NAC10NALIDAD", "NACIONALIDAD")
                .replace("DOMIC1LIO", "DOMICILIO")
                .replace("DNI0", "DNI")
                .replace("DNI ", "DNI ");
    }

    private static String labeled(String[] lines, String... labels) { return labeledOrNext(lines, labels); }

    private static String labeledOrNext(String[] lines, String... labels) {
        for (int i=0;i<lines.length;i++) {
            String line=clean(lines[i]);
            for(String label:labels) {
                String lab=label.toUpperCase(Locale.ROOT);
                int p=line.indexOf(lab);
                if(p>=0) {
                    String v=line.substring(p+lab.length()).replaceFirst("^[ :.-]+","").trim();
                    if(!v.isEmpty()) return v;
                    if(i+1<lines.length) return clean(lines[i+1]);
                }
            }
        }
        return "";
    }

    private static String dateAfterLabel(String[] lines,String... labels){
        for(String l:labels){
            String v=labeledOrNext(lines,l);
            Matcher m=DATE.matcher(v);
            if(m.find()) return normalizeDate(m.group());
        }
        return "";
    }

    private static String compactMrz(String s){
        String x=s.toUpperCase(Locale.ROOT).replace(" ","").replace("–","-");
        x=x.replace("ID ESP","IDESP").replace("IDESP ","IDESP").replace("C I D","CID");
        return x.replaceAll("[^A-Z0-9<]","");
    }

    private static String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}
    private static String normalizeDate(String s){return s.replace('-','/').replace('.','/');}

    private static boolean isValidDni(String value) {
        if (value == null || !value.matches("\\d{8}[A-Z]")) return false;
        try { return DNI_LETTERS.charAt(Integer.parseInt(value.substring(0,8)) % 23) == value.charAt(8); }
        catch (Exception e) { return false; }
    }

    private static boolean isValidNie(String value) {
        if (value == null || !value.matches("[XYZ]\\d{7}[A-Z]")) return false;
        char prefix=value.charAt(0);
        String numeric=(prefix=='X'?"0":prefix=='Y'?"1":"2")+value.substring(1,8);
        try { return DNI_LETTERS.charAt(Integer.parseInt(numeric) % 23) == value.charAt(8); }
        catch (Exception e) { return false; }
    }

    private static String mrzDate(String yyMMdd){
        try{int yy=Integer.parseInt(yyMMdd.substring(0,2));int year=yy<=30?2000+yy:1900+yy;return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(yyMMdd.substring(4,6)),Integer.parseInt(yyMMdd.substring(2,4)),year);}catch(Exception e){return "";}
    }
}
