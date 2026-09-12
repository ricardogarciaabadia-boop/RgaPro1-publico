from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s=P.read_text(encoding='utf-8')
def replace_method(src,sig,new):
    a=src.find(sig)
    if a<0: raise SystemExit('method not found: '+sig)
    b=src.find('{',a);d=0
    for i in range(b,len(src)):
        if src[i]=='{': d+=1
        elif src[i]=='}':
            d-=1
            if d==0:return src[:a]+new+src[i+1:]
    raise SystemExit('unbalanced method')
classify=r'''    private static String classifyProduct(String text){
        int decesos=score(text,"DECESOS",100)+score(text,"ASISTENCIA FAMILIAR",50)+score(text,"SERVICIO FUNERARIO",40);
        int vida=score(text,"SEGURO DE VIDA",80)+score(text,"VIDA",20)+score(text,"FALLECIMIENTO",20);
        int accidente=score(text,"ACCIDENTES",70)+score(text,"ACCIDENTE",30);
        int hogar=score(text,"OCASO HOGAR",140)+score(text,"HOGAR SENIOR",140)+score(text,"MULTIRRIESGO HOGAR",120)+score(text,"HOGAR",70)+score(text,"CONTINENTE",35)+score(text,"CONTENIDO",35)+score(text,"DATOS DE LA VIVIENDA",50)+score(text,"RIESGO/S ASEGURADO/S",20);
        int salud=score(text,"ASISTENCIA SANITARIA",80)+score(text,"SEGURO DE SALUD",70)+score(text,"SALUD",30);
        int auto=score(text,"AUTOMOVIL",80)+score(text,"AUTOMÓVIL",80)+score(text,"VEHICULO",30)+score(text,"VEHÍCULO",30);
        int ahorro=score(text,"AHORRO",60)+score(text,"PIAS",60)+score(text,"RENTA",20);
        int comunidad=score(text,"COMUNIDADES",70)+score(text,"COMUNIDAD",30);
        int rc=score(text,"RESPONSABILIDAD CIVIL",90);
        int max=Math.max(Math.max(Math.max(decesos,vida),Math.max(accidente,hogar)),Math.max(Math.max(salud,auto),Math.max(Math.max(ahorro,comunidad),rc)));
        if(hogar>0 && hogar>=max-1)return "Hogar";
        if(max==decesos&&decesos>0)return "Decesos"; if(max==vida&&vida>0)return "Vida"; if(max==accidente&&accidente>0)return "Accidentes"; if(max==salud&&salud>0)return "Salud"; if(max==auto&&auto>0)return "Auto"; if(max==ahorro&&ahorro>0)return "Ahorro"; if(max==comunidad&&comunidad>0)return "Comunidades"; if(max==rc&&rc>0)return "Responsabilidad civil"; return "Otros";
    }
'''
s=replace_method(s,'    private static String classifyProduct(String text){',classify)
identity=r'''    private static String extractIdentity(String text,String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)b=new int[]{0,lines.length};
        String[] labels={"DOC. ID","DOC ID","DOCUMENTO DE IDENTIDAD","DOCUMENTO","DNI","NIE","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR"};
        ArrayList<Candidate> c=new ArrayList<>();
        for(int i=b[0];i<b[1];i++){
            String line=lines[i]; String u=line.replace(".","").replace(":","").toUpperCase(Locale.ROOT);
            boolean near=false;for(String l:labels)if(u.contains(l.replace(".",""))){near=true;break;}
            if(near)for(int j=i;j<Math.min(i+4,b[1]);j++)addIdentityCandidates(c,lines[j],j,labels);
        }
        Collections.sort(c,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
        for(Candidate x:c)if(isValidIdentity(x.value))return x.value;
        // Ocaso commonly prints the holder ID as DOC. ID.: 12345678X.
        Pattern docId=Pattern.compile("(?i)DOC\\s*\\.?\\s*ID\\s*\\.?\\s*[:.-]?\\s*([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])");
        for(String line:lines){
            Matcher dm=docId.matcher(line.toUpperCase(Locale.ROOT));
            while(dm.find())if(isValidIdentity(dm.group(1)))return dm.group(1);
            addIdentityCandidates(c,line,0,labels);
        }
        for(Candidate x:c)if(isValidIdentity(x.value))return x.value;
        return "";
    }
'''
s=replace_method(s,'    private static String extractIdentity(String text,String[] lines){',identity)
add=r'''    private static void addIdentityCandidates(ArrayList<Candidate> out,String source,int lineIndex,String... labels){
        String src=source==null?"":source.toUpperCase(Locale.ROOT).replace("O","0").replace("I","1").replace("L","1");
        Matcher m=DNI.matcher(src);while(m.find())out.add(identityCandidate(m.group(),source,lineIndex,labels));
        m=DNI_SPACED.matcher(src);while(m.find())out.add(identityCandidate(m.group(1)+m.group(2)+m.group(3),source,lineIndex,labels));
        Matcher bad=Pattern.compile("(?<![0-9])(\\d{8})[0-9](?![0-9])").matcher(src.replaceAll("[ .]",""));
        while(bad.find()){String digits=bad.group(1);String value=digits+DNI_LETTERS.charAt(Integer.parseInt(digits)%23);out.add(identityCandidate(value,source,lineIndex,labels));}
    }
'''
s=replace_method(s,'    private static void addIdentityCandidates(ArrayList<Candidate> out,String source,int lineIndex,String... labels){',add)

anchor='''    private static String extractMoney(String[] lines,String[] labels){'''
methods=r'''    private static String extractPostalCode(String[] lines){
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);int p=u.indexOf("CODIGO POSTAL");if(p<0)p=u.indexOf("CÓDIGO POSTAL");if(p>=0){Matcher m=Pattern.compile("(?<![0-9])([0-9]{5})(?![0-9])").matcher(line.substring(p));if(m.find())return m.group(1);}}
        for(String line:lines){Matcher m=Pattern.compile("(?<![0-9])([0-9]{5})\\s+(?:GIBRALEON|HUELVA)").matcher(line);if(m.find())return m.group(1);}
        return "";
    }
    private static String extractIssueDate(String[] lines){for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("FECHA:")||u.contains("FECHA DE EMISION")||u.contains("FECHA DE EMISIÓN")||u.contains("EMITIDO EN")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}return "";}
    private static String extractEffectiveDate(String[] lines){for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("FECHA DE EFECTO")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("DESDE LAS")||u.contains("DESDE")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}return "";}
    private static String extractExpiryDate(String[] lines){for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("HASTA LAS")||u.contains("HASTA")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}return "";}
'''
s=s.replace(anchor,methods+anchor,1)

parse_anchor='''            out.put("phone",extractPhone(text,lines)); out.put("email",extractEmail(text,lines));'''
parse_new='''            out.put("phone",extractPhone(text,lines)); out.put("email",extractEmail(text,lines));
            out.put("postalCode",extractPostalCode(lines)); out.put("issueDate",extractIssueDate(lines));
            out.put("effectiveDate",extractEffectiveDate(lines)); out.put("expiryDate",extractExpiryDate(lines));'''
if parse_anchor not in s: raise SystemExit('parse field anchor not found')
s=s.replace(parse_anchor,parse_new,1)
P.write_text(s,encoding='utf-8')

M=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
m=M.read_text(encoding='utf-8')
old='''        addPolicyField("DIRECCIÓN",null,policyAddressE);\n        addPolicyField("TELÉFONO",null,policyPhoneE);\n\n        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);'''
new='''        addPolicyField("DIRECCIÓN",null,policyAddressE);
        policyPostalE=inputWithValue("Código postal",p.optString("postalCode",""));
        policyIssueE=inputWithValue("Fecha de emisión",p.optString("issueDate",""));
        policyEffectiveE=inputWithValue("Fecha de efecto",p.optString("effectiveDate",""));
        policyExpiryE=inputWithValue("Fecha de vencimiento",p.optString("expiryDate",""));
        addPolicyField("CÓDIGO POSTAL",null,policyPostalE);
        addPolicyField("TELÉFONO",null,policyPhoneE);
        addPolicyField("FECHA DE EMISIÓN",null,policyIssueE);
        addPolicyField("FECHA DE EFECTO / VIGENCIA",null,policyEffectiveE);
        addPolicyField("FECHA DE VENCIMIENTO",null,policyExpiryE);
        addPolicyField("TOTAL RECIBO",null,receiptE);

        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);'''
if old in m:m=m.replace(old,new,1)
old='''    private void addPolicyField(String label,String value,EditText field){'''
new='''    private EditText inputWithValue(String hint,String value){EditText e=input(hint);e.setText(value==null?"":value);return e;}

    private void addPolicyField(String label,String value,EditText field){'''
if old in m:m=m.replace(old,new,1)
old='''            if(!id.isEmpty())c.put("identityNumber",id); if(!address.isEmpty())c.put("address",address); if(!phone.isEmpty())c.put("phone",phone); if(!email.isEmpty())c.put("email",email);'''
new='''            if(!id.isEmpty())c.put("identityNumber",id); if(!address.isEmpty())c.put("address",address); if(!phone.isEmpty())c.put("phone",phone); if(!email.isEmpty())c.put("email",email);
            c.put("postalCode",policyPostalE==null?parsed.optString("postalCode",""):policyPostalE.getText().toString().trim());
            c.put("issueDate",policyIssueE==null?parsed.optString("issueDate",""):policyIssueE.getText().toString().trim());
            c.put("effectiveDate",policyEffectiveE==null?parsed.optString("effectiveDate",""):policyEffectiveE.getText().toString().trim());
            c.put("expiry",policyExpiryE==null?parsed.optString("expiryDate",""):policyExpiryE.getText().toString().trim());'''
if old in m:m=m.replace(old,new,1)
old='''            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holder);pol.put("identityNumber",id);pol.put("address",address);pol.put("phone",phone);pol.put("email",email);pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());'''
new='''            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holder);pol.put("identityNumber",id);pol.put("address",address);pol.put("phone",phone);pol.put("email",email);pol.put("postalCode",policyPostalE==null?parsed.optString("postalCode",""):policyPostalE.getText().toString().trim());pol.put("issueDate",policyIssueE==null?parsed.optString("issueDate",""):policyIssueE.getText().toString().trim());pol.put("effectiveDate",policyEffectiveE==null?parsed.optString("effectiveDate",""):policyEffectiveE.getText().toString().trim());pol.put("expiryDate",policyExpiryE==null?parsed.optString("expiryDate",""):policyExpiryE.getText().toString().trim());pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());'''
if old in m:m=m.replace(old,new,1)
old='private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,receiptE,capitalE,decesosE,decesosLeveladaE;'
new='private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,policyPostalE,policyIssueE,receiptE,capitalE,decesosE,decesosLeveladaE;'
if old in m:m=m.replace(old,new,1)
M.write_text(m,encoding='utf-8')
print('policy parser v5 + robust DOC. ID fallback + policy review fields applied')
