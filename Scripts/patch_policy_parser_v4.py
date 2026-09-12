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
            for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String v=firstPolicyCandidateStrict(line.substring(p+label.length()));if(!v.isEmpty())return v;if(i+1<lines.length){v=firstPolicyCandidateStrict(lines[i+1]);if(!v.isEmpty())return v;}}
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

# v5: product-specific Ocaso Hogar recognition and required home fields.
helpers=r'''    private static String extractHogarRiskAddress(String[] lines){
        for(int i=0;i<lines.length;i++){
            String u=lines[i].toUpperCase(Locale.ROOT);
            if(u.contains("RIESGO/S ASEGURADO/S")||u.contains("RIESGOS ASEGURADOS")){
                for(int j=i+1;j<Math.min(i+6,lines.length);j++){
                    String v=clean(lines[j]);String uv=v.toUpperCase(Locale.ROOT);
                    int p=uv.indexOf("DIRECCION");if(p<0)p=uv.indexOf("DIRECCIÓN");
                    if(p>=0)v=clean(v.substring(p+9).replaceFirst("^[: ]+",""));
                    if(looksLikeAddress(v)&&!isOcasoAddressStrict(v))return v;
                }
            }
        }return "";
    }
    private static String extractHogarCadastralReference(String[] lines){
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);if(u.contains("REFERENCIA CATASTRAL")||u.contains("REF. CATASTRAL")){String v=clean(line.replaceFirst("(?i).*?(REFERENCIA CATASTRAL|REF\\. CATASTRAL)[: ]*",""));if(!v.isEmpty())return v;}}return "";
    }
    private static String extractHogarCapital(String[] lines,String label){
        for(String line:lines){String u=line.toUpperCase(Locale.ROOT);int p=u.indexOf(label);if(p>=0){Matcher m=MONEY.matcher(line.substring(p+label.length()));if(m.find())return normalizeMoney(m.group(1));}}return "";
    }
    private static String extractDateAfterLabel(String[] lines,String[] labels){
        for(int i=0;i<lines.length;i++){String u=lines[i].toUpperCase(Locale.ROOT);for(String label:labels){int p=u.indexOf(label);if(p<0)continue;Matcher m=DATE.matcher(lines[i].substring(p+label.length()));if(m.find())return normalizeDate(m.group());for(int j=i+1;j<Math.min(i+3,lines.length);j++){m=DATE.matcher(lines[j]);if(m.find())return normalizeDate(m.group());}}}return "";
    }
    private static String addMoney(String a,String b){try{double x=Double.parseDouble(a.replace(".","").replace(",",".")),y=Double.parseDouble(b.replace(".","").replace(",","."));return String.format(Locale.ROOT,"%.2f",x+y).replace('.',',');}catch(Exception e){return "";}}
'''
if 'extractHogarRiskAddress' not in s:
    marker='    private static String classifyProduct(String text){';s=s.replace(marker,helpers+'\n'+marker,1)

s=replace_method(s,'classifyProduct',r'''private static String classifyProduct(String text){
        int hogar=score(text,"OCASO HOGAR",120)+score(text,"HOGAR PROTECCION",120)+score(text,"HOGAR PROTECCIÓN",120)+score(text,"MULTIRRIESGO HOGAR",120)+score(text,"RIESGO/S ASEGURADO/S",40)+score(text,"CONTINENTE",20)+score(text,"CONTENIDO",20);
        if(hogar>=120)return "Hogar";
        int decesos=score(text,"DECESOS",100)+score(text,"ASISTENCIA FAMILIAR",50)+score(text,"SERVICIO FUNERARIO",40),vida=score(text,"SEGURO DE VIDA",80)+score(text,"VIDA",20)+score(text,"FALLECIMIENTO",20),accidente=score(text,"ACCIDENTES",70)+score(text,"ACCIDENTE",30),salud=score(text,"ASISTENCIA SANITARIA",80)+score(text,"SALUD",30),auto=score(text,"AUTOMOVIL",80)+score(text,"AUTOMÓVIL",80)+score(text,"VEHICULO",30)+score(text,"VEHÍCULO",30),ahorro=score(text,"AHORRO",60)+score(text,"PIAS",60)+score(text,"RENTA",20),comunidad=score(text,"COMUNIDADES",70)+score(text,"COMUNIDAD",30),rc=score(text,"RESPONSABILIDAD CIVIL",90);
        int max=Math.max(Math.max(Math.max(decesos,vida),Math.max(accidente,salud)),Math.max(Math.max(auto,ahorro),Math.max(comunidad,rc)));
        if(max==decesos&&decesos>0)return "Decesos";if(max==vida&&vida>0)return "Vida";if(max==accidente&&accidente>0)return "Accidente de mujer";if(max==salud&&salud>0)return "Salud";if(max==auto&&auto>0)return "Auto";if(max==ahorro&&ahorro>0)return "Ahorro";if(max==comunidad&&comunidad>0)return "Comunidades";if(max==rc&&rc>0)return "Responsabilidad civil";return "Otros";
    }''')

anchor='out.put("number",extractPolicyNumber(text,lines)); out.put("holder",extractHolder(lines));'
if anchor in s:
    repl='out.put("number",extractPolicyNumber(text,lines)); out.put("holder",extractHolder(lines)); out.put("riskAddress",product.equals("Hogar")?extractHogarRiskAddress(lines):""); out.put("cadastralReference",product.equals("Hogar")?extractHogarCadastralReference(lines):""); out.put("effectiveDate",product.equals("Hogar")?extractDateAfterLabel(lines,new String[]{"FECHA DE EFECTO","FECHA EFECTO"}):""); out.put("expiryDate",product.equals("Hogar")?extractDateAfterLabel(lines,new String[]{"HASTA LAS","FECHA DE VENCIMIENTO","VENCIMIENTO"}):""); out.put("continent",product.equals("Hogar")?extractHogarCapital(lines,"CONTINENTE"):""); out.put("content",product.equals("Hogar")?extractHogarCapital(lines,"CONTENIDO"):""); out.put("totalInsuredValue",product.equals("Hogar")?addMoney(extractHogarCapital(lines,"CONTINENTE"),extractHogarCapital(lines,"CONTENIDO")):"");'
    s=s.replace(anchor,repl,1)
else: raise SystemExit('parse anchor not found')

P.write_text(s,encoding='utf-8');print('policy parser v4/v5 applied')