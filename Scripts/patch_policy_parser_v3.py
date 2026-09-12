from pathlib import Path

P = Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s = P.read_text(encoding='utf-8')

def replace_method(name, replacement):
    global s
    start = s.find('private static ', 0)
    while start >= 0:
        brace = s.find('{', start)
        if brace < 0:
            break
        signature = s[start:brace]
        if (' ' + name + '(') in signature:
            depth = 0
            i = brace
            while i < len(s):
                if s[i] == '{': depth += 1
                elif s[i] == '}':
                    depth -= 1
                    if depth == 0:
                        i += 1
                        s = s[:start] + replacement + s[i:]
                        return
                i += 1
        start = s.find('private static ', brace + 1)
    raise SystemExit('method not found: ' + name)

replace_method('extractHolder', r'''private static String extractHolder(String[] lines){
        int[] b=policyholderBounds(lines); int from=b[0]>=0?b[0]:0, to=b[0]>=0?b[1]:lines.length;
        String[] labels={"TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO","TOMADOR/A","NOMBRE Y APELLIDOS","NOMBRE"};
        for(int i=from;i<to;i++){String line=clean(lines[i]);for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String v=stripIdentityAndNoise(line.substring(p+label.length()));if(isRealPersonName(v))return v;for(int j=i+1;j<Math.min(i+5,to);j++){v=stripIdentityAndNoise(lines[j]);if(isRealPersonName(v)&&!isCompanyOrOffice(v))return v;}}}
        for(int i=0;i<lines.length;i++){String u=clean(lines[i]);if(u.contains("DOC ID")||u.contains("DOC. ID")||u.contains("DNI")||u.contains("NIE")){String v=u.replaceAll("(?i)\\b(DOC\\.?\\s*ID|DNI|NIE|DOCUMENTO)[ :.-]*[0-9A-Z]+"," ");v=cutAtNextLabel(v);if(isRealPersonName(v)&&!isCompanyOrOffice(v))return v;if(i>0){v=clean(lines[i-1]);if(isRealPersonName(v)&&!isCompanyOrOffice(v))return v;}}}
        return "";
    }''')

replace_method('extractAddress', r'''private static String extractAddress(String[] lines){
        int[] b=policyholderBounds(lines);if(b[0]<0)return "";
        String[] labels={"DIRECCION DE RESIDENCIA:","DIRECCION DE RESIDENCIA","DIRECCION:","DIRECCION","DOMICILIO DEL TOMADOR","DOMICILIO"};
        for(int i=b[0];i<b[1];i++){String line=clean(lines[i]);for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String v=cutAtNextLabel(clean(line.substring(p+label.length())));if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return v;for(int j=i+1;j<Math.min(i+4,b[1]);j++){v=cutAtNextLabel(clean(lines[j]));if(!isFieldLabelOnly(v)&&looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return v;}}}
        return "";
    }''')

replace_method('addIdentityCandidates', r'''private static void addIdentityCandidates(ArrayList<Candidate> out,String source,int lineIndex,String... labels){
        Matcher m=DNI.matcher(source);while(m.find())out.add(identityCandidate(m.group(),source,lineIndex,labels));
        m=DNI_SPACED.matcher(source);while(m.find())out.add(identityCandidate(m.group(1)+m.group(2)+m.group(3),source,lineIndex,labels));
        String compact=source.toUpperCase(Locale.ROOT).replace('O','0').replace('I','1').replace('L','1');m=DNI.matcher(compact);while(m.find())out.add(identityCandidate(m.group(),source,lineIndex,labels));
        Matcher bad=Pattern.compile("(?<![0-9])(\\d{8})[0-9](?![0-9])").matcher(source);while(bad.find()){String digits=bad.group(1);String value=digits+DNI_LETTERS.charAt(Integer.parseInt(digits)%23);out.add(identityCandidate(value,source,lineIndex,labels));}
    }''')

replace_method('normalize', r'''private static String normalize(String raw){
        if(raw==null)return "";String s=raw.toUpperCase(Locale.ROOT).replace('\r','\n').replace('\u00A0',' ');
        s=s.replace("Á","A").replace("É","E").replace("Í","I").replace("Ó","O").replace("Ú","U").replace("Ü","U").replace("Ñ","N");
        s=s.replace("N°","Nº").replace("P0LIZA","POLIZA").replace("T0MADOR","TOMADOR").replace("RECIB0","RECIBO").replace("CAPlTAL","CAPITAL").replace("DECES0S","DECESOS").replace("DOMlCILIO","DOMICILIO").replace("DIRECClON","DIRECCION").replace("TELEF0NO","TELEFONO");
        return s.replaceAll("[ \\t]+"," ").replaceAll("\\n{2,}","\\n").trim();
    }''')

P.write_text(s,encoding='utf-8')
print('policy parser v3 applied')
