from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s = MAIN.read_text(encoding='utf-8')


def replace_method(src, signature, replacement):
    start = src.find(signature)
    if start < 0:
        raise SystemExit('method not found: ' + signature)
    brace = src.find('{', start)
    if brace < 0:
        raise SystemExit('opening brace not found: ' + signature)
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + replacement + src[i + 1:]
    raise SystemExit('unbalanced method: ' + signature)

# State for a photographed, multi-page policy document.
if 'POLICY_CAMERA=8104' not in s:
    s = s.replace('private static final int CAMERA=8101, IMAGE=8102, PDF=8103;',
                  'private static final int CAMERA=8101, IMAGE=8102, PDF=8103, POLICY_CAMERA=8104, POLICY_CAMERA_PERMISSION=8105;')
if 'policyPageUris' not in s:
    s = s.replace('private int documentKind=0; // 1 imagen, 2 PDF',
                  'private int documentKind=0; // 1 imagen, 2 PDF\n    private final ArrayList<Uri> policyPageUris = new ArrayList<>();\n    private final ArrayList<Bitmap> policyPageBitmaps = new ArrayList<>();\n    private boolean policyCameraFlow = false;')

helpers = r'''    private void startPolicyPageCamera(){
        try{
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.CAMERA}, POLICY_CAMERA_PERMISSION);return;
            }
            File dir=new File(getExternalFilesDir("documents"),"policy_pages");
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear la carpeta de páginas");
            File f=File.createTempFile("poliza_pagina_",".jpg",dir);
            cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("photo",cameraUri));
            policyCameraFlow=true;
            startActivityForResult(i,POLICY_CAMERA);
        }catch(Exception e){Toast.makeText(this,"No se pudo abrir la cámara: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private Bitmap loadPolicyPageForOcr(Uri u) throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Página no disponible");BitmapFactory.decodeStream(in,null,o);}
        int max=2600,sample=1;while(Math.max(o.outWidth,o.outHeight)/(double)sample>max)sample*=2;
        BitmapFactory.Options real=new BitmapFactory.Options();real.inSampleSize=Math.max(1,sample);real.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Página no disponible");Bitmap b=BitmapFactory.decodeStream(in,null,real);if(b==null)throw new IOException("No se pudo decodificar la página");return b;}
    }

    private File buildPolicyPdf() throws Exception{
        File dir=new File(getExternalFilesDir("documents"),"policy_pages");if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear el archivo");
        File out=File.createTempFile("poliza_documento_",".pdf",dir);
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        try{
            for(int i=0;i<policyPageBitmaps.size();i++){
                Bitmap b=policyPageBitmaps.get(i);if(b==null)continue;
                int w=Math.max(595,b.getWidth());int h=(int)(w*(b.getHeight()/(double)Math.max(1,b.getWidth())));
                android.graphics.pdf.PdfDocument.PageInfo info=new android.graphics.pdf.PdfDocument.PageInfo.Builder(w,h,i+1).createPage();
                android.graphics.pdf.PdfDocument.Page page=pdf.startPage(info);Canvas c=page.getCanvas();Rect src=new Rect(0,0,b.getWidth(),b.getHeight());RectF dst=new RectF(0,0,w,h);c.drawBitmap(b,src,dst,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));pdf.finishPage(page);
            }
            try(FileOutputStream os=new FileOutputStream(out)){pdf.writeTo(os);}
        }finally{pdf.close();}
        return out;
    }

    private void askPolicyPageNext(){
        int n=policyPageUris.size();
        LinearLayout box=col();box.setPadding(dp(8),dp(4),dp(8),dp(4));
        box.addView(tv("PÁGINA "+n+" CAPTURADA",18,BLUE,true));
        box.addView(tv("Puedes seguir fotografiando el documento. El orden de las páginas se conserva.",14,MUTED,false));
        if(!policyPageBitmaps.isEmpty()){ImageView iv=new ImageView(this);iv.setImageBitmap(policyPageBitmaps.get(policyPageBitmaps.size()-1));iv.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(iv,new LinearLayout.LayoutParams(-1,dp(260)));}
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Documento de póliza").setView(box).setNegativeButton("TERMINAR Y LEER",null).setPositiveButton("AÑADIR OTRA PÁGINA",null).create();
        dlg.setOnShowListener(v->{Button more=dlg.getButton(AlertDialog.BUTTON_POSITIVE),done=dlg.getButton(AlertDialog.BUTTON_NEGATIVE);more.setOnClickListener(x->{dlg.dismiss();startPolicyPageCamera();});done.setOnClickListener(x->{dlg.dismiss();finishPolicyPhotoDocument();});});
        dlg.show();
    }

    private void finishPolicyPhotoDocument(){
        if(policyPageUris.isEmpty()){Toast.makeText(this,"No se ha fotografiado ninguna página.",Toast.LENGTH_LONG).show();return;}
        try{
            File pdf=buildPolicyPdf();documentUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pdf);documentKind=2;previewBitmap=policyPageBitmaps.isEmpty()?null:policyPageBitmaps.get(0);currentImagePath=documentUri.toString();
            ocrPolicyPagesSequentially(0,new StringBuilder());
        }catch(Exception e){Toast.makeText(this,"No se pudo preparar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void ocrPolicyPagesSequentially(final int index,final StringBuilder all){
        if(index>=policyPageBitmaps.size()){
            String raw=all.toString().trim();
            try{JSONObject p=OcasoPolicyParser.parse(raw);showPolicyReview(p,raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            return;
        }
        Toast.makeText(this,"Leyendo página "+(index+1)+" de "+policyPageBitmaps.size()+"…",Toast.LENGTH_SHORT).show();
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(policyPageBitmaps.get(index),0)).addOnSuccessListener(result->{
            if(result!=null&&!result.getText().trim().isEmpty()){if(all.length()>0)all.append("\n\n===== PÁGINA ").append(index+1).append(" =====\n");all.append(result.getText());}
            r.close();ocrPolicyPagesSequentially(index+1,all);
        }).addOnFailureListener(e->{r.close();all.append("\n\n===== PÁGINA ").append(index+1).append(" =====\n");all.append("[OCR ERROR: ").append(e.getMessage()==null?"":e.getMessage()).append("]");ocrPolicyPagesSequentially(index+1,all);});
    }

'''
if 'startPolicyPageCamera()' not in s:
    anchor='    private void policyDetail(JSONObject p){'
    s=s.replace(anchor,helpers+anchor,1)

# Add a dedicated entry point in the Policies screen.
old='Button scan=btn("📄 SUBIR PÓLIZA PDF",true);scan.setOnClickListener(v->choosePdf());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(60)));'
new='Button scan=btn("📄 SUBIR PÓLIZA PDF",true);scan.setOnClickListener(v->choosePdf());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(60)));Button cameraPolicy=btn("📷 FOTOGRAFIAR PÓLIZA · VARIAS PÁGINAS",true);cameraPolicy.setOnClickListener(v->{policyPageUris.clear();policyPageBitmaps.clear();policyCameraFlow=true;startPolicyPageCamera();});body.addView(cameraPolicy,new LinearLayout.LayoutParams(-1,dp(64)));'
if old in s and 'FOTOGRAFIAR PÓLIZA · VARIAS PÁGINAS' not in s:
    s=s.replace(old,new,1)

# Replace the current result handler with one that also accepts the multi-page policy camera flow.
new_result=r'''    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        try{
            if(request==POLICY_CAMERA){
                if(cameraUri==null)throw new IOException("No se recibió la foto");
                Bitmap page=loadPolicyPageForOcr(cameraUri);policyPageUris.add(cameraUri);policyPageBitmaps.add(page);policyCameraFlow=true;askPolicyPageNext();return;
            }
            if(request==PDF){Uri u=data==null?null:data.getData();if(u==null)return;documentUri=u;documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();ocrPage();return;}
            if(request==IMAGE && data!=null && data.getClipData()!=null){
                int n=data.getClipData().getItemCount();
                if(n>=2){Uri f=data.getClipData().getItemAt(0).getUri();Uri b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(f);backBitmap=loadBitmap(b);frontImagePath=f.toString();backImagePath=b.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentKind=1;reviewDniPair();return;}
            }
            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}
            else if(frontBitmap==null){frontBitmap=currentBitmap;frontImagePath=currentImagePath;}
            else {backBitmap=currentBitmap;backImagePath=currentImagePath;}
            if(frontBitmap!=null && backBitmap==null){Toast.makeText(this,"Anverso cargado. Ahora selecciona el REVERSO.",Toast.LENGTH_LONG).show();ocrPage();}
            else {reviewDniPair();}
        }catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }'''
s=replace_method(s,'    @Override protected void onActivityResult(int request,int result,Intent data){',new_result)

# Support the dedicated camera permission request without disturbing the existing permission flow.
if 'POLICY_CAMERA_PERMISSION &&' not in s:
    sig='    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){'
    pos=s.find(sig)
    branch='if(requestCode==POLICY_CAMERA_PERMISSION){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startPolicyPageCamera();else Toast.makeText(this,"Permiso de cámara denegado.",Toast.LENGTH_LONG).show();return;}'
    if pos>=0:
        brace=s.find('{',pos);s=s[:brace+1]+branch+s[brace+1:]
    else:
        anchor='    private void startPolicyPageCamera(){'
        method='    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==POLICY_CAMERA_PERMISSION){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startPolicyPageCamera();else Toast.makeText(this,"Permiso de cámara denegado.",Toast.LENGTH_LONG).show();}}\n\n'
        s=s.replace(anchor,method+anchor,1)

MAIN.write_text(s,encoding='utf-8')
print('Multi-page policy camera OCR workflow applied')
