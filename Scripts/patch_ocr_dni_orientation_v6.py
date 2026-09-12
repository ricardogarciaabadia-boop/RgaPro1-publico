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
    marker='    private void reviewDniPair(){'
    if marker not in s: raise SystemExit('reviewDniPair marker not found')
    s=s.replace(marker,helper+'\n'+marker,1)

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
print('DNI v6 applied: upright preview + reverse-only address')
