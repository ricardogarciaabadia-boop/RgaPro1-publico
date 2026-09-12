from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')
anchor='''        addPolicyField("TELÉFONO",null,policyPhoneE);\n\n        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);'''
insert='''        addPolicyField("TELÉFONO",null,policyPhoneE);\n        if("Hogar".equalsIgnoreCase(p.optString("policyType", ""))){\n            addPolicyField("DIRECCIÓN DEL RIESGO",p.optString("riskAddress",""),null);\n            addPolicyField("REFERENCIA CATASTRAL",p.optString("cadastralReference",""),null);\n            addPolicyField("CONTINENTE",p.optString("continent",""),null);\n            addPolicyField("CONTENIDO",p.optString("content",""),null);\n            addPolicyField("VALOR TOTAL ASEGURADO",p.optString("totalInsuredValue",""),null);\n            addPolicyField("FECHA DE EFECTO",p.optString("effectiveDate",""),null);\n            addPolicyField("FECHA DE VENCIMIENTO",p.optString("expiryDate",""),null);\n        }\n\n        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);'''
if anchor not in s: raise SystemExit('review anchor not found')
s=s.replace(anchor,insert,1)
P.write_text(s,encoding='utf-8');print('home fields UI patch applied')