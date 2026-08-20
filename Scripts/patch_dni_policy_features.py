from pathlib import Path
import re

main=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=main.read_text(encoding='utf-8')
# Keep the requested DNI fields, now including date of birth.
s=s.replace('for(String k:new String[]{"birthDate","nationality","sex","birthPlace","parents","supportNumber","issueDate"})p.remove(k);','for(String k:new String[]{"nationality","sex","birthPlace","parents","supportNumber","issueDate"})p.remove(k);')
if 'String birthDate=p.optString("birthDate","").trim();' not in s:
    s=s.replace('String dni=p.optString("identityNumber",p.optString("holderDni","")).trim().toUpperCase(Locale.ROOT);','String dni=p.optString("identityNumber",p.optString("holderDni","")).trim().toUpperCase(Locale.ROOT);String birthDate=p.optString("birthDate","").trim();')
if 'if(birthDate.isEmpty())' not in s:
    needle='String raw=p.optString("ocrText","");'
    repl='String raw=p.optString("ocrText","");\n            if(birthDate.isEmpty()){Matcher m=Pattern.compile("(?im)^(?:FECHA\\\\s+DE\\\\s+NACIMIENTO|NACIMIENTO)\\\\s*[:.-]?\\\\s*(\\\\d{1,2}[\\\\s./-]\\\\d{1,2}[\\\\s./-]\\\\d{2,4})\\\\s*$").matcher(raw);if(m.find())birthDate=m.group(1).replace(".","/").replace("-","/").trim();}'
    s=s.replace(needle,repl)
if 'if(!birthDate.isEmpty())p.put("birthDate",birthDate);' not in s:
    s=s.replace('if(!dni.isEmpty()){p.put("identityNumber",dni);p.put("holderDni",dni);}','if(!dni.isEmpty()){p.put("identityNumber",dni);p.put("holderDni",dni);}\n            if(!birthDate.isEmpty())p.put("birthDate",birthDate);')
main.write_text(s,encoding='utf-8')
