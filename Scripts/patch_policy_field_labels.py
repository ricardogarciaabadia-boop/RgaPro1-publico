from pathlib import Path

P = Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s = P.read_text(encoding='utf-8')


def replace_method(src, sig, repl):
    start = src.find(sig)
    if start < 0:
        raise SystemExit('method not found: ' + sig)
    brace = src.find('{', start)
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + repl + src[i+1:]
    raise SystemExit('unbalanced: ' + sig)

# Replace policy-number extraction so it only accepts a value associated with
# the policy-number label. Never use an arbitrary number from the document.
policy_number = r'''    private static String extractPolicyNumber(String text, String[] lines) {
        String[] labels={"NUMERO DE POLIZA","Nº DE POLIZA","Nº POLIZA","NUMERO POLIZA","POLIZA"};
        for(int i=0;i<lines.length;i++){
            String line=lines[i];
            for(String label:labels){
                int p=line.indexOf(label); if(p<0)continue;
                String same=line.substring(p+label.length());
                String v=firstValidPolicyNumber(same);
                if(!v.isEmpty())return v;
                for(int j=i+1;j<Math.min(i+4,lines.length);j++){
                    v=firstValidPolicyNumber(lines[j]);
                    if(!v.isEmpty())return v;
                }
            }
        }
        return "";
    }
    private static String firstValidPolicyNumber(String s){
        Matcher m=POLICY_NUMBER.matcher(s);
        while(m.find()){
            String v=m.group(1);
            if(v.length()<5||looksLikePhone(v)||looksLikeDateNumber(v)||looksLikeMoneyNumber(v))continue;
            return v;
        }
        return "";
    }
'''
s = replace_method(s, '    private static String extractPolicyNumber(String text, String[] lines) {', policy_number)

# Replace holder extraction with a label/value reader that skips field labels.
holder = r'''    private static String extractHolder(String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        String[] labels={"TOMADOR DEL SEGURO","TOMADOR/A","NOMBRE Y APELLIDOS","NOMBRE","RAZON SOCIAL","RAZÓN SOCIAL"};
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            for(String label:labels){
                int p=line.indexOf(label); if(p<0)continue;
                String v=stripIdentityAndNoise(line.substring(p+label.length()));
                if(isRealPersonName(v))return v;
                for(int j=i+1;j<Math.min(i+4,b[1]);j++){
                    v=stripIdentityAndNoise(lines[j]);
                    if(isFieldLabelOnly(v))continue;
                    if(isRealPersonName(v))return v;
                }
            }
        }
        return "";
    }
'''
s = replace_method(s, '    private static String extractHolder(String[] lines){', holder)

# Replace identity extraction with nearby label/value context.
identity = r'''    private static String extractIdentity(String text,String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        ArrayList<Candidate> candidates=new ArrayList<>();
        String[] labels={"DNI","NIE","DOC ID","DOC. ID","DOCUMENTO","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR"};
        for(int i=b[0];i<b[1];i++){
            String line=lines[i].toUpperCase(Locale.ROOT);
            boolean near=false; for(String label:labels)if(line.contains(label)){near=true;break;}
            if(!near)continue;
            for(int j=i;j<Math.min(i+4,b[1]);j++)addIdentityCandidates(candidates,lines[j],j,labels);
        }
        Collections.sort(candidates,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
        for(Candidate c:candidates)if(isValidIdentity(c.value))return c.value;
        return "";
    }
'''
s = replace_method(s, '    private static String extractIdentity(String text,String[] lines){', identity)

# Replace address extraction: explicit client field first, then only a short
# window after the client-specific label. No arbitrary document fallback.
address = r'''    private static String extractAddress(String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        String[] labels={"DIRECCION:","DIRECCIÓN:","DIRECCION","DIRECCIÓN","DOMICILIO"};
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            for(String label:labels){
                int p=line.indexOf(label); if(p<0)continue;
                String v=line.substring(p+label.length()).replaceFirst("^\\s*[:.-]\\s*","");
                v=cutAtNextLabel(v);
                if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return clean(v);
                for(int j=i+1;j<Math.min(i+4,b[1]);j++){
                    v=cutAtNextLabel(lines[j]);
                    if(isFieldLabelOnly(v))continue;
                    if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return clean(v);
                }
            }
        }
        return "";
    }
'''
s = replace_method(s, '    private static String extractAddress(String[] lines){', address)

# Add one common guard used by every field extractor.
helper = r'''    private static boolean isFieldLabelOnly(String s){
        if(s==null)return true;
        String u=clean(s).toUpperCase(Locale.ROOT).replace(":","").trim();
        if(u.isEmpty())return true;
        String[] labels={"TOMADOR DEL SEGURO","TOMADOR","NOMBRE","NOMBRE Y APELLIDOS","APELLIDOS","DNI","NIE","DNI / NIE","DOCUMENTO","DIRECCION","DIRECCIÓN","DOMICILIO","TELEFONO","TELÉFONO","MOVIL","MÓVIL","EMAIL","E-MAIL","NUMERO DE POLIZA","Nº DE POLIZA","Nº POLIZA","NUMERO POLIZA","POLIZA"};
        for(String x:labels)if(u.equals(x))return true;
        return false;
    }

'''
marker='    private static boolean isRealPersonName(String s){'
if marker not in s:
    raise SystemExit('isRealPersonName marker not found')
s=s.replace(marker, helper+marker, 1)

P.write_text(s,encoding='utf-8')
print('policy field-label guard applied to name, DNI, address and policy number')
