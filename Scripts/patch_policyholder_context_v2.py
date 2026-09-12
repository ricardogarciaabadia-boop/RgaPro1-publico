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

holder = r'''    private static String extractHolder(String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        String[] labels={"RAZON SOCIAL","RAZÓN SOCIAL","NOMBRE Y APELLIDOS","NOMBRE","TOMADOR DEL SEGURO","TOMADOR/A"};
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            for(String label:labels){
                int p=line.indexOf(label); if(p<0)continue;
                String v=stripIdentityAndNoise(line.substring(p+label.length()));
                if(isRealPersonName(v))return v;
                if(i+1<b[1]){v=stripIdentityAndNoise(lines[i+1]);if(isRealPersonName(v))return v;}
            }
        }
        // In several Ocaso layouts the holder follows the concept line.
        for(int i=b[0];i<b[1];i++){
            if(lines[i].contains("CONCEPTO EN EL CUAL SE ASEGURA") && i+1<b[1]){
                String v=stripIdentityAndNoise(lines[i+1]);
                if(isRealPersonName(v))return v;
            }
        }
        return "";
    }
'''
address = r'''    private static String extractAddress(String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        // First choice: an explicit Dirección field, including its next OCR line.
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            int p=indexOfAny(line,new String[]{"DIRECCION:","DIRECCIÓN:","DIRECCION","DIRECCIÓN"});
            if(p>=0){
                String v=line.substring(p+9).replaceFirst("^\\s*[:.-]\\s*","");
                if(v.trim().isEmpty()&&i+1<b[1])v=lines[i+1];
                v=cutAtNextLabel(v);
                if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return clean(v);
                if(i+1<b[1]){v=cutAtNextLabel(lines[i+1]);if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return clean(v);}
            }
        }
        // Second choice: the address immediately following "Por cuenta propia".
        for(int i=b[0];i<b[1];i++){
            if(lines[i].contains("POR CUENTA PROPIA")){
                for(int j=i+1;j<Math.min(i+3,b[1]);j++){
                    String v=cutAtNextLabel(lines[j]);
                    if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return clean(v);
                }
            }
        }
        // Do NOT fall back to arbitrary addresses in the whole document: those are often the risk address.
        return "";
    }
'''
identity = r'''    private static String extractIdentity(String text,String[] lines){
        ArrayList<Candidate> candidates=new ArrayList<>();
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        for(int i=b[0];i<b[1];i++)addIdentityCandidates(candidates,lines[i],i,"DNI","NIE","DOC ID","DOC. ID","DOCUMENTO","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR");
        Collections.sort(candidates,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
        for(Candidate c:candidates)if(isValidIdentity(c.value))return c.value;
        return "";
    }
'''
phone = r'''    private static String extractPhone(String text,String[] lines){
        int[] b=policyholderBounds(lines); if(b[0]<0)return "";
        for(int i=b[0];i<b[1];i++){
            String u=lines[i].toUpperCase(Locale.ROOT);
            if(!(u.contains("MEDIO/S DE CONTACTO")||u.contains("MEDIOS DE CONTACTO")||u.contains("TELEFONO")||u.contains("TELÉFONO")||u.contains("MOVIL")||u.contains("MÓVIL")))continue;
            for(int j=i;j<Math.min(i+3,b[1]);j++){
                String compact=lines[j].replaceAll("[ .-]","").replace("+34","");
                Matcher m=PHONE.matcher(compact);if(m.find()&&!isOcasoPhone(m.group(1)))return m.group(1);
            }
        }
        return "";
    }
'''
realname = r'''    private static boolean isRealPersonName(String s){
        if(s==null)return false;String u=clean(s).toUpperCase(Locale.ROOT);
        if(u.length()<5||u.length()>100||u.matches(".*\\d{2,}.*"))return false;
        String[] forbidden={"TOMADOR DEL SEGURO","DATOS DEL TOMADOR","RAZON SOCIAL","RAZÓN SOCIAL","NOMBRE Y APELLIDOS","CONCEPTO EN EL CUAL","POR CUENTA","MEDIO/S DE CONTACTO","MEDIOS DE CONTACTO","DIRECCION","DIRECCIÓN","DOMICILIO","LEIDO Y CONFORME","DIRECTOR GENERAL","SEGUROS Y REASEGUROS","AGENTE DE SEGUROS","RIESGO/S ASEGURADO","ASEGURADO PRINCIPAL"};
        for(String f:forbidden)if(u.equals(f)||u.contains(f))return false;
        if(isCompanyOrOffice(u))return false;
        String[] w=u.split(" ");return w.length>=2;
    }
'''
bounds = r'''    private static int[] policyholderBounds(String[] lines){
        String[] starts={"DATOS DEL TOMADOR Y DOMICILIO","TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO","DATOS DEL TOMADOR"};
        String[] ends={"CONCEPTO EN EL CUAL SE ASEGURA","RIESGO/S ASEGURADO/S","RIESGOS ASEGURADOS","RIESGO ASEGURADO","ASEGURADO PRINCIPAL","DATOS DEL RIESGO","DATOS DE LA VIVIENDA ASEGURADA","DURACION DEL CONTRATO","DURACIÓN DEL CONTRATO"};
        int start=-1;
        for(int i=0;i<lines.length;i++){String u=lines[i];for(String m:starts)if(u.contains(m)){start=i;break;}if(start>=0)break;}
        if(start<0)return new int[]{-1,-1};
        int end=lines.length;
        for(int i=start+1;i<lines.length;i++)for(String m:ends)if(lines[i].contains(m)){end=i;return new int[]{start,end};}
        return new int[]{start,end};
    }
'''

s=replace_method(s,'    private static String extractHolder(String[] lines){',holder)
s=replace_method(s,'    private static String extractAddress(String[] lines){',address)
s=replace_method(s,'    private static String extractIdentity(String text,String[] lines){',identity)
s=replace_method(s,'    private static String extractPhone(String text,String[] lines){',phone)
s=replace_method(s,'    private static boolean isRealPersonName(String s){',realname)
s=replace_method(s,'    private static int[] sectionBounds(String[] lines,String[] starts,String[] ends){',bounds)
P.write_text(s,encoding='utf-8')
print('policyholder context v2 applied')
