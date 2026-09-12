from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s=p.read_text()
start=s.index('    private static String extractHolder(')
end=s.index('    private static String normalize(', start)
new=r'''    private static String extractHolder(String[] lines){
        int start=findSectionStart(lines,"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO");
        if(start>=0){
            int end=sectionEnd(lines,start,"RELACION DE ASEGURADOS","RELACIÓN DE ASEGURADOS","RELACION DE ASEGURADOS:","DOMICILIO DE COBRO");
            for(int i=start;i<end;i++){
                String line=lines[i];
                for(String label:new String[]{"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO"}){
                    int pos=line.indexOf(label); if(pos<0) continue;
                    String v=stripIdentityAndNoise(line.substring(pos+label.length()));
                    if(isCompanyOrOffice(v)) continue;
                    if(isPlausiblePerson(v)) return v;
                    if(i+1<end){v=stripIdentityAndNoise(lines[i+1]); if(!isCompanyOrOffice(v)&&isPlausiblePerson(v)) return v;}
                }
            }
        }
        for(int i=0;i<lines.length;i++) for(String label:new String[]{"TOMADOR/A","TOMADOR","CONTRATANTE"}){
            int pos=lines[i].indexOf(label); if(pos<0) continue;
            String v=stripIdentityAndNoise(lines[i].substring(pos+label.length()));
            if(!isCompanyOrOffice(v)&&isPlausiblePerson(v)) return v;
        }
        return "";
    }

    private static String extractAddress(String[] lines){
        int start=findSectionStart(lines,"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO");
        if(start>=0){
            int end=sectionEnd(lines,start,"RELACION DE ASEGURADOS","RELACIÓN DE ASEGURADOS","DOMICILIO DE COBRO");
            for(int i=start;i<end;i++){
                String line=lines[i];
                for(String label:new String[]{"DOMICILIO DEL TOMADOR","DIRECCION DEL TOMADOR","DIRECCIÓN DEL TOMADOR","DOMICILIO","DIRECCION","DIRECCIÓN"}){
                    int pos=line.indexOf(label); if(pos<0) continue;
                    String v=cutAtNextLabel(clean(line.substring(pos+label.length())));
                    if(looksLikeAddress(v)&&!isOcasoAddress(v)) return v;
                    if(i+1<end){v=cutAtNextLabel(clean(lines[i+1])); if(looksLikeAddress(v)&&!isOcasoAddress(v)) return v;}
                }
            }
        }
        return "";
    }

    private static String extractIdentity(String text,String[] lines){
        int start=findSectionStart(lines,"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO");
        if(start>=0){
            int end=sectionEnd(lines,start,"RELACION DE ASEGURADOS","RELACIÓN DE ASEGURADOS");
            ArrayList<Candidate> local=new ArrayList<>();
            for(int i=start;i<end;i++) addIdentityCandidates(local,lines[i],i-start,"DNI","NIE","DOC ID","DOCUMENTO","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR");
            Collections.sort(local,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
            for(Candidate c:local) if(isValidIdentity(c.value)) return c.value;
        }
        ArrayList<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<lines.length;i++)addIdentityCandidates(candidates,lines[i],i,"DNI","NIE","DOC ID","DOCUMENTO","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR");
        addIdentityCandidates(candidates,text,0,"");
        Collections.sort(candidates,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
        for(Candidate c:candidates)if(isValidIdentity(c.value))return c.value;
        return candidates.isEmpty()?"":candidates.get(0).value;
    }

    private static String extractPhone(String text,String[] lines){
        int start=findSectionStart(lines,"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO");
        if(start>=0){int end=sectionEnd(lines,start,"RELACION DE ASEGURADOS","RELACIÓN DE ASEGURADOS");for(int i=start;i<end;i++){String u=lines[i].toUpperCase(Locale.ROOT);if(u.contains("TELEFONO")||u.contains("TELÉFONO")||u.contains("MOVIL")||u.contains("MÓVIL")||u.contains("TEL")){Matcher m=PHONE.matcher(lines[i].replaceAll("[ .-]",""));if(m.find())return m.group(1);}}}
        return "";
    }
    private static String extractEmail(String text,String[] lines){
        int start=findSectionStart(lines,"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO");
        if(start>=0){int end=sectionEnd(lines,start,"RELACION DE ASEGURADOS","RELACIÓN DE ASEGURADOS");for(int i=start;i<end;i++){Matcher m=EMAIL.matcher(lines[i]);if(m.find())return m.group();}}
        return "";
    }

    private static JSONArray insured(String[] lines,boolean decesos){
        JSONArray result=new JSONArray(); if(!decesos)return result; Set<String> seen=new HashSet<>();
        int start=findSectionStart(lines,"RELACION DE ASEGURADOS","RELACIÓN DE ASEGURADOS");
        if(start<0)return result;
        int end=sectionEnd(lines,start,"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO","PRIMAS","COBERTURAS");
        for(int i=start;i<end;i++){
            ArrayList<String> ids=identityValues(lines[i]); if(ids.isEmpty())continue; String id=ids.get(0); if(seen.contains(id))continue;
            String name=personAround(lines,i,start,end),birth=dateAround(lines,i,start,end); if(name.isEmpty()&&birth.isEmpty())continue;
            try{JSONObject x=new JSONObject();x.put("name",name);x.put("identityNumber",id);x.put("birthDate",birth);result.put(x);seen.add(id);}catch(Exception ignored){}
        }
        return result;
    }

    private static String personAround(String[] lines,int i,int start,int end){
        int[] order={i,i-1,i+1};
        for(int idx:order){if(idx<start||idx>=end)continue;String v=clean(lines[idx]).replaceAll("(?i)(DNI|NIE|DOC\\.? ID|DOCUMENTO)[ :.-]*[0-9A-Z]+","");v=cutAtNextLabel(v);if(isPlausiblePerson(v))return v;}
        return "";
    }

    private static String dateAround(String[] lines,int i,int start,int end){for(int idx:new int[]{i,i-1,i+1}){if(idx<start||idx>=end)continue;Matcher m=DATE.matcher(lines[idx]);while(m.find()){String d=normalizeDate(m.group());if(validDate(d))return d;}}return "";}

    private static int findSectionStart(String[] lines,String... labels){for(int i=0;i<lines.length;i++){String u=lines[i].toUpperCase(Locale.ROOT);for(String label:labels)if(u.contains(label))return i;}return -1;}
    private static int sectionEnd(String[] lines,int start,String... labels){for(int i=start+1;i<lines.length;i++){String u=lines[i].toUpperCase(Locale.ROOT);for(String label:labels)if(u.contains(label))return i;}return lines.length;}
    private static boolean isCompanyOrOffice(String s){String u=clean(s).toUpperCase(Locale.ROOT);return u.contains("OCASO")||u.contains("S.A.")||u.contains("SUCURSAL")||u.contains("OFICINA")||u.contains("ASEGURADORA");}
    private static boolean isOcasoAddress(String s){String u=clean(s).toUpperCase(Locale.ROOT);return (u.contains("PRINCESA")&&u.contains("MADRID"))||u.contains("PASEO DE LA CASTELLANA")||u.contains("OCASO");}

'''
s=s[:start]+new+s[end:]
p.write_text(s)

p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=p.read_text()
s=s.replace('private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,receiptE,capitalE,decesosE,decesosLeveladaE;', 'private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,receiptE,capitalE,decesosE,decesosLeveladaE;')
s=s.replace('addRead(l,"Email",p.optString("email",""));', 'addRead(l,"Email",p.optString("email",""));\n        addRead(l,"Fecha de efecto",p.optString("effectiveDate",""));\n        addRead(l,"Fecha de vencimiento",p.optString("expiryDate",""));')
s=s.replace('policyEmailE=input("Email");\n        receiptE=', 'policyEmailE=input("Email");\n        policyEffectiveE=input("Fecha de efecto");\n        policyExpiryE=input("Fecha de vencimiento");\n        receiptE=')
s=s.replace('policyEmailE.setText(p.optString("email",""));\n        receiptE=', 'policyEmailE.setText(p.optString("email",""));\n        policyEffectiveE.setText(p.optString("effectiveDate",""));\n        policyExpiryE.setText(p.optString("expiryDate",""));\n        receiptE=')
s=s.replace('addPolicyField("TELÉFONO",null,policyPhoneE);', 'addPolicyField("TELÉFONO",null,policyPhoneE);\n        addPolicyField("EMAIL",null,policyEmailE);\n        addPolicyField("FECHA DE EFECTO",null,policyEffectiveE);\n        addPolicyField("FECHA DE VENCIMIENTO",null,policyExpiryE);\n        addPolicyField("NÚMERO DE PÓLIZA",null,policyNumberE);')
s=s.replace('pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);', 'pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);\n            pol.put("effectiveDate",policyEffectiveE.getText().toString().trim());\n            pol.put("expiryDate",policyExpiryE.getText().toString().trim());')
p.write_text(s)
