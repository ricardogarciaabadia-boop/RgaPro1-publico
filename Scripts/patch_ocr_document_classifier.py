from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivity.java")
s = MAIN.read_text(encoding="utf-8")

if "private String classifyPolicyTypeFinal(" not in s:
    marker = "    private void saveClient("
    helpers = r'''
    private String classifyPolicyTypeFinal(String raw,String selected){
        String t=selected==null?"":selected.trim();
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT);
        if(!t.isEmpty()&&!t.equalsIgnoreCase("Cliente")&&!t.equalsIgnoreCase("DNI")&&!t.equalsIgnoreCase("NIE")) return t.equalsIgnoreCase("Decesos")?"Deceso":t;
        if(u.matches("(?s).*\\b(DECESO|DECESOS|SEPELIO|FUNERAL|ASISTENCIA\\s+FAMILIAR)\\b.*")) return "Deceso";
        if(u.matches("(?s).*\\b(HOGAR|VIVIENDA|CONTINENTE|CONTENIDO|ROBO\\s+EN\\s+VIVIENDA)\\b.*")) return "Hogar";
        if(u.matches("(?s).*\\b(VIDA|FALLECIMIENTO|CAPITAL\\s+ASEGURADO\\s+VIDA)\\b.*")) return "Vida";
        if(u.matches("(?s).*\\b(ACCIDENTE|ACCIDENTES|INVALIDEZ\\s+ACCIDENTAL)\\b.*")) return "Accidente";
        if(u.matches("(?s).*\\b(PIAS|APORTE\\s+EXTRAORDINARIO|AHORRO|FLEXIBLE)\\b.*")) return "Ahorro";
        if(u.matches("(?s).*\\b(COMUNIDAD|COMUNIDADES|FINCA\\s+COMUNIDAD)\\b.*")) return "Comunidades";
        if(u.matches("(?s).*\\b(EMPRESA|PYME|NEGOCIO|COMERCIO)\\b.*")) return "Empresa";
        if(u.matches("(?s).*\\b(RESPONSABILIDAD\\s+CIVIL|RC\\s+GENERAL)\\b.*")) return "Responsabilidad civil";
        if(u.matches("(?s).*\\b(SALUD|ASISTENCIA\\s+SANITARIA|CUADRO\\s+MEDICO|CUADRO\\s+MÉDICO)\\b.*")) return "Salud";
        return t.isEmpty()?"Otros":t;
    }

    private boolean looksLikeDniDocumentFinal(String raw){
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT);
        boolean id=Pattern.compile("(?<![0-9])[0-9]{8}[A-Z](?![A-Z0-9])").matcher(u).find()
                ||Pattern.compile("(?<![A-Z0-9])[XYZ][0-9]{7}[A-Z](?![A-Z0-9])").matcher(u).find();
        boolean policy=Pattern.compile("(?s)\\b(PÓLIZA|POLIZA|TOMADOR|ASEGURADO|ASEGURADOS|PRIMA|COBERTURA|CAPITAL\\s+ASEGURADO|CONDICIONES\\s+PARTICULARES)\\b").matcher(u).find();
        return id && !policy;
    }

    private String labeledFinal(String raw,String... labels){
        String[] lines=(raw==null?"":raw.replace('\\r','\\n')).split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=lines[i].trim();
            String up=line.toUpperCase(Locale.ROOT);
            for(String label:labels){
                String lab=label.toUpperCase(Locale.ROOT);
                int p=up.indexOf(lab);
                if(p>=0){
                    String v=line.substring(Math.min(line.length(),p+lab.length())).replaceFirst("^[\\s:.-]+","").trim();
                    if(!v.isEmpty()) return v;
                    if(i+1<lines.length)return lines[i+1].trim();
                }
            }
        }
        return "";
    }

    private String extractPhoneFinal(String raw){
        Matcher m=Pattern.compile("(?<!\\d)(?:\\+34[\\s.-]?)?[6789]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}(?!\\d)").matcher(raw==null?"":raw);
        return m.find()?m.group().replaceAll("[\\s.-]",""):"";
    }

    private String extractEmailFinal(String raw){
        Matcher m=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",Pattern.CASE_INSENSITIVE).matcher(raw==null?"":raw);
        return m.find()?m.group():"";
    }

    private void sanitizeDniRecordFinal(JSONObject p,String raw){
        try{
            String name=p.optString("name","").trim();
            String surname=p.optString("surname","").trim();
            String dni=p.optString("identityNumber",p.optString("holderDni","")).trim().toUpperCase(Locale.ROOT);
            if(dni.isEmpty()){
                Matcher m=Pattern.compile("(?<![0-9])[0-9]{8}[A-Z](?![A-Z0-9])").matcher((raw==null?"":raw).toUpperCase(Locale.ROOT));
                if(m.find())dni=m.group();
            }
            if(name.isEmpty())name=labeledFinal(raw,"NOMBRE");
            if(surname.isEmpty())surname=labeledFinal(raw,"APELLIDOS");
            String address=p.optString("address","").trim();
            if(address.isEmpty())address=labeledFinal(raw,"DOMICILIO","DIRECCIÓN","DIRECCION");
            String phone=p.optString("phone","").trim(); if(phone.isEmpty())phone=extractPhoneFinal(raw);
            String email=p.optString("email","").trim(); if(email.isEmpty())email=extractEmailFinal(raw);
            p.put("name",name);p.put("surname",surname);p.put("holder",(name+" "+surname).trim());
            p.put("identityNumber",dni);p.put("holderDni",dni);p.put("address",address);p.put("phone",phone);p.put("email",email);
            p.put("documentKind","DNI");
            String[] remove={"birthDate","nationality","sex","birthPlace","parents","supportNumber","issueDate","validityDate","expiry","cif","products","insureds","product","ahorroModalidad"};
            for(String k:remove)p.remove(k);
        }catch(Exception ignored){}
    }

    private JSONObject parsePolicyPersonFinal(String value){
        JSONObject p=new JSONObject();
        try{
            String x=value==null?"":value.replaceFirst("^[\\-•·:*]+\\s*","").trim();
            if(x.length()<2)return p;
            Matcher em=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",Pattern.CASE_INSENSITIVE).matcher(x);
            if(em.find()){p.put("email",em.group());x=x.substring(0,em.start())+" "+x.substring(em.end());}
            Matcher ph=Pattern.compile("(?<!\\d)(?:\\+34[\\s.-]?)?[6789]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}(?!\\d)").matcher(x);
            if(ph.find()){p.put("phone",ph.group().replaceAll("[\\s.-]",""));x=x.substring(0,ph.start())+" "+x.substring(ph.end());}
            Matcher id=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b",Pattern.CASE_INSENSITIVE).matcher(x);
            if(id.find()){p.put("identityNumber",id.group().toUpperCase(Locale.ROOT));x=x.substring(0,id.start())+" "+x.substring(id.end());}
            Matcher date=Pattern.compile("\\b\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4}\\b").matcher(x);
            if(date.find()){p.put("birthDate",date.group());x=x.substring(0,date.start())+" "+x.substring(date.end());}
            String address=labeledFinal(x,"DOMICILIO","DIRECCIÓN","DIRECCION");
            if(!address.isEmpty()){p.put("address",address);x=x.replaceFirst("(?i)DOMICILIO\\s*[:.-]?\\s*"+Pattern.quote(address),"");}
            x=x.replaceAll("\\s+"," ").trim();
            if(x.length()<2)return p;
            p.put("name",x);p.put("role","ASEGURADO");
        }catch(Exception ignored){}
        return p;
    }

    private JSONArray extractPolicyPeopleFinal(String raw,String holder){
        JSONArray out=new JSONArray();
        String text=raw==null?"":raw.replace('\\r','\\n');
        Matcher sec=Pattern.compile("(?is)(?:PERSONAS\\s+ASEGURADAS|ASEGURADOS?|ASEGURADAS?)\\s*[:\\-]?\\s*(.*?)(?=\\n\\s*(?:TOMADOR(?:A)?|CONTRATANTE|BENEFICIARIO(?:S)?|PRIMA|RECIBO|FORMA\\s+DE\\s+PAGO|DATOS\\s+DE\\s+LA\\s+P[ÓO]LIZA|OBSERVACIONES)\\b|$)").matcher(text);
        String block=sec.find()?sec.group(1):"";
        if(block.trim().isEmpty())block=text;
        for(String line:block.split("\\n|;")){
            String x=line.trim();
            if(x.length()<3||x.length()>220)continue;
            String u=x.toUpperCase(Locale.ROOT);
            if(u.matches(".*\\b(?:TOMADOR|CONTRATANTE|ASEGURADORA|P[ÓO]LIZA|PRIMA|RECIBO|BENEFICIARIO|COBERTURA|CONDICIONES|DOMICILIO\\s+DE\\s+LA\\s+ASEGURADORA)\\b.*"))continue;
            JSONObject p=parsePolicyPersonFinal(x);
            if(p.optString("name","").isEmpty())continue;
            boolean dup=false;String key=personKey(p);
            for(int i=0;i<out.length();i++){JSONObject old=out.optJSONObject(i);if(old!=null&&!key.isEmpty()&&key.equals(personKey(old))){dup=true;break;}}
            if(!dup)out.put(p);
        }
        if(out.length()==0&&!holder.trim().isEmpty()){
            JSONObject p=new JSONObject();try{p.put("name",holder.trim());p.put("role","ASEGURADO");out.put(p);}catch(Exception ignored){}
        }
        return out;
    }

'''
    if marker not in s:
        raise SystemExit("saveClient marker not found")
    s = s.replace(marker, helpers + marker, 1)

if "DNI workflow: only Nombre" not in s:
    pos = s.find("EditText holder=edit(")
    if pos < 0:
        raise SystemExit("OCR form field declaration not found")
    end = s.find("new AlertDialog.Builder(this)", pos)
    if end < 0:
        raise SystemExit("OCR dialog builder not found")
    hide = r'''
        // DNI workflow: only Nombre, Apellidos, DNI, Dirección, Teléfono y Email.
        if(dniMode){cif.setVisibility(View.GONE);birth.setVisibility(View.GONE);nationality.setVisibility(View.GONE);sex.setVisibility(View.GONE);birthPlace.setVisibility(View.GONE);parents.setVisibility(View.GONE);support.setVisibility(View.GONE);issue.setVisibility(View.GONE);validity.setVisibility(View.GONE);}
        '''
    s = s[:end] + hide + s[end:]

needle = 'save(a);updatePeopleIndex(p);'
if needle in s and 'sanitizeDniRecordFinal(p,raw);' not in s:
    repl = r'''boolean dniDocument=dniMode||looksLikeDniDocumentFinal(raw);
            p.put("documentKind",dniDocument?"DNI":"POLIZA");
            if(dniDocument){sanitizeDniRecordFinal(p,raw);}
            else{
                p.put("type",classifyPolicyTypeFinal(raw,p.optString("type","")));
                if("Deceso".equalsIgnoreCase(p.optString("type",""))){
                    JSONArray richer=extractPolicyPeopleFinal(raw,p.optString("holder",""));
                    if(richer.length()>0)p.put("insureds",richer);
                }
            }
            save(a);updatePeopleIndex(p);'''
    s = s.replace(needle, repl, 1)

if 'if("DNI".equalsIgnoreCase(policy.optString("documentKind","")))return;' not in s:
    target='    private void updatePeopleIndex(JSONObject policy){'
    s=s.replace(target,target+'if("DNI".equalsIgnoreCase(policy.optString("documentKind","")))return;',1)

MAIN.write_text(s,encoding="utf-8")
print("OCR classifier + strict DNI fields + richer policy insured extraction applied")
