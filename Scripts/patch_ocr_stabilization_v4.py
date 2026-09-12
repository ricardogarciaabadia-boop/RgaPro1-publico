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

helpers=r'''    private Bitmap stabilizeOcrBitmap(Bitmap src){
        if(src==null)return null;
        int max=2400,w=src.getWidth(),h=src.getHeight();
        float scale=Math.min(1f,max/(float)Math.max(w,h));
        int nw=Math.max(1,Math.round(w*scale)),nh=Math.max(1,Math.round(h*scale));
        Bitmap base=(nw!=w||nh!=h)?Bitmap.createScaledBitmap(src,nw,nh,true):src;
        Bitmap out=Bitmap.createBitmap(base.getWidth(),base.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix cm=new ColorMatrix(new float[]{1.25f,0,0,0,-28,0,1.25f,0,0,-28,0,0,1.25f,0,-28,0,0,0,1,0});
        p.setColorFilter(new ColorMatrixColorFilter(cm));c.drawBitmap(base,0,0,p);
        Paint sharp=new Paint(Paint.ANTI_ALIAS_FLAG);sharp.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{1.18f,-0.09f,-0.09f,0,0,-0.09f,1.18f,-0.09f,0,0,-0.09f,-0.09f,1.18f,0,0,0,0,0,1,0})));c.drawBitmap(out,0,0,sharp);
        if(base!=src)base.recycle();return out;
    }
    private int dniFrontScore(String text){String u=normalizeOcrIdentity(text);int s=0;if(u.contains("REINO DE ESPANA"))s+=8;if(u.contains("DOCUMENTO NACIONAL DE IDENTIDAD"))s+=6;if(u.contains("DNI"))s+=3;if(u.contains("NOMBRE"))s+=2;if(u.contains("APELLIDOS"))s+=2;if(u.contains("FOTO"))s+=1;return s;}
    private int dniBackScore(String text){String u=normalizeOcrIdentity(text);int s=0;if(u.contains("IDESP"))s+=10;if(u.contains("<<<<"))s+=8;if(u.contains("LUGAR DE NACIMIENTO"))s+=4;if(u.contains("DOMICILIO"))s+=4;if(u.contains("EQUIPO"))s+=1;return s;}
    private void orderDniSides(String aText,String bText){
        int af=dniFrontScore(aText),ab=dniBackScore(aText),bf=dniFrontScore(bText),bb=dniBackScore(bText);
        if((bf+bb)>(af+ab) || (ab>af && bf>bb)){
            Bitmap tb=frontBitmap;frontBitmap=backBitmap;backBitmap=tb;String ts=frontImagePath;frontImagePath=backImagePath;backImagePath=ts;
        }else if(ab>af && bf>=bb){
            Bitmap tb=frontBitmap;frontBitmap=backBitmap;backBitmap=tb;String ts=frontImagePath;frontImagePath=backImagePath;backImagePath=ts;
        }
    }
'''
if 'stabilizeOcrBitmap' not in s:
    marker='    private void reviewDniPair(){'
    s=s.replace(marker,helpers+'\n'+marker,1)

s=replace_method(s,'    private Bitmap loadBitmap(Uri u)throws Exception{',r'''    private Bitmap loadBitmap(Uri u)throws Exception{
        try(InputStream in=getContentResolver().openInputStream(u)){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap b=BitmapFactory.decodeStream(in,null,o);if(b==null)throw new IOException("imagen vacía");return b;
        }
    }''')

s=replace_method(s,'    private void processDniPairOcr(){',r'''    private void processDniPairOcr(){
        if(frontBitmap==null||backBitmap==null)return;
        Bitmap frontOcr=stabilizeOcrBitmap(frontBitmap),backOcr=stabilizeOcrBitmap(backBitmap);
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(frontOcr,0)).addOnSuccessListener(f->{
            final String ft=f==null?"":f.getText();
            r.process(InputImage.fromBitmap(backOcr,0)).addOnSuccessListener(b->{
                final String bt=b==null?"":b.getText();
                frontText=ft;backText=bt;orderDniSides(ft,bt);
                currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;r.close();
                showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));
                frontOcr.recycle();backOcr.recycle();
            }).addOnFailureListener(e->{r.close();frontOcr.recycle();backOcr.recycle();Toast.makeText(this,"OCR reverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});
        }).addOnFailureListener(e->{r.close();frontOcr.recycle();backOcr.recycle();Toast.makeText(this,"OCR anverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }''')

s=replace_method(s,'    private void processImage(){',r'''    private void processImage(){
        if(currentBitmap==null){Toast.makeText(this,"Primero selecciona un JPEG válido.",Toast.LENGTH_LONG).show();return;}
        Bitmap ocrBitmap=stabilizeOcrBitmap(currentBitmap);TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(ocrBitmap,0)).addOnSuccessListener(t->{String text=t.getText()==null?"":t.getText();if(side==2)backText=text;else frontText=text;r.close();ocrBitmap.recycle();showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));}).addOnFailureListener(e->{r.close();ocrBitmap.recycle();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }''')

# Stabilize policy camera/PDF OCR too, without changing the stored original document.
s=replace_method(s,'    private void ocrPolicyPagesSequentially(final int index,final StringBuilder all){',r'''    private void ocrPolicyPagesSequentially(final int index,final StringBuilder all){
        if(index>=policyPageBitmaps.size()){
            String raw=all.toString().trim();try{JSONObject p=OcasoPolicyParser.parse(raw);showPolicyReview(p,raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}return;
        }
        Toast.makeText(this,"Leyendo página "+(index+1)+" de "+policyPageBitmaps.size()+"…",Toast.LENGTH_SHORT).show();
        Bitmap ocrBitmap=stabilizeOcrBitmap(policyPageBitmaps.get(index));TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(ocrBitmap,0)).addOnSuccessListener(result->{if(result!=null&&!result.getText().trim().isEmpty()){if(all.length()>0)all.append("\\n\\n===== PÁGINA ").append(index+1).append(" =====\\n");all.append(result.getText());}r.close();ocrBitmap.recycle();ocrPolicyPagesSequentially(index+1,all);}).addOnFailureListener(e->{r.close();ocrBitmap.recycle();all.append("\\n\\n===== PÁGINA ").append(index+1).append(" =====\\n");all.append("[OCR ERROR: ").append(e.getMessage()==null?"":e.getMessage()).append("]");ocrPolicyPagesSequentially(index+1,all);});
    }''')

# For uploaded two-image selection, do not trust picker order; classify after OCR.
old='if(n>=2){Uri f=data.getClipData().getItemAt(0).getUri();Uri b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(f);backBitmap=loadBitmap(b);frontImagePath=f.toString();backImagePath=b.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentKind=1;reviewDniPair();return;}'
new='if(n>=2){Uri f=data.getClipData().getItemAt(0).getUri();Uri b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(f);backBitmap=loadBitmap(b);frontImagePath=f.toString();backImagePath=b.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentKind=1;reviewDniPair();return;}'
if old not in s: raise SystemExit('multi-image block not found')
s=s.replace(old,new,1)

P.write_text(s,encoding='utf-8');print('OCR stabilization v4 applied')
