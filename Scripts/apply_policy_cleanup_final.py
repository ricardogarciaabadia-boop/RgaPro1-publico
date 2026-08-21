from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')
needle='p.put("number",number.getText().toString().trim());p.put("type",type);p.put("ocrText",raw);p.put("updatedAt",System.currentTimeMillis());'
repl=needle+'if(policyDocument){p.put("documentKind","POLIZA");p.put("identityType","");p.put("identityNumber","");p.remove("holderDni");p.remove("birthDate");p.remove("nationality");p.remove("sex");p.remove("birthPlace");p.remove("parents");p.remove("supportNumber");p.remove("issueDate");p.remove("validityDate");p.remove("expiry");}'
if needle in s and 'p.remove("holderDni");p.remove("birthDate")' not in s[s.find(needle):s.find(needle)+len(repl)+200]:
    s=s.replace(needle,repl,1)
p.write_text(s,encoding='utf-8')
print('Final policy cleanup applied')
