from pathlib import Path
import re

P = Path("app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java")
s = P.read_text(encoding="utf-8")

start = s.index("    private JSONObject parseEssential(String raw){")
end = s.index("    private String normalize(String s){", start)

new_parse = r'''    private JSONObject parseEssential(String raw){
        JSONObject x=new JSONObject();
        try{
            String u=normalize(raw);
            String[] lines=u.split("\\n");
            String surname=multiLabel(lines);
            String name=singleLabel(lines,"NOMBRE");
            String dni=findId(u);
            String birth=findBirth(u);
            String exp=findAfter(u,"VALIDEZ","VÁLIDEZ","CADUCIDAD","FECHA DE CADUCIDAD");

            String mrzSurname=mrzName(raw,false), mrzName=mrzName(raw,true);
            if(!mrzSurname.isEmpty()) surname=mrzSurname;
            if(!mrzName.isEmpty()) name=mrzName;
            if(dni.isEmpty()) dni=findId(u.replaceAll("[ \\t\\r\\n]+",""));
            if(birth.isEmpty()) birth=mrzDates(raw)[0];
            if(exp.isEmpty()) exp=mrzDates(raw)[1];

            x.put("name",clean(name));
            x.put("surname",clean(surname));
            x.put("identityNumber",dni);
            x.put("birthDate",birth);
            x.put("expiry",exp);
            int c=0;
            if(!dni.isEmpty()) c+=25;
            if(!name.isEmpty()) c+=20;
            if(!surname.isEmpty()) c+=25;
            if(!birth.isEmpty()) c+=15;
            if(!exp.isEmpty()) c+=15;
            x.put("confidence",c);
        }catch(Exception ignored){}
        return x;
    }
'''
s = s[:start] + new_parse + s[end:]

# Replace the normalization helper so common ML Kit/OCR substitutions do not break labels.
start = s.index("    private String normalize(String s){")
end = s.index("    private String clean(String s){", start)
new_norm = r'''    private String normalize(String s){
        return s.toUpperCase(Locale.ROOT)
                .replace('\\r','\\n')
                .replace("APELLID0S","APELLIDOS")
                .replace("APELLID0","APELLIDO")
                .replace("APELLlDOS","APELLIDOS")
                .replace("APELLlDO","APELLIDO")
                .replace("N0MBRE","NOMBRE")
                .replace("N0MBRES","NOMBRES")
                .replace("NACIMIENT0","NACIMIENTO")
                .replace("NACIMlENTO","NACIMIENTO")
                .replace("VALlDEZ","VALIDEZ")
                .replace("EMlSION","EMISION")
                .replace("NAC10NALIDAD","NACIONALIDAD")
                .replace("DOMIC1LIO","DOMICILIO");
    }
'''
s = s[:start] + new_norm + s[end:]

# Replace multiLabel with a bounded continuation parser: GARCIA + ABADIA becomes one surname.
start = s.index("    private String multiLabel(String[] lines){")
end = s.index("    private String findId(String u){", start)
new_multi = r'''    private String multiLabel(String[] lines){
        final String[] stop={"NOMBRE","SEXO","NACIONALIDAD","EMISION","VALIDEZ","CADUCIDAD","NACIMIENTO","NUM SOPORTE","DOMICILIO","LUGAR DE NACIMIENTO","HIJO/A DE","HIJO DE","FECHA DE"};
        for(int i=0;i<lines.length;i++){
            String l=clean(lines[i]);
            int p=l.indexOf("APELLIDOS");
            if(p<0) continue;
            StringBuilder out=new StringBuilder();
            String first=clean(l.substring(p+9).replaceFirst("^[ :.-]+",""));
            if(!first.isEmpty()) out.append(first);
            for(int j=i+1;j<Math.min(lines.length,i+4);j++){
                String n=clean(lines[j]);
                if(n.isEmpty()) continue;
                boolean stopHere=false;
                for(String k:stop) if(n.startsWith(k)||n.equals(k)){stopHere=true;break;}
                if(stopHere) break;
                if(n.matches("[A-ZÁÉÍÓÚÜÑ]+(?:[ -][A-ZÁÉÍÓÚÜÑ]+)*")){
                    if(out.length()>0) out.append(' ');
                    out.append(n);
                } else break;
            }
            return clean(out.toString());
        }
        return "";
    }
'''
s = s[:start] + new_multi + s[end:]

# Replace date helpers with context-first + historical-date fallback.
start = s.index("    private String findBirth(String u){")
end = s.index("    private List<String> dates(String u){", start)
new_birth = r'''    private String findBirth(String u){
        String direct=findAfter(u,"NACIMIENTO","FECHA DE NACIMIENTO");
        if(!direct.isEmpty()) return direct;
        List<String> ds=dates(u);
        String issue=findAfter(u,"EMISION","EMISIÓN","FECHA DE EMISION","FECHA DE EMISIÓN");
        String valid=findAfter(u,"VALIDEZ","VÁLIDEZ","CADUCIDAD","FECHA DE CADUCIDAD");
        int current=Calendar.getInstance().get(Calendar.YEAR);
        for(String d:ds){
            if(d.equals(issue)||d.equals(valid)) continue;
            try{
                Date dt=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).parse(d);
                Calendar c=Calendar.getInstance(); c.setTime(dt);
                int y=c.get(Calendar.YEAR);
                if(y>=1900 && y<=current-10) return d;
            }catch(Exception ignored){}
        }
        return "";
    }
    private String findAfter(String u,String... labs){
        for(String lab:labs){
            int p=u.indexOf(lab);
            if(p<0) continue;
            String tail=u.substring(p,Math.min(u.length(),p+260));
            Matcher m=Pattern.compile("(\\d{2})\\s*[./-]?\\s*(\\d{2})\\s*[./-]?\\s*(\\d{4})").matcher(tail);
            if(m.find()) return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
        }
        return "";
    }
'''
s = s[:start] + new_birth + s[end:]

P.write_text(s,encoding="utf-8")
print("Patched reliable front DNI OCR parsing")
