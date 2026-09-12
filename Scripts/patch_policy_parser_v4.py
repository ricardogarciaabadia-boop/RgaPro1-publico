from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s=P.read_text(encoding='utf-8')

def replace_method(src,name,replacement):
    start=src.find('private static ',0)
    while start>=0:
        brace=src.find('{',start)
        if brace<0:break
        sig=src[start:brace]
        if (' '+name+'(') in sig:
            depth=0
            for i in range(brace,len(src)):
                if src[i]=='{':depth+=1
                elif src[i]=='}':
                    depth-=1
                    if depth==0:return src[:start]+replacement+src[i+1:]
        start=src.find('private static ',brace+1)
    raise SystemExit('method not found: '+name)

s=replace_method(s,'extractPolicyNumber',r'''private static String extractPolicyNumber(String text,String[] lines){
        String[] labels={"NUMERO DE POLIZA","Nº DE POLIZA","Nº POLIZA","NUMERO POLIZA","N° DE POLIZA","N° POLIZA","POLIZA"};
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]).replace(" :",":");
            for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String tail=line.substring(p+label.length());String v=firstPolicyCandidateStrict(tail);if(!v.isEmpty())return v;if(i+1<lines.length){v=firstPolicyCandidateStrict(lines[i+1]);if(!v.isEmpty())return v;}}
        }
        return "";
    }''')

s=s.replace('private static String firstPolicyCandidate(String s){Matcher m=POLICY_NUMBER.matcher(s);while(m.find()){String v=m.group(1);if(v.length()>=5&&!looksLikePhone(v)&&!looksLikeDateNumber(v)&&!looksLikeMoneyNumber(v))return v;}return "";}',r'''private static String firstPolicyCandidate(String s){return firstPolicyCandidateStrict(s);}
    private static String firstPolicyCandidateStrict(String s){
        if(s==null)return "";Matcher m=Pattern.compile("(?<![0-9])(\\d{5,12})(?![0-9])").matcher(s.replace(" ",""));
        while(m.find()){String v=m.group(1);if(v.length()==9&&looksLikePhone(v))continue;if(v.length()==8&&s.matches(".*\\bDNI\\b.*"))continue;return v;}return "";
    }''')

s=replace_method(s,'extractAddress',r'''private static String extractAddress(String[] lines){
        int[] b=policyholderBounds(lines);if(b[0]<0)return "";
        String[] labels={"DIRECCION DE RESIDENCIA","DIRECCIÓN DE RESIDENCIA","DIRECCION","DIRECCIÓN","DOMICILIO DEL TOMADOR","DOMICILIO"};
        for(int i=b[0];i<b[1];i++){
            String line=clean(lines[i]).replace(" :",":");
            for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String v=cutAtNextLabel(clean(line.substring(p+label.length()).replaceFirst("^\\s*[:.-]\\s*","")));if(looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return v;
                for(int j=i+1;j<Math.min(i+4,b[1]);j++){v=cutAtNextLabel(clean(lines[j]));if(!isFieldLabelOnly(v)&&looksLikeAddress(v)&&!isOcasoAddressStrict(v)&&!isNonClientAddress(v))return v;}
            }
        }return "";
    }''')

s=replace_method(s,'addIdentityCandidates',r'''private static void addIdentityCandidates(ArrayList<Candidate> out,String source,int lineIndex,String... labels){
        Matcher m=DNI.matcher(source);while(m.find())out.add(identityCandidate(m.group(),source,lineIndex,labels));
        m=DNI_SPACED.matcher(source);while(m.find())out.add(identityCandidate(m.group(1)+m.group(2)+m.group(3),source,lineIndex,labels));
        String compact=source.toUpperCase(Locale.ROOT).replace('O','0').replace('I','1').replace('L','1');m=DNI.matcher(compact);while(m.find())out.add(identityCandidate(m.group(),source,lineIndex,labels));
        Matcher bad=Pattern.compile("(?<![0-9])(\\d{8})[0-9](?![0-9])").matcher(source.replaceAll("[ .]",""));
        while(bad.find()){String digits=bad.group(1);String value=digits+DNI_LETTERS.charAt(Integer.parseInt(digits)%23);out.add(identityCandidate(value,source,lineIndex,labels));}
    }''')

s=replace_method(s,'extractPhone',r'''private static String extractPhone(String text,String[] lines){
        int[] b=policyholderBounds(lines);if(b[0]<0)return "";
        for(int i=b[0];i<b[1];i++){
            String u=clean(lines[i]);String compact=u.replace("+34","").replaceAll("[ .-]","");
            boolean label=u.contains("MEDIOS DE CONTACTO")||u.contains("MEDIO/S DE CONTACTO")||u.contains("MEDIO/S DE CONTACTO:")||u.contains("TELEFONO")||u.contains("TELÉFONO")||u.contains("MOVIL")||u.contains("MÓVIL");
            if(!label)continue;for(int j=i;j<Math.min(i+4,b[1]);j++){Matcher m=PHONE.matcher(lines[j].replace("+34","").replaceAll("[ .-]",""));if(m.find()&&!isOcasoPhone(m.group(1)))return m.group(1);}
            Matcher m=PHONE.matcher(compact);if(m.find()&&!isOcasoPhone(m.group(1)))return m.group(1);
        }return "";
    }''')

P.write_text(s,encoding='utf-8');print('policy parser v4 applied')
