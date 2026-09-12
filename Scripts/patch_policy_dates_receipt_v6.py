from pathlib import Path

P=Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s=P.read_text(encoding='utf-8')

# Add structured policy fields to parse().
old='''            out.put("identityNumber",extractIdentity(text,lines)); out.put("address",extractAddress(lines));\n            out.put("phone",extractPhone(text,lines)); out.put("email",extractEmail(text,lines));\n            out.put("receipt",extractMoney(lines,new String[]{"TOTAL DEL RECIBO","TOTAL RECIBO","IMPORTE TOTAL","PRIMA TOTAL","RECIBO","PRIMA"}));'''
new='''            out.put("identityNumber",extractIdentity(text,lines)); out.put("address",extractAddress(lines));\n            out.put("phone",extractPhone(text,lines)); out.put("email",extractEmail(text,lines));\n            out.put("postalCode",extractPostalCode(lines));\n            out.put("issueDate",extractIssueDate(lines));\n            out.put("effectiveDate",extractEffectiveDate(lines));\n            out.put("expiryDate",extractExpiryDate(lines));\n            out.put("receipt",extractMoney(lines,new String[]{"TOTAL DEL RECIBO","TOTAL RECIBO","IMPORTE TOTAL","PRIMA TOTAL","RECIBO","PRIMA"}));'''
if old not in s: raise SystemExit('parse anchor not found')
s=s.replace(old,new,1)

anchor='''    private static String extractMoney(String[] lines,String[] labels){for(String line:lines){String n=line.replaceAll("(?<=\\\\d)\\\\s+(?=\\\\d)","");for(String label:labels){int p=n.indexOf(label);if(p<0)continue;Matcher m=MONEY.matcher(n.substring(p+label.length()));if(m.find()){String v=normalizeMoney(m.group(1));if(isPlausibleMoney(v))return v;}}}return "";}\n'''
if anchor not in s: raise SystemExit('money anchor not found')
methods=r'''    private static String extractPostalCode(String[] lines){
        for(String line:lines){
            String u=line.toUpperCase(Locale.ROOT);
            int p=u.indexOf("CODIGO POSTAL"); if(p<0)p=u.indexOf("CÓDIGO POSTAL");
            if(p>=0){Matcher m=Pattern.compile("(?<![0-9])([0-9]{5})(?![0-9])").matcher(line.substring(p));if(m.find())return m.group(1);}
        }
        for(String line:lines){Matcher m=Pattern.compile("(?<![0-9])([0-9]{5})\\s+(?:GIBRALEON|HUELVA)").matcher(line);if(m.find())return m.group(1);}
        return "";
    }
    private static String extractIssueDate(String[] lines){
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("FECHA:")||u.contains("FECHA DE EMISION")||u.contains("FECHA DE EMISIÓN")||u.contains("EMITIDO EN")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}
        return "";
    }
    private static String extractEffectiveDate(String[] lines){
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("FECHA DE EFECTO")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("DESDE LAS")||u.contains("DESDE")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}
        return "";
    }
    private static String extractExpiryDate(String[] lines){
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("HASTA LAS")||u.contains("HASTA")){Matcher m=DATE.matcher(line);if(m.find())return normalizeDate(m.group());}}
        return "";
    }
'''
s=s.replace(anchor,anchor+methods,1)
P.write_text(s,encoding='utf-8')

M=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
m=M.read_text(encoding='utf-8')

# Show all important policy fields in the review screen.
old='''        addPolicyField("DIRECCIÓN",null,policyAddressE);\n        addPolicyField("TELÉFONO",null,policyPhoneE);\n\n        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);'''
new='''        addPolicyField("DIRECCIÓN",null,policyAddressE);\n        addPolicyField("CÓDIGO POSTAL",null,inputWithValue("Código postal",p.optString("postalCode","")));\n        addPolicyField("TELÉFONO",null,policyPhoneE);\n        addPolicyField("FECHA DE EMISIÓN",null,inputWithValue("Fecha de emisión",p.optString("issueDate","")));\n        policyEffectiveE=inputWithValue("Fecha de efecto",p.optString("effectiveDate",""));\n        policyExpiryE=inputWithValue("Fecha de vencimiento",p.optString("expiryDate",""));\n        addPolicyField("FECHA DE EFECTO / VIGENCIA",null,policyEffectiveE);\n        addPolicyField("FECHA DE VENCIMIENTO",null,policyExpiryE);\n        addPolicyField("TOTAL RECIBO",null,receiptE);\n\n        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);'''
if old not in m: raise SystemExit('review anchor not found')
m=m.replace(old,new,1)

# The postal code needs to remain editable and save; create a field holder for it.
field_decl='private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,receiptE,capitalE,decesosE,decesosLeveladaE;'
field_new='private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,policyPostalE,policyIssueE,receiptE,capitalE,decesosE,decesosLeveladaE;'
if field_decl not in m: raise SystemExit('field decl not found')
m=m.replace(field_decl,field_new,1)

# Replace inline postal/issue fields with persistent fields.
old='''        addPolicyField("CÓDIGO POSTAL",null,inputWithValue("Código postal",p.optString("postalCode","")));'''
new='''        policyPostalE=inputWithValue("Código postal",p.optString("postalCode",""));\n        policyIssueE=inputWithValue("Fecha de emisión",p.optString("issueDate",""));\n        addPolicyField("CÓDIGO POSTAL",null,policyPostalE);'''
m=m.replace(old,new,1)
old='''        addPolicyField("FECHA DE EMISIÓN",null,inputWithValue("Fecha de emisión",p.optString("issueDate","")));'''
new='''        addPolicyField("FECHA DE EMISIÓN",null,policyIssueE);'''
m=m.replace(old,new,1)

# Add helper for prefilled editable fields before addPolicyField.
anchor2='''    private void addPolicyField(String label,String value,EditText field){'''
helper='''    private EditText inputWithValue(String hint,String value){EditText e=input(hint);e.setText(value==null?"":value);return e;}\n\n'''
if anchor2 not in m: raise SystemExit('addPolicyField anchor not found')
m=m.replace(anchor2,helper+anchor2,1)

# Save structured fields on the policy and on the client for expiry searches.
old='''            if(!id.isEmpty())c.put("identityNumber",id); if(!address.isEmpty())c.put("address",address); if(!phone.isEmpty())c.put("phone",phone); if(!email.isEmpty())c.put("email",email);'''
new='''            if(!id.isEmpty())c.put("identityNumber",id); if(!address.isEmpty())c.put("address",address); if(!phone.isEmpty())c.put("phone",phone); if(!email.isEmpty())c.put("email",email);\n            c.put("postalCode",policyPostalE==null?parsed.optString("postalCode",""):policyPostalE.getText().toString().trim());\n            c.put("issueDate",policyIssueE==null?parsed.optString("issueDate",""):policyIssueE.getText().toString().trim());\n            c.put("effectiveDate",policyEffectiveE==null?parsed.optString("effectiveDate",""):policyEffectiveE.getText().toString().trim());\n            c.put("expiry",policyExpiryE==null?parsed.optString("expiryDate",""):policyExpiryE.getText().toString().trim());'''
if old not in m: raise SystemExit('client save anchor not found')
m=m.replace(old,new,1)

old='''            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holder);pol.put("identityNumber",id);pol.put("address",address);pol.put("phone",phone);pol.put("email",email);pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());'''
new='''            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holder);pol.put("identityNumber",id);pol.put("address",address);pol.put("phone",phone);pol.put("email",email);pol.put("postalCode",policyPostalE==null?parsed.optString("postalCode",""):policyPostalE.getText().toString().trim());pol.put("issueDate",policyIssueE==null?parsed.optString("issueDate",""):policyIssueE.getText().toString().trim());pol.put("effectiveDate",policyEffectiveE==null?parsed.optString("effectiveDate",""):policyEffectiveE.getText().toString().trim());pol.put("expiryDate",policyExpiryE==null?parsed.optString("expiryDate",""):policyExpiryE.getText().toString().trim());pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());'''
if old not in m: raise SystemExit('policy save anchor not found')
m=m.replace(old,new,1)

M.write_text(m,encoding='utf-8')
print('policy dates/receipt v6 applied')
