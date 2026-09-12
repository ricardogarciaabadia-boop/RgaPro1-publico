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

ocr='''    private void ocrPage(){
        shell("OCR","Entrada automática de documentos");
        body.addView(tv("DOCUMENTO",24,TEXT,true));
        body.addView(tv("Añade un archivo. RgaPro detectará automáticamente si es DNI/NIE o póliza/documento, lo leerá y lo asociará al cliente existente o creará uno nuevo.",14,MUTED,false));
        Button file=btn("📎  ARCHIVO",true);
        file.setOnClickListener(v->chooseUniversalFile());
        body.addView(file,new LinearLayout.LayoutParams(-1,dp(64)));
        body.addView(tv("También puedes usar la cámara para un DNI/NIE cuando necesites fotografiar sus dos caras.",13,MUTED,false));
        Button cam=btn("📷  CÁMARA DNI/NIE",false);
        cam.setOnClickListener(v->{side=1;frontBitmap=null;backBitmap=null;frontImagePath="";backImagePath="";takePhoto();});
        body.addView(cam,new LinearLayout.LayoutParams(-1,dp(60)));
    }
'''
s=replace_method(s,'    private void ocrPage(){',ocr)

chooser='''    private void chooseUniversalFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/jpg","image/png","image/webp","application/pdf"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,8106);
    }

'''
marker='    private void chooseImage()'
if marker in s and 'private void chooseUniversalFile()' not in s:
    s=s.replace(marker,chooser+marker,1)
elif 'private void chooseUniversalFile()' not in s:
    raise SystemExit('chooseImage marker not found')

activity='''    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        try{
            if(request==POLICY_CAMERA){
                if(cameraUri==null)throw new IOException("No se recibió la foto");
                Bitmap page=loadPolicyPageForOcr(cameraUri);policyPageUris.add(cameraUri);policyPageBitmaps.add(page);policyCameraFlow=true;askPolicyPageNext();return;
            }
            if(request==8106){
                if(data==null)return;
                ArrayList<Uri> files=new ArrayList<>();
                if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)files.add(data.getClipData().getItemAt(i).getUri());}
                else if(data.getData()!=null)files.add(data.getData());
                if(files.isEmpty())return;
                if(files.size()>=2){
                    Uri a=files.get(0),b=files.get(1);
                    frontBitmap=loadBitmap(a);backBitmap=loadBitmap(b);
                    frontImagePath=a.toString();backImagePath=b.toString();
                    documentKind=1;documentUri=a;currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=a.toString();
                    processSelectedDniFiles();return;
                }
                Uri u=files.get(0);documentUri=u;currentImagePath=u.toString();
                String mime=getContentResolver().getType(u);boolean pdf="application/pdf".equalsIgnoreCase(mime);
                if(!pdf)pdf=u.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
                documentKind=pdf?2:1;
                if(pdf){previewBitmap=renderPdfFirstPage(u);processCurrentDocument();}
                else{currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;processSelectedSingleFile();}
                return;
            }
            if(request==PDF){
                Uri u=data==null?null:data.getData();if(u==null)return;
                documentUri=u;documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();processCurrentDocument();return;
            }
            if(request==IMAGE && data!=null && data.getClipData()!=null){
                int n=data.getClipData().getItemCount();
                if(n>=2){Uri a=data.getClipData().getItemAt(0).getUri(),b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(a);backBitmap=loadBitmap(b);frontImagePath=a.toString();backImagePath=b.toString();documentKind=1;documentUri=a;currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=a.toString();processSelectedDniFiles();return;}
            }
            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;side=0;reviewDniPair();return;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;side=2;new AlertDialog.Builder(this).setTitle("Ahora toma el REVERSO").setMessage("RgaPro identificará automáticamente qué foto es anverso y cuál reverso.").setPositiveButton("📷 TOMAR REVERSO",(d,w)->takePhoto()).setNegativeButton("Cancelar",null).show();return;}}
            processSelectedSingleFile();
        }catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
'''
s=replace_method(s,'    @Override protected void onActivityResult(int request,int result,Intent data){',activity)

single='''    private boolean isLikelyDniText(String text){
        String u=normalizeOcrIdentity(text).toUpperCase(Locale.ROOT);
        int score=0;
        if(u.contains("DOCUMENTO NACIONAL"))score+=5;
        if(u.contains("APELLIDOS"))score+=3;
        if(u.contains("NOMBRE"))score+=2;
        if(u.contains("NACIONALIDAD"))score+=2;
        if(u.contains("SEXO"))score+=1;
        if(u.contains("IDESP"))score+=5;
        if(u.contains("VOLANTE<CAR"))score+=2;
        if(extractDniRobust(u).length()==9)score+=4;
        return score>=6;
    }

    private void processSelectedDniFiles(){
        if(frontBitmap==null||backBitmap==null){processSelectedSingleFile();return;}
        DniOcrEngine.process(frontBitmap,backBitmap,new DniOcrEngine.Callback(){
            public void onSuccess(String ft,String bt,Bitmap pf,Bitmap pb){runOnUiThread(()->{frontBitmap=pf;backBitmap=pb;frontText=ft;backText=bt;previewBitmap=pf;currentBitmap=pf;showIdentityReview(parseEssentialRobust(ft+"\\n"+bt));});}
            public void onFailure(Exception e){runOnUiThread(()->Toast.makeText(MainActivityV2.this,"DNI/NIE: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        });
    }

    private void processSelectedSingleFile(){
        if(currentBitmap==null){Toast.makeText(this,"Archivo de imagen vacío.",Toast.LENGTH_LONG).show();return;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(currentBitmap,0)).addOnSuccessListener(t->{
            String raw=t==null?"":t.getText();r.close();
            if(isLikelyDniText(raw)){
                Bitmap prepared=DniImagePreprocessor.prepare(currentBitmap);if(prepared!=null){currentBitmap=prepared;previewBitmap=prepared;frontBitmap=prepared;frontImagePath=currentImagePath;}
                frontText=raw;showIdentityReview(parseEssentialRobust(raw));
            }else{
                try{showPolicyReview(OcasoPolicyParser.parse(raw),raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            }
        }).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }

'''
marker='    private void processCurrentDocument(){'
if marker not in s: raise SystemExit('processCurrentDocument marker not found')
s=s.replace(marker,single+marker,1)

policy='''    private void processCurrentDocument(){
        if(documentUri==null||documentKind==0){Toast.makeText(this,"Primero selecciona un archivo.",Toast.LENGTH_LONG).show();return;}
        if(documentKind==2){
            PdfOcrHelper.process(this,documentUri,new PdfOcrHelper.Callback(){
                public void onSuccess(String text){runOnUiThread(()->routeOcrText(text));}
                public void onError(Exception e){runOnUiThread(()->Toast.makeText(MainActivityV2.this,"PDF: "+e.getMessage(),Toast.LENGTH_LONG).show());}
            });
        }else processSelectedSingleFile();
    }

    private void routeOcrText(String text){
        String raw=text==null?"":text;
        if(isLikelyDniText(raw)){frontText=raw;showIdentityReview(parseEssentialRobust(raw));return;}
        try{showPolicyReview(OcasoPolicyParser.parse(raw),raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

'''
s=replace_method(s,'    private void processCurrentDocument(){',policy)

MAIN.write_text(s,encoding='utf-8')
print('Unified automatic file ingestion applied')
