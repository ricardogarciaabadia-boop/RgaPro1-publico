from pathlib import Path
import re

P=Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s=P.read_text(encoding='utf-8')

def replace_method(src,sig,new):
    a=src.find(sig)
    if a<0: raise SystemExit('method not found: '+sig)
    b=src.find('{',a); d=0
    for i in range(b,len(src)):
        if src[i]=='{': d+=1
        elif src[i]=='}':
            d-=1
            if d==0:return src[:a]+new+src[i+1:]
    raise SystemExit('unbalanced method')

# Hogar-specific insured capital values. Keep decesos capital separate.
methods=r'''    private static String extractHogarValue(String[] lines,String label){
        for(String line:lines){
            String u=line.toUpperCase(Locale.ROOT);
            int p=u.indexOf(label);
            if(p<0) continue;
            Matcher m=MONEY.matcher(line.substring(p+label.length()));
            String best="";
            while(m.find()){String v=normalizeMoney(m.group(1));if(isPlausibleMoney(v))best=v;}
            if(!best.isEmpty())return best;
        }
        return "";
    }

    private static String extractReceiptRobust(String[] lines){
        String best="";
        for(String line:lines){
            String u=line.toUpperCase(Locale.ROOT);
            if(!(u.contains("TOTAL RECIBO")||u.contains("TOTAL DEL RECIBO")))continue;
            Matcher m=MONEY.matcher(line);
            while(m.find()){
                String v=normalizeMoney(m.group(1));
                if(isPlausibleMoney(v))best=v;
            }
        }
        return best;
    }
'''
# Insert only once before extractMoney.
if 'private static String extractHogarValue' not in s:
    anchor='    private static String extractMoney(String[] lines,String[] labels){'
    if anchor not in s: raise SystemExit('extractMoney anchor not found')
    s=s.replace(anchor,methods+anchor,1)

# Override receipt extraction and add continent/content to parser output.
old='out.put("receipt",extractMoney(lines,new String[]{"TOTAL DEL RECIBO","TOTAL RECIBO","IMPORTE TOTAL","PRIMA TOTAL","RECIBO","PRIMA"}));'
new='out.put("receipt",extractReceiptRobust(lines));'
if old in s:s=s.replace(old,new,1)
else:
    # tolerate the already-patched line format
    s=re.sub(r'out\.put\("receipt",extractMoney\([^;]+\);',new,s,count=1)

anchor='out.put("postalCode",extractPostalCode(lines)); out.put("issueDate",extractIssueDate(lines));'
insert='out.put("postalCode",extractPostalCode(lines)); out.put("issueDate",extractIssueDate(lines));\n            out.put("continent",extractHogarValue(lines,"CONTINENTE:")); out.put("content",extractHogarValue(lines,"CONTENIDO:"));'
if anchor not in s: raise SystemExit('date output anchor not found')
s=s.replace(anchor,insert,1)
P.write_text(s,encoding='utf-8')

# UI/save changes are applied after patch_policy_parser_v5 in CI.
M=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
m=M.read_text(encoding='utf-8')

# Add field variables.
old='private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,policyPostalE,policyIssueE,receiptE,capitalE,decesosE,decesosLeveladaE;'
new='private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyEffectiveE,policyExpiryE,policyPostalE,policyIssueE,policyContinentE,policyContentE,receiptE,capitalE,decesosE,decesosLeveladaE;'
if old in m:m=m.replace(old,new,1)

# Insert Hogar fields immediately before TOTAL RECIBO in review, if not present.
marker='        addPolicyField("TOTAL RECIBO",null,receiptE);'
if 'policyContinentE=inputWithValue("Continente"' not in m:
    addition='''        if ("Hogar".equalsIgnoreCase(product)) {
            policyContinentE=inputWithValue("Continente",p.optString("continent",""));
            policyContentE=inputWithValue("Contenido",p.optString("content",""));
            addPolicyField("CONTINENTE",null,policyContinentE);
            addPolicyField("CONTENIDO",null,policyContentE);
        }
        addPolicyField("TOTAL RECIBO",null,receiptE);'''
    if marker not in m: raise SystemExit('total receipt UI marker not found')
    m=m.replace(marker,addition,1)

# Replace any unconditional capital review field with a product-aware one.
pattern=r'\s*addPolicyField\("CAPITAL",null,capitalE\);'
if re.search(pattern,m):
    m=re.sub(pattern,'\n        if (!"Hogar".equalsIgnoreCase(product)) addPolicyField("CAPITAL",null,capitalE);',m,count=1)

# Save Hogar values into both client and policy JSON.
client_anchor='c.put("expiry",policyExpiryE==null?parsed.optString("expiryDate",""):policyExpiryE.getText().toString().trim());'
client_add=client_anchor+'\n            c.put("continent",policyContinentE==null?parsed.optString("continent",""):policyContinentE.getText().toString().trim());\n            c.put("content",policyContentE==null?parsed.optString("content",""):policyContentE.getText().toString().trim());'
if client_anchor in m and 'c.put("continent"' not in m:m=m.replace(client_anchor,client_add,1)

policy_anchor='pol.put("receipt",receiptE.getText().toString().trim());'
policy_add='pol.put("continent",policyContinentE==null?parsed.optString("continent",""):policyContinentE.getText().toString().trim());pol.put("content",policyContentE==null?parsed.optString("content",""):policyContentE.getText().toString().trim());'+policy_anchor
if policy_anchor in m and 'pol.put("continent"' not in m:m=m.replace(policy_anchor,policy_add,1)

M.write_text(m,encoding='utf-8')
print('Hogar continent/content + decesos-only capital patch applied')
