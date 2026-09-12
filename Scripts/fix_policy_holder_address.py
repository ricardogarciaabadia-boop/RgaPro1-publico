from pathlib import Path

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s=MAIN.read_text(encoding='utf-8')

def replace_method(src, signature, replacement):
    start=src.find(signature)
    if start<0: raise SystemExit('method not found: '+signature)
    brace=src.find('{',start); depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0: return src[:start]+replacement+src[i+1:]
    raise SystemExit('unbalanced: '+signature)

holder=r'''    private static String extractHolder(String[] lines){
        int[] b=sectionBounds(lines,new String[]{"DATOS DEL TOMADOR Y DOMICILIO","TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO"},new String[]{"RIESGO/S ASEGURADO/S","RIESGOS ASEGURADOS","DURACION DEL CONTRATO","DURACIÓN DEL CONTRATO"});
        if(b[0]<0)return "";
        String[] labels={"RAZON SOCIAL","RAZÓN SOCIAL","NOMBRE Y APELLIDOS","NOMBRE","TOMADOR DEL SEGURO"};
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String v=stripIdentityAndNoise(cutAtNextLabel(line.substring(p+label.length())));if(isRealPersonName(v))return v;}
        }
        for(int i=b[0];i<b[1];i++){
            String v=stripIdentityAndNoise(lines[i]);
            if(isRealPersonName(v))return v;
        }
        return "";
    }
'''
address=r'''    private static String extractAddress(String[] lines){
        int[] b=sectionBounds(lines,new String[]{"DATOS DEL TOMADOR Y DOMICILIO","TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO"},new String[]{"RIESGO/S ASEGURADO/S","RIESGOS ASEGURADOS","DURACION DEL CONTRATO","DURACIÓN DEL CONTRATO"});
        if(b[0]<0)return "";
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            int p=indexOfAny(line,new String[]{"DIRECCION:","DIRECCIÓN:","DIRECCION","DIRECCIÓN"});
            if(p>=0){
                String v=line.substring(p+9);
                v=v.replaceFirst("^\\s*[:.-]\\s*","");
                if(v.trim().isEmpty()&&i+1<b[1])v=lines[i+1];
                v=cutAtNextLabel(v);
                if(looksLikeAddress(v)&&!isOcasoAddressStrict(v))return clean(v);
            }
        }
        for(int i=b[0];i<b[1];i++){
            String v=clean(lines[i]);
            if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return v;
        }
        return "";
    }
'''
identity=r'''    private static String extractIdentity(String text,String[] lines){
        ArrayList<Candidate> candidates=new ArrayList<>();
        int[] b=sectionBounds(lines,new String[]{"DATOS DEL TOMADOR Y DOMICILIO","TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO"},new String[]{"RIESGO/S ASEGURADO/S","RIESGOS ASEGURADOS","DURACION DEL CONTRATO","DURACIÓN DEL CONTRATO"});
        if(b[0]>=0){for(int i=b[0];i<b[1];i++)addIdentityCandidates(candidates,lines[i],i,"DNI","NIE","DOC ID","DOC. ID","DOCUMENTO","IDENTIFICACION","IDENTIFICACIÓN","TOMADOR");}
        Collections.sort(candidates,new Comparator<Candidate>(){public int compare(Candidate a,Candidate b){return b.score-a.score;}});
        for(Candidate c:candidates)if(isValidIdentity(c.value))return c.value;
        return "";
    }
'''
phone=r'''    private static String extractPhone(String text,String[] lines){
        int[] b=sectionBounds(lines,new String[]{"DATOS DEL TOMADOR Y DOMICILIO","TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO"},new String[]{"RIESGO/S ASEGURADO/S","RIESGOS ASEGURADOS","DURACION DEL CONTRATO","DURACIÓN DEL CONTRATO"});
        if(b[0]<0)return "";
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
helpers=r'''    private static boolean isRealPersonName(String s){
        if(s==null)return false;String u=clean(s).toUpperCase(Locale.ROOT);
        if(u.length()<5||u.length()>100||u.matches(".*\\d{2,}.*"))return false;
        if(isCompanyOrOffice(u)||u.contains("POR CUENTA")||u.contains("CONCEPTO")||u.contains("MEDIO DE CONTACTO")||u.contains("MEDIOS DE CONTACTO")||u.contains("DIRECCION")||u.contains("DIRECCIÓN")||u.contains("DOMICILIO")||u.contains("LEIDO Y CONFORME")||u.contains("DIRECTOR GENERAL")||u.contains("SEGUROS Y REASEGUROS"))return false;
        String[] w=u.split(" ");return w.length>=2;
    }
    private static boolean isOcasoAddressStrict(String s){
        String u=clean(s).toUpperCase(Locale.ROOT);
        return isOcasoAddress(u)||u.contains("PRINCESA")||u.contains("28008")||u.contains("915380")||u.contains("915 380")||u.contains("OCASO.ES")||u.contains("DOMICILIO SOCIAL")||u.contains("OCASO, S.A")||u.contains("OCASO S.A");
    }
    private static boolean isNonClientAddress(String s){
        String u=clean(s).toUpperCase(Locale.ROOT);
        return u.contains("DOMICILIO DE COBRO")||u.contains("DOMICILIO DE")||u.contains("CUENTA ES")||u.contains("BANCO ")||u.contains("CAIXABANK")||u.contains("SANTANDER")||u.contains("CAJA R.");
    }
    private static boolean isOcasoPhone(String s){return "915380100".equals(s)||"915380469".equals(s);}

'''
marker='    private static String normalize(String raw){'
s=replace_method(s,'    private static String extractHolder(String[] lines){',holder)
s=replace_method(s,'    private static String extractAddress(String[] lines){',address)
s=replace_method(s,'    private static String extractIdentity(String text,String[] lines){',identity)
s=replace_method(s,'    private static String extractPhone(String text,String[] lines){',phone)
s=s.replace(marker,helpers+marker,1)
MAIN.write_text(s,encoding='utf-8')
print('policy holder/address/phone parser hardened')
