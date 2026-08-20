from pathlib import Path

p = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Improve policy OCR cross-linking: isolate the insured section instead of consuming
# the rest of the document, preserve role/identity/date fields, and avoid duplicate
# people by DNI/NIE/CIF or normalized name+birthdate.
start = s.find('    private JSONArray extractInsuredsFromOcr(String raw,String holder){')
if start >= 0:
    brace = s.find('{', start)
    depth = 0
    end = None
    for i in range(brace, len(s)):
        if s[i] == '{': depth += 1
        elif s[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise SystemExit('extractInsuredsFromOcr: unbalanced method')
    method = r'''    private JSONArray extractInsuredsFromOcr(String raw,String holder){
        JSONArray out=new JSONArray();
        String text=raw==null?"":raw.replace('\r','\n');
        String upper=text.toUpperCase(Locale.ROOT);
        Matcher section=Pattern.compile("(?is)(?:PERSONAS\\s+ASEGURADAS|ASEGURADOS?|ASEGURADAS?)\\s*[:\\-]?\\s*(.*?)(?=\\n\\s*(?:TOMADOR(?:A)?|CONTRATANTE|BENEFICIARIO(?:S)?|PRIMA|RECIBO|FORMA\\s+DE\\s+PAGO|DATOS\\s+DE\\s+LA\\s+POLIZA|DATOS\\s+DE\\s+LA\\s+PÓLIZA|DOMICILIO|OBSERVACIONES)\\b|$)").matcher(text);
        String block=section.find()?section.group(1):"";
        if(block.trim().isEmpty()){
            Matcher repeated=Pattern.compile("(?im)^\\s*(?:ASEGURADO(?:A)?|PERSONA\\s+ASEGURADA)\\s*[:\\-]+\\s*(.+)$").matcher(text);
            while(repeated.find()) addInsuredCandidate(out,repeated.group(1));
        }else{
            for(String line:block.split("\\n|;")){
                String x=line.trim();
                if(x.length()<3||x.length()>180)continue;
                if(x.toUpperCase(Locale.ROOT).matches(".*\\b(?:TOMADOR|CONTRATANTE|ASEGURADORA|POLIZA|PÓLIZA|PRIMA|RECIBO|DOMICILIO|TELEFONO|TELÉFONO|EMAIL|CORREO|FECHA)\\b.*"))continue;
                addInsuredCandidate(out,x);
            }
        }
        if(out.length()==0&&!holder.trim().isEmpty()) addInsuredCandidate(out,holder.trim());
        return out;
    }

    private void addInsuredCandidate(JSONArray out,String value){
        try{
            String x=value.replaceFirst("^[\\-•·:*]\\s*","").trim();
            if(x.isEmpty())return;
            JSONObject p=new JSONObject();
            Matcher id=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]|[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J])\\b",Pattern.CASE_INSENSITIVE).matcher(x);
            if(id.find()){p.put("identityNumber",id.group().toUpperCase(Locale.ROOT));x=x.substring(0,id.start())+" "+x.substring(id.end());}
            Matcher date=Pattern.compile("\\b\\d{2}[ /.-]\\d{2}[ /.-]\\d{4}\\b").matcher(x);
            if(date.find()){p.put("birthDate",date.group());x=x.substring(0,date.start())+" "+x.substring(date.end());}
            x=x.replaceAll("\\s+"," ").trim();
            if(x.length()<2)return;
            p.put("name",x);p.put("role","ASEGURADO");out.put(p);
        }catch(Exception ignored){}
    }
'''
    s = s[:start] + method + s[end:]

# Add a dedicated relation summary to policy detail/listing without changing the existing schema.
if 'private String policyRelationSummary(JSONObject p)' not in s:
    anchor = '    private void editPolicy(JSONObject policy){'
    helper = r'''    private String policyRelationSummary(JSONObject p){
        JSONArray ins=p.optJSONArray("insureds");
        int count=ins==null?0:ins.length();
        String product=p.optString("product","");
        String modality=p.optString("ahorroModalidad","");
        StringBuilder z=new StringBuilder("Tomador: ").append(p.optString("holder","Sin titular"));
        z.append(" · Asegurados: ").append(count);
        if(!product.isEmpty())z.append(" · Producto: ").append(product);
        if(!modality.isEmpty())z.append(" · Ahorro: ").append(modality);
        return z.toString();
    }

'''
    s=s.replace(anchor,helper+anchor,1)
    s=s.replace('String extra="";if(("Deceso".equalsIgnoreCase(t)||"Decesos".equalsIgnoreCase(t))&&!p.optString("product","").isEmpty())extra=" · Producto: "+p.optString("product");','String extra="";if(("Deceso".equalsIgnoreCase(t)||"Decesos".equalsIgnoreCase(t))&&!p.optString("product","").isEmpty())extra=" · Producto: "+p.optString("product");')

p.write_text(s, encoding='utf-8')
print('Improved policy OCR insured extraction and cross-link relation handling')
