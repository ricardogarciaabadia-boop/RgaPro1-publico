from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')

def replace_method(src, sig, new):
    a=src.find(sig)
    if a<0: raise SystemExit('method not found: '+sig)
    b=src.find('{',a); d=0
    for i in range(b,len(src)):
        if src[i]=='{': d+=1
        elif src[i]=='}':
            d-=1
            if d==0:return src[:a]+new+src[i+1:]
    raise SystemExit('unbalanced method')

name=r'''    private String extractNameRobust(String text){
        String u=normalizeOcrIdentity(text).replace('\r','\n');
        String surnames="", name="";
        Matcher m=Pattern.compile("(?is)APELLIDOS\\s*[:.-]?\\s*(.+?)(?=\\s+NOMBRE\\b)").matcher(u);
        if(m.find())surnames=m.group(1).replaceAll("\\s+"," ").trim();
        m=Pattern.compile("(?is)NOMBRE\\s*[:.-]?\\s*(.+?)(?=\\s+(?:SEXO|NACIONALIDAD|FECHA DE NACIMIENTO|NUM SOPORTE|VALIDEZ)\\b)").matcher(u);
        if(m.find())name=m.group(1).replaceAll("\\s+"," ").trim();
        if(surnames.isEmpty()){
            String[] ls=u.split("\\n");
            for(int i=0;i<ls.length;i++)if(ls[i].trim().startsWith("APELLIDOS")){String v=ls[i].replaceFirst("^APELLIDOS?\\s*[:.-]?\\s*","").trim();if(v.isEmpty()&&i+1<ls.length)v=ls[++i].trim();surnames=v;break;}
        }
        if(name.isEmpty()){
            String[] ls=u.split("\\n");
            for(int i=0;i<ls.length;i++)if(ls[i].trim().startsWith("NOMBRE")){String v=ls[i].replaceFirst("^NOMBRES?\\s*[:.-]?\\s*","").trim();if(v.isEmpty()&&i+1<ls.length)v=ls[++i].trim();name=v;break;}
        }
        surnames=surnames.replaceAll("[^A-ZÁÉÍÓÚÑ ]"," ").replaceAll("\\s+"," ").trim();
        name=name.replaceAll("[^A-ZÁÉÍÓÚÑ ]"," ").replaceAll("\\s+"," ").trim();
        return (!name.isEmpty()&&!surnames.isEmpty())?(name+" "+surnames).trim():(!name.isEmpty()?name:surnames);
    }
'''
s=replace_method(s,'    private String extractNameRobust(String text){',name)

date=r'''    private String extractBirthDateRobust(String text){
        String u=normalizeOcrIdentity(text);
        Matcher m=Pattern.compile("(?:FECHA\\s+DE\\s+NACIMIENTO|NACIMIENTO)[^0-9]{0,40}(\\d{1,2})\\s*[/.-]?\\s*(\\d{1,2})\\s*[/.-]?\\s*(\\d{4})").matcher(u);
        if(m.find())return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
        return "";
    }
'''
s=replace_method(s,'    private String extractBirthDateRobust(String text){',date)

addr=r'''    private String dniReverseAddress(String text){
        if(text==null)return "";
        String[] ls=normalizeOcrIdentity(text).split("\\n");
        for(int i=0;i<ls.length;i++){
            String l=ls[i].trim();
            if(l.contains("DOMICILIO")||l.startsWith("DIRECCION")){
                String v=l.replaceFirst(".*?(?:DOMICILIO|DIRECCION)\\s*:?[ ]*","").trim();
                if(v.length()>5&&!v.contains("<<<<"))return v;
                for(int j=i+1;j<Math.min(i+4,ls.length);j++){
                    v=ls[j].trim();
                    if(v.length()>5&&!v.contains("<<<<")&&!v.matches("(?i)ESPAÑA|ESP|LUGAR DE NACIMIENTO.*"))return v;
                }
            }
        }
        return "";
    }
'''
s=replace_method(s,'    private String dniReverseAddress(String text){',addr)

proc=r'''    private void processImage(){
        if(currentBitmap==null){Toast.makeText(this,"Primero selecciona un JPEG válido.",Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,"Girando y clasificando documento…",Toast.LENGTH_SHORT).show();
        recognizeDniOrientations(currentBitmap,0,"",-1,0,(text,deg)->{
            Bitmap upright=rotateOcrBitmap(currentBitmap,deg);
            String u=normalizeOcrIdentity(text);
            boolean isBack=dniBackScoreStrong(text)>dniFrontScoreStrong(text);
            if(isBack){backBitmap=upright;backText=text;backImagePath=currentImagePath;}else{frontBitmap=upright;frontText=text;frontImagePath=currentImagePath;}
            currentBitmap=upright;previewBitmap=upright;
            if(frontBitmap!=null&&backBitmap!=null){
                int af=dniFrontScoreStrong(frontText),ab=dniBackScoreStrong(frontText),bf=dniFrontScoreStrong(backText),bb=dniBackScoreStrong(backText);
                if((ab+bf)>(af+bb)){String t=frontText;frontText=backText;backText=t;Bitmap bm=frontBitmap;frontBitmap=backBitmap;backBitmap=bm;String p=frontImagePath;frontImagePath=backImagePath;backImagePath=p;}
                JSONObject data=parseEssentialRobust(frontText);String address=dniReverseAddress(backText);try{if(address.length()>0)data.put("address",address);}catch(Exception ignored){};
                currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;showIdentityReview(data);
            }else{
                JSONObject data=parseEssentialRobust(isBack?"":frontText);if(isBack){try{String address=dniReverseAddress(backText);if(address.length()>0)data.put("address",address);}catch(Exception ignored){}}
                showIdentityReview(data);
            }
        });
    }
'''
s=replace_method(s,'    private void processImage(){',proc)
P.write_text(s,encoding='utf-8')
print('DNI v7 applied: explicit field parsing + single-image rotation/classification + reverse address')
