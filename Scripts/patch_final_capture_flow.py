from pathlib import Path

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=MAIN.read_text(encoding='utf-8')

def replace_method(src,sig,replacement):
    start=src.find(sig)
    if start<0: raise SystemExit('method not found: '+sig)
    brace=src.find('{',start); depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return src[:start]+replacement+src[i+1:]
    raise SystemExit('unbalanced method: '+sig)

# Remove the dedicated top-level Pólizas navigation button. Policies remain reachable from client records.
old='Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false),p=btn("Pólizas",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());p.setOnClickListener(v->policies());\n        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(p,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);'
new='Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());\n        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);'
if old not in s: print('top nav old block not found')
else: s=s.replace(old,new,1)

# Remove home shortcut that exposes a Pólizas button.
old='        Button ps=btn("📄  PÓLIZAS",false);ps.setOnClickListener(v->policies());body.addView(ps,new LinearLayout.LayoutParams(-1,dp(58)));\n'
if old in s:s=s.replace(old,'',1)

# OCR screen: camera is the primary document intake; one Documents button handles files.
ocr='''    private void ocrPage(){
        shell("OCR","Entrada inteligente de documentos");
        body.addView(tv("DOCUMENTOS",24,TEXT,true));
        body.addView(tv("Haz una foto o añade un archivo. RgaPro detectará automáticamente si es un DNI/NIE, una póliza o cualquier otro documento compatible.",14,MUTED,false));
        Button cam=btn("📷  CÁMARA",true);
        cam.setOnClickListener(v->{side=0;frontBitmap=null;backBitmap=null;frontText=\"\";backText=\"\";frontImagePath=\"\";backImagePath=\"\";policyPageUris.clear();policyPageBitmaps.clear();policyCameraFlow=false;takePhoto();});
        body.addView(cam,new LinearLayout.LayoutParams(-1,dp(64)));
        Button docs=btn("📎  DOCUMENTOS",false);
        docs.setOnClickListener(v->chooseUniversalFile());
        body.addView(docs,new LinearLayout.LayoutParams(-1,dp(62)));
    }
'''
s=replace_method(s,'    private void ocrPage(){',ocr)

# More reliable ContentResolver decoding for gallery/document-provider URIs.
oldload='    private Bitmap loadBitmap(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(b==null)throw new IOException("imagen vacía");return b;}'
newload='''    private Bitmap loadBitmap(Uri u)throws Exception{
        if(u==null)throw new IOException("archivo no disponible");
        Bitmap b=null;
        try(InputStream in=getContentResolver().openInputStream(u)){
            if(in!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;b=BitmapFactory.decodeStream(in,null,o);}
        }
        if(b==null){
            try(ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(u,"r")){
                if(pfd!=null)b=BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
            }
        }
        if(b==null)throw new IOException("imagen vacía o formato no compatible");
        return b;
    }'''
if oldload in s:s=s.replace(oldload,newload,1)
else: print('loadBitmap old block not found')

# Add first-shot camera detection and generic multipage document capture helpers.
marker='    private void processSelectedDniFiles(){'
helpers='''    private void detectFirstCameraDocument(){
        if(currentBitmap==null){Toast.makeText(this,"La foto no se pudo leer.",Toast.LENGTH_LONG).show();return;}
        final Bitmap source=currentBitmap;
        final int[] angles=new int[]{0,90,180,270};
        detectCameraAtAngle(source,angles,0,new String[]{\"\",\"\"},new float[]{-1,-1});
    }

    private void detectCameraAtAngle(Bitmap source,int[] angles,int index,String[] best,float[] bestScore){
        if(index>=angles.length){
            if(bestScore[0]>=0){
                Bitmap normalized=DniImagePreprocessor.rotate(source,angles[(int)bestScore[1]]);
                currentBitmap=normalized;
            }
            if(bestScore[0]>=6 && isLikelyDniText(best[0])){
                frontBitmap=DniImagePreprocessor.prepare(currentBitmap);
                if(frontBitmap==null)frontBitmap=currentBitmap;
                frontImagePath=currentImagePath;
                frontText=best[0];side=2;
                new AlertDialog.Builder(this).setTitle("DNI/NIE detectado").setMessage("Se ha detectado un DNI/NIE. Ahora toma el REVERSO.").setPositiveButton("📷 TOMAR REVERSO",(d,w)->takePhoto()).setNegativeButton("Cancelar",null).show();
            }else{
                policyPageUris.clear();policyPageBitmaps.clear();
                policyPageUris.add(cameraUri);policyPageBitmaps.add(currentBitmap);policyCameraFlow=true;
                askPolicyPageNext();
            }
            return;
        }
        final int angle=angles[index];
        Bitmap test=DniImagePreprocessor.rotate(source,angle);
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(test,0)).addOnSuccessListener(t->{
            String text=t==null?\"\":t.getText();r.close();
            String u=normalizeOcrIdentity(text).toUpperCase(Locale.ROOT);float score=0;
            if(u.contains(\"DOCUMENTO NACIONAL\"))score+=5;
            if(u.contains(\"APELLIDOS\"))score+=3;
            if(u.contains(\"NOMBRE\"))score+=2;
            if(u.contains(\"NACIONALIDAD\"))score+=2;
            if(u.contains(\"SEXO\"))score+=1;
            if(u.contains(\"IDESP\"))score+=5;
            if(extractDniRobust(u).length()==9)score+=4;
            if(score>bestScore[0]){best[0]=text;bestScore[0]=score;bestScore[1]=angle;}
            detectCameraAtAngle(source,angles,index+1,best,bestScore);
        }).addOnFailureListener(e->{r.close();detectCameraAtAngle(source,angles,index+1,best,bestScore);});
    }

'''
if marker in s and 'private void detectFirstCameraDocument()' not in s:s=s.replace(marker,helpers+marker,1)

# Replace CAMERA handling in the universal onActivityResult implementation.
oldcam='''            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;side=0;reviewDniPair();return;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;side=2;new AlertDialog.Builder(this).setTitle("Ahora toma el REVERSO").setMessage("RgaPro identificará automáticamente qué foto es anverso y cuál reverso.").setPositiveButton("📷 TOMAR REVERSO",(d,w)->takePhoto()).setNegativeButton("Cancelar",null).show();return;}}
            processSelectedSingleFile();'''
newcam='''            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){
                if(side==2){
                    backBitmap=DniImagePreprocessor.prepare(currentBitmap);if(backBitmap==null)backBitmap=currentBitmap;backImagePath=currentImagePath;side=0;reviewDniPair();return;
                }
                detectFirstCameraDocument();return;
            }
            processSelectedSingleFile();'''
if oldcam in s:s=s.replace(oldcam,newcam,1)
else: print('camera block not found')

MAIN.write_text(s,encoding='utf-8')
print('Final capture/navigation patch applied')
