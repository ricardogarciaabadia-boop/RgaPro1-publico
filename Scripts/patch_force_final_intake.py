from pathlib import Path
import re

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

# FINAL NAVIGATION: only Inicio, Clientes and OCR. No top-level Pólizas button.
def replace_shell(src):
    sig='    private void shell(String title,String subtitle){'
    start=src.find(sig)
    if start<0: raise SystemExit('shell not found')
    brace=src.find('{',start); depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:
                body='''    private void shell(String title,String subtitle){
        LinearLayout root=col();root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(8),dp(12),dp(8));top.setBackgroundColor(NAVY);
        try{ImageView icon=new ImageView(this);icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);top.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));}catch(Exception ignored){}
        LinearLayout tt=col();tt.addView(tv("RgaPro",24,Color.WHITE,true));tt.addView(tv(subtitle,13,Color.LTGRAY,false));top.addView(tt,new LinearLayout.LayoutParams(0,dp(60),1));root.addView(top,new LinearLayout.LayoutParams(-1,dp(76)));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(NAVY);
        Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());
        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);
        ScrollView sc=new ScrollView(this);body=col();body.setPadding(dp(16),dp(16),dp(16),dp(24));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
'''
                return src[:start]+body+src[i+1:]
    raise SystemExit('shell unbalanced')
s=replace_shell(s)

# Remove every remaining home-level Pólizas shortcut.
s=re.sub(r'\s*Button ps=btn\("📄  PÓLIZAS",false\);.*?\n', '\n', s, count=1)

# FINAL OCR screen: camera + one Documents button.
s=replace_method(s,'    private void ocrPage(){','''    private void ocrPage(){
        shell("OCR","Entrada inteligente de documentos");
        body.addView(tv("DOCUMENTOS",24,TEXT,true));
        body.addView(tv("Haz una foto o añade un archivo. RgaPro detectará automáticamente si es un DNI/NIE, una póliza o cualquier otro documento y lo asociará al cliente existente o creará uno nuevo.",14,MUTED,false));
        Button cam=btn("📷  CÁMARA",true);
        cam.setOnClickListener(v->{side=0;frontBitmap=null;backBitmap=null;frontText="";backText="";frontImagePath="";backImagePath="";policyPageUris.clear();policyPageBitmaps.clear();policyCameraFlow=false;takePhoto();});
        body.addView(cam,new LinearLayout.LayoutParams(-1,dp(64)));
        Button docs=btn("📎  DOCUMENTOS",false);
        docs.setOnClickListener(v->chooseUniversalFile());
        body.addView(docs,new LinearLayout.LayoutParams(-1,dp(62)));
    }
''')

# Universal file chooser. Multiple images are accepted; PDF and common image formats are accepted.
choose='''    private void chooseUniversalFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/jpg","image/png","image/webp","application/pdf"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,8106);
    }
'''
if '    private void chooseUniversalFile()' not in s:
    marker='    private void chooseImage()'
    s=s.replace(marker,choose+'\n'+marker,1)

# Reliable decoder: do not reject valid document-provider URIs as "imagen vacía".
s=replace_method(s,'    private Bitmap loadBitmap(Uri u)throws Exception{','''    private Bitmap loadBitmap(Uri u)throws Exception{
        if(u==null)throw new IOException("archivo no disponible");
        Bitmap b=null;
        try(InputStream in=getContentResolver().openInputStream(u)){
            if(in!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;b=BitmapFactory.decodeStream(in,null,o);}
        }
        if(b==null){try(ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(u,"r")){if(pfd!=null)b=BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());}}
        if(b==null)throw new IOException("imagen vacía o formato no compatible");
        return b;
    }
''')

# Detect a camera document in all four rotations. If it is DNI/NIE, require the reverse; otherwise enter multipage capture.
helper='''    private void detectFirstCameraDocument(){
        if(currentBitmap==null){Toast.makeText(this,"La foto no se pudo leer.",Toast.LENGTH_LONG).show();return;}
        final int[] angles={0,90,180,270};
        detectCameraAngle(currentBitmap,angles,0,"",-1,-1);
    }
    private void detectCameraAngle(Bitmap source,int[] angles,int index,String bestText,float bestScore,int bestAngle){
        if(index>=angles.length){
            if(bestScore>=6&&isLikelyDniText(bestText)){
                Bitmap rotated=DniImagePreprocessor.rotate(source,bestAngle);Bitmap prepared=DniImagePreprocessor.prepare(rotated);if(prepared!=null){currentBitmap=prepared;previewBitmap=prepared;frontBitmap=prepared;frontImagePath=currentImagePath;}
                frontText=bestText;side=2;
                new AlertDialog.Builder(this).setTitle("DNI/NIE detectado").setMessage("Se ha detectado un DNI/NIE. Ahora toma el REVERSO.").setPositiveButton("📷 TOMAR REVERSO",(d,w)->takePhoto()).setNegativeButton("Cancelar",null).show();
            }else{
                policyPageUris.clear();policyPageBitmaps.clear();policyPageUris.add(cameraUri);policyPageBitmaps.add(DniImagePreprocessor.deskew(source));policyCameraFlow=true;askPolicyPageNext();
            }
            return;
        }
        int angle=angles[index];Bitmap test=DniImagePreprocessor.rotate(source,angle);TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(test,0)).addOnSuccessListener(t->{String text=t==null?"":t.getText();r.close();String u=normalizeOcrIdentity(text).toUpperCase(Locale.ROOT);float score=0;if(u.contains("DOCUMENTO NACIONAL"))score+=5;if(u.contains("APELLIDOS"))score+=3;if(u.contains("NOMBRE"))score+=2;if(u.contains("NACIONALIDAD"))score+=2;if(u.contains("SEXO"))score+=1;if(u.contains("IDESP"))score+=5;if(extractDniRobust(u).length()==9)score+=4;detectCameraAngle(source,angles,index+1,score>bestScore?text:bestText,Math.max(score,bestScore),score>bestScore?angle:bestAngle);}).addOnFailureListener(e->{r.close();detectCameraAngle(source,angles,index+1,bestText,bestScore,bestAngle);});
    }

'''
if '    private void detectFirstCameraDocument()' not in s:
    s=s.replace('    private void processSelectedDniFiles(){',helper+'    private void processSelectedDniFiles(){',1)

# FINAL activity result: one file auto-detects PDF/image/DNI; multiple files are classified before routing.
activity='''    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        try{
            if(request==POLICY_CAMERA){
                if(cameraUri==null)throw new IOException("No se recibió la foto");
                Bitmap page=loadPolicyPageForOcr(cameraUri);page=DniImagePreprocessor.deskew(page);policyPageUris.add(cameraUri);policyPageBitmaps.add(page);policyCameraFlow=true;askPolicyPageNext();return;
            }
            if(request==8106){
                if(data==null)return;ArrayList<Uri> files=new ArrayList<>();
                if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)files.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)files.add(data.getData());
                if(files.isEmpty())return;
                if(files.size()>=2){
                    ArrayList<Bitmap> imgs=new ArrayList<>();for(Uri u:files){String mt=getContentResolver().getType(u);if(mt!=null&&mt.toLowerCase(Locale.ROOT).contains("pdf"))continue;imgs.add(loadBitmap(u));}
                    if(imgs.size()>=2){
                        frontBitmap=imgs.get(0);backBitmap=imgs.get(1);frontImagePath=files.get(0).toString();backImagePath=files.get(1).toString();documentKind=1;documentUri=files.get(0);currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;
                        TextRecognizer rr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);rr.process(InputImage.fromBitmap(frontBitmap,0)).addOnSuccessListener(t->{String a=t==null?"":t.getText();rr.close();if(isLikelyDniText(a)){processSelectedDniFiles();}else{policyPageUris.clear();policyPageBitmaps.clear();for(int i=0;i<imgs.size();i++){Bitmap z=DniImagePreprocessor.deskew(imgs.get(i));policyPageBitmaps.add(z);policyPageUris.add(files.get(i));}finishPolicyPhotoDocument();}}).addOnFailureListener(e->{rr.close();processSelectedDniFiles();});return;
                    }
                }
                Uri u=files.get(0);documentUri=u;currentImagePath=u.toString();String mt=getContentResolver().getType(u);boolean pdf=(mt!=null&&mt.toLowerCase(Locale.ROOT).contains("pdf"))||u.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");documentKind=pdf?2:1;
                if(pdf){previewBitmap=renderPdfFirstPage(u);processCurrentDocument();}else{currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;processSelectedSingleFile();}return;
            }
            if(request==PDF){Uri u=data==null?null:data.getData();if(u==null)return;documentUri=u;documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();processCurrentDocument();return;}
            if(request==IMAGE){Uri u=data==null?null:data.getData();if(u==null)return;documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();processSelectedSingleFile();return;}
            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=DniImagePreprocessor.prepare(currentBitmap);if(backBitmap==null)backBitmap=currentBitmap;backImagePath=currentImagePath;side=0;reviewDniPair();}else detectFirstCameraDocument();return;}
            processSelectedSingleFile();
        }catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
'''
s=replace_method(s,'    @Override protected void onActivityResult(int request,int result,Intent data){',activity)

# Single image: try OCR, then DNI or document/policy.
s=replace_method(s,'    private void processSelectedSingleFile(){','''    private void processSelectedSingleFile(){
        if(currentBitmap==null){Toast.makeText(this,"Archivo de imagen vacío.",Toast.LENGTH_LONG).show();return;}
        Bitmap straight=DniImagePreprocessor.deskew(currentBitmap);if(straight!=null){currentBitmap=straight;previewBitmap=straight;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(currentBitmap,0)).addOnSuccessListener(t->{String raw=t==null?"":t.getText();r.close();if(isLikelyDniText(raw)){Bitmap prepared=DniImagePreprocessor.prepare(currentBitmap);if(prepared!=null){currentBitmap=prepared;previewBitmap=prepared;frontBitmap=prepared;frontImagePath=currentImagePath;}frontText=raw;showIdentityReview(parseEssentialRobust(raw));}else{try{showPolicyReview(OcasoPolicyParser.parse(raw),raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}}}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }
''')

# Make PDF OCR route automatic as well.
s=replace_method(s,'    private void processCurrentDocument(){','''    private void processCurrentDocument(){
        if(documentUri==null||documentKind==0){Toast.makeText(this,"Primero selecciona un archivo.",Toast.LENGTH_LONG).show();return;}
        if(documentKind==2){PdfOcrHelper.process(this,documentUri,new PdfOcrHelper.Callback(){public void onSuccess(String text){runOnUiThread(()->routeOcrText(text));}public void onError(Exception e){runOnUiThread(()->Toast.makeText(MainActivityV2.this,"PDF: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}else processSelectedSingleFile();
    }
    private void routeOcrText(String text){String raw=text==null?"":text;if(isLikelyDniText(raw)){frontText=raw;showIdentityReview(parseEssentialRobust(raw));}else{try{showPolicyReview(OcasoPolicyParser.parse(raw),raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}}}
''')

MAIN.write_text(s,encoding='utf-8')
print('FORCE FINAL INTAKE: 3-item navigation + camera auto-detection + universal documents + reliable file loading')
