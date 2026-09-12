from pathlib import Path

P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')
s=s.replace('if ("Hogar".equalsIgnoreCase(product)) {','if ("Hogar".equalsIgnoreCase(p.optString("policyType", ""))) {',1)
s=s.replace('if (!"Hogar".equalsIgnoreCase(product)) addPolicyField("CAPITAL",null,capitalE);','if (!"Hogar".equalsIgnoreCase(p.optString("policyType", ""))) addPolicyField("CAPITAL",null,capitalE);',1)
P.write_text(s,encoding='utf-8')
print('Hogar review scope fixed')
