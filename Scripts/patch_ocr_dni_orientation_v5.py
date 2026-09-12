from pathlib import Path

P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')

def replace_method(src, signature, replacement):
    start=src.find(signature)
    if start<0: raise SystemExit('method not found: '+signature)
    brace=src.find('{',start)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return src[:start]+replacement+src[i+1:]
    raise SystemExit('unbalanced method: '+signature)

helpers=r'''    private Bitmap rotateOcrBitmap(Bitmap src,int degrees){
        if(src==null||degrees==0)return src;
        Matrix m=new Matrix();m.postRotate(degrees);
        return Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);
    }
    private int dniFrontScoreStrong(String text){
        String u=normalizeOcrIdentity(text);int s=dniFrontScore(text);
        if(u.contains("NOMBRE"))s+=5;if(u.contains("APELLIDOS"))s+=5;if(u.contains("FECHA DE NACIMIENTO"))s+=5;
        if(u.contains("FECHA DE CADUCIDAD"))s+=4;if(u.contains("NACIONALIDAD"))s+=3;if(u.contains("SEXO"))s+=2;
        return s;
    }
    private int dniBackScoreStrong(String text){
        String u=normalizeOcrIdentity(text);int s=dniBackScore(text);
        if(u.contains("DOMICILIO"))s+=6;if(u.contains("LUGAR DE NACIMIENTO"))s+=6;if(u.contains("EQUIPO"))s+=2;
        if(u.contains("VALIDEZ"))s+=2;if(u.contains("ESP"))s+=2;
        return s;
    }
    private interface OcrOrientationCallback{void done(String text,int degrees);}
    private void recognizeDniOrientations(final Bitmap source,final int degrees,final String bestText,final int bestScore,final int bestDegrees,final OcrOrientationCallback cb){
        if(degrees>270){cb.done(bestText,bestDegrees);return;}
        final Bitmap prepared=stabilizeOcrBitmap(source);final Bitmap rotated=rotateOcrBitmap(prepared,degrees);final boolean ownRotated=rotated!=prepared;
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(rotated,0)).addOnSuccessListener(result->{
            String text=result==null?"":result.getText();int score=Math.max(dniFrontScoreStrong(text),dniBackScoreStrong(text));
            r.close();if(ownRotated)rotated.recycle();if(prepared!=source)prepared.recycle();
            if(score>bestScore)recognizeDniOrientations(source,degrees+90,text,score,degrees,cb);else recognizeDniOrientations(source,degrees+90,bestText,bestScore,bestDegrees,cb);
        }).addOnFailureListener(e->{r.close();if(ownRotated)rotated.recycle();if(prepared!=source)prepared.recycle();recognizeDniOrientations(source,degrees+90,bestText,bestScore,bestDegrees,cb);});
    }
'''
if 'dniFrontScoreStrong' not in s:
    marker='    private void reviewDniPair(){'
    if marker not in s: raise SystemExit('reviewDniPair marker not found')
    s=s.replace(marker,helpers+'\n'+marker,1)

s=replace_method(s,'    private void processDniPairOcr(){',r'''    private void processDniPairOcr(){
        if(frontBitmap==null||backBitmap==null)return;
        Toast.makeText(this,"Identificando anverso y reverso…",Toast.LENGTH_SHORT).show();
        recognizeDniOrientations(frontBitmap,0,"",-1,0,(aText,aDeg)->{
            recognizeDniOrientations(backBitmap,0,"",-1,0,(bText,bDeg)->{
                int af=dniFrontScoreStrong(aText),ab=dniBackScoreStrong(aText),bf=dniFrontScoreStrong(bText),bb=dniBackScoreStrong(bText);
                boolean swap=(ab+bf)>(af+bb);
                if(swap){String ts=aText;aText=bText;bText=ts;Bitmap tb=frontBitmap;frontBitmap=backBitmap;backBitmap=tb;String ps=frontImagePath;frontImagePath=backImagePath;backImagePath=ps;}
                frontText=aText;backText=bText;currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;
                showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));
            });
        });
    }''')

P.write_text(s,encoding='utf-8');print('DNI orientation/side detection v5 applied')
