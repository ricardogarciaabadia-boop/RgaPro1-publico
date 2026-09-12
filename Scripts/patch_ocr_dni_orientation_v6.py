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

helper=r'''    private String dniReverseAddress(String text){
        if(text==null)return "";
        String[] ls=normalizeOcrIdentity(text).split("\\n");
        for(String z:ls){
            String l=z.trim();
            if(l.contains("DOMICILIO")){String v=l.replaceFirst(".*DOMICILIO\\s*:?[ ]*","").trim();if(v.length()>5&&!v.contains("<<<<"))return v;}
            if(l.startsWith("DIRECCION")){String v=l.replaceFirst("DIRECCION\\s*:?[ ]*","").trim();if(v.length()>5&&!v.contains("<<<<"))return v;}
        }
        return "";
    }
'''
if 'private String dniReverseAddress' not in s:
    s=s.replace('    private void reviewDniPair(){',helper+'\n    private void reviewDniPair(){',1)

name_method=r'''    private String extractNameRobust(String text){
        String u=normalizeOcrIdentity(text).replace('\r','\n');
        String surnames="", name="";
        String[] lines=u.split("\\n");
        for(int i=0;i<lines.length;i++){
            String l=lines[i].trim().replaceAll("\\s+"," ");
            if(l.startsWith("APELLIDOS")){
                String v=l.replaceFirst("^APELLIDOS?\\s*[:.-]?\\s*","").trim();
                if(!v.isEmpty()&&!v.equals("APELLIDOS"))surnames=v;
                else if(i+1<lines.length)surnames=lines[++i].trim();
            }
            if(l.startsWith("NOMBRE")){
                String v=l.replaceFirst("^NOMBRES?\\s*[:.-]?\\s*","").trim();
                if(!v.isEmpty()&&!v.equals("NOMBRE"))name=v;
                else if(i+1<lines.length)name=lines[++i].trim();
            }
        }
        if(surnames.isEmpty()){
            Matcher m=Pattern.compile("(?i)APELLIDOS\\s*[:.-]?\\s*([A-ZÁÉÍÓÚÑ]+(?:\\s+[A-ZÁÉÍÓÚÑ]+)+)").matcher(u);
            if(m.find())surnames=m.group(1).trim();
        }
        if(name.isEmpty()){
            Matcher m=Pattern.compile("(?i)NOMBRE\\s*[:.-]?\\s*([A-ZÁÉÍÓÚÑ]+)").matcher(u);
            if(m.find())name=m.group(1).trim();
        }
        if(!name.isEmpty()&&!surnames.isEmpty())return (name+" "+surnames).replaceAll("\\s+"," ").trim();
        return !name.isEmpty()?name:surnames;
    }
'''
s=replace_method(s,'    private String extractNameRobust(String text){',name_method)

s=replace_method(s,'    private void processDniPairOcr(){',r'''    private void processDniPairOcr(){
        if(frontBitmap==null||backBitmap==null)return;
        Toast.makeText(this,"Girando y clasificando anverso/reverso…",Toast.LENGTH_SHORT).show();
        recognizeDniOrientations(frontBitmap,0,"",-1,0,(aText,aDeg)->{
            recognizeDniOrientations(backBitmap,0,"",-1,0,(bText,bDeg)->{
                int af=dniFrontScoreStrong(aText),ab=dniBackScoreStrong(aText),bf=dniFrontScoreStrong(bText),bb=dniBackScoreStrong(bText);
                boolean swap=(ab+bf)>(af+bb);
                Bitmap au=rotateOcrBitmap(frontBitmap,aDeg),bu=rotateOcrBitmap(backBitmap,bDeg);
                if(swap){String t=aText;aText=bText;bText=t;Bitmap bm=au;au=bu;bu=bm;Bitmap ob=frontBitmap;frontBitmap=backBitmap;backBitmap=ob;String p=frontImagePath;frontImagePath=backImagePath;backImagePath=p;}
                if(au!=frontBitmap)frontBitmap=au;if(bu!=backBitmap)backBitmap=bu;
                frontText=aText;backText=bText;currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;
                org.json.JSONObject data=parseEssentialRobust(frontText);
                String address=dniReverseAddress(backText);
                try{if(address.length()>0)data.put("address",address);data.put("phone",data.optString("phone",""));}catch(Exception ignored){}
                showIdentityReview(data);
            });
        });
    }''')
P.write_text(s,encoding='utf-8')
print('DNI v6 updated: explicit NOMBRE/APELLIDOS extraction + upright preview + reverse-only address')
