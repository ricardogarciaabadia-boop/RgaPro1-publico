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
    raise SystemExit('unbalanced method: '+sig)

helpers=r'''    private Bitmap autoCropDocument(Bitmap src){
        if(src==null)return null;
        int w=src.getWidth(),h=src.getHeight(),step=Math.max(1,Math.min(w,h)/500);
        int[] border=new int[Math.max(16,((w/step)+(h/step))*2)];int bi=0;
        for(int x=0;x<w;x+=step){border[bi++]=src.getPixel(x,0);border[bi++]=src.getPixel(x,Math.max(0,h-1));if(bi>=border.length)break;}
        for(int y=0;y<h&&bi<border.length;y+=step){border[bi++]=src.getPixel(0,y);if(bi<border.length)border[bi++]=src.getPixel(Math.max(0,w-1),y);}
        int br=0,bg=0,bb=0,count=Math.max(1,bi);for(int i=0;i<count;i++){br+=Color.red(border[i]);bg+=Color.green(border[i]);bb+=Color.blue(border[i]);}br/=count;bg/=count;bb/=count;
        int minX=w,minY=h,maxX=-1,maxY=-1;double threshold=42.0;
        for(int y=0;y<h;y+=step){for(int x=0;x<w;x+=step){int c=src.getPixel(x,y);int dr=Color.red(c)-br,dg=Color.green(c)-bg,db=Color.blue(c)-bb;double d=Math.sqrt(dr*dr+dg*dg+db*db);int sat=Math.max(Color.red(c),Math.max(Color.green(c),Color.blue(c)))-Math.min(Color.red(c),Math.min(Color.green(c),Color.blue(c)));if(d>threshold && sat>10){if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;}}}
        if(maxX<0||maxY<0)return src;int bw=maxX-minX+1,bh=maxY-minY+1;double area=(double)bw*bh/(double)(w*h);
        if(area<0.08||area>0.92||bw<w*0.25||bh<h*0.12)return src;
        int padX=Math.max(8,(int)(bw*0.035)),padY=Math.max(8,(int)(bh*0.035));minX=Math.max(0,minX-padX);minY=Math.max(0,minY-padY);maxX=Math.min(w-1,maxX+padX);maxY=Math.min(h-1,maxY+padY);
        return Bitmap.createBitmap(src,minX,minY,maxX-minX+1,maxY-minY+1);
    }
    private Bitmap uprightAndCropDni(Bitmap source,int degrees){
        Bitmap rotated=rotateOcrBitmap(source,degrees);Bitmap cropped=autoCropDocument(rotated);if(cropped!=rotated && rotated!=source)rotated.recycle();return cropped;
    }
    private interface DniChosenCallback{void done(String text,int degrees);}
    private void classifyDniImage(final Bitmap source,final int degrees,final String bestText,final int bestScore,final int bestDegrees,final DniChosenCallback cb){
        if(degrees>270){cb.done(bestText,bestDegrees);return;}
        final Bitmap prepared=stabilizeOcrBitmap(source),rotated=rotateOcrBitmap(prepared,degrees);
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(rotated,0)).addOnSuccessListener(result->{String text=result==null?"":result.getText();int score=Math.max(dniFrontScoreStrong(text),dniBackScoreStrong(text));r.close();if(rotated!=prepared)rotated.recycle();if(prepared!=source)prepared.recycle();if(score>bestScore)classifyDniImage(source,degrees+90,text,score,degrees,cb);else classifyDniImage(source,degrees+90,bestText,bestScore,bestDegrees,cb);}).addOnFailureListener(e->{r.close();if(rotated!=prepared)rotated.recycle();if(prepared!=source)prepared.recycle();classifyDniImage(source,degrees+90,bestText,bestScore,bestDegrees,cb);});
    }
    private void reOcrDni(Bitmap image,boolean front,final Runnable done){
        if(image==null){done.run();return;}TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);Bitmap ocr=stabilizeOcrBitmap(image);r.process(InputImage.fromBitmap(ocr,0)).addOnSuccessListener(result->{if(front)frontText=result==null?"":result.getText();else backText=result==null?"":result.getText();r.close();if(ocr!=image)ocr.recycle();done.run();}).addOnFailureListener(e->{r.close();if(ocr!=image)ocr.recycle();done.run();});
    }
'''
if 'private Bitmap autoCropDocument' not in s:
    s=s.replace('    private void reviewDniPair(){',helpers+'\n    private void reviewDniPair(){',1)

pair=r'''    private void processDniPairOcr(){
        if(frontBitmap==null||backBitmap==null)return;
        Toast.makeText(this,"Detectando anverso/reverso, girando y recortando…",Toast.LENGTH_SHORT).show();
        classifyDniImage(frontBitmap,0,"",-1,0,(aText,aDeg)->{
            classifyDniImage(backBitmap,0,"",-1,0,(bText,bDeg)->{
                int af=dniFrontScoreStrong(aText),ab=dniBackScoreStrong(aText),bf=dniFrontScoreStrong(bText),bb=dniBackScoreStrong(bText);
                boolean swap=(ab+bf)>(af+bb);
                Bitmap au=uprightAndCropDni(frontBitmap,aDeg),bu=uprightAndCropDni(backBitmap,bDeg);
                if(swap){String t=aText;aText=bText;bText=t;Bitmap bm=au;au=bu;bu=bm;Bitmap ob=frontBitmap;frontBitmap=backBitmap;backBitmap=ob;String p=frontImagePath;frontImagePath=backImagePath;backImagePath=p;}
                frontBitmap=au;backBitmap=bu;currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;
                reOcrDni(frontBitmap,true,()->reOcrDni(backBitmap,false,()->{
                    org.json.JSONObject data=parseEssentialRobust(frontText);String address=dniReverseAddress(backText);try{if(address.length()>0)data.put("address",address);}catch(Exception ignored){}showIdentityReview(data);
                }));
            });
        });
    }'''
s=replace_method(s,'    private void processDniPairOcr(){',pair)

single=r'''    private void processImage(){
        if(currentBitmap==null){Toast.makeText(this,"Primero selecciona un JPEG válido.",Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,"Detectando lado, girando y recortando…",Toast.LENGTH_SHORT).show();
        classifyDniImage(currentBitmap,0,"",-1,0,(text,deg)->{
            Bitmap prepared=uprightAndCropDni(currentBitmap,deg);currentBitmap=prepared;previewBitmap=prepared;
            boolean isBack=dniBackScoreStrong(text)>dniFrontScoreStrong(text);
            if(isBack){backBitmap=prepared;backText=text;backImagePath=currentImagePath;}
            else{frontBitmap=prepared;frontText=text;frontImagePath=currentImagePath;}
            if(frontBitmap!=null&&backBitmap!=null){processDniPairOcr();return;}
            if(isBack){org.json.JSONObject data=parseEssentialRobust("");try{String address=dniReverseAddress(backText);if(address.length()>0)data.put("address",address);}catch(Exception ignored){}showIdentityReview(data);}
            else{showIdentityReview(parseEssentialRobust(frontText));}
        });
    }'''
s=replace_method(s,'    private void processImage(){',single)

P.write_text(s,encoding='utf-8');print('DNI v8 applied: automatic crop + upright image + explicit side classification')
