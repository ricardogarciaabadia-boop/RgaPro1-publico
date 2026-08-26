from pathlib import Path

P = Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s = P.read_text(encoding='utf-8')

old = 'private String frontText="", backText="", currentImagePath=""; private Bitmap currentBitmap, previewBitmap;'
new = 'private String frontText="", backText="", currentImagePath="", frontImagePath="", backImagePath=""; private Bitmap currentBitmap, previewBitmap, frontBitmap, backBitmap;'
if old in s:
    s = s.replace(old, new, 1)

old = '''    private void chooseImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,IMAGE);}'''
new = '''    private void chooseImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,IMAGE);}'''
if old in s:
    s = s.replace(old, new, 1)

old = '''    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;documentUri=u;try{if(data!=null&&(request==IMAGE||request==PDF)){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}if(request==PDF){documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();ocrPage();}else{documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();ocrPage();}}catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}}'''
new = '''    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        try{
            if(request==PDF){Uri u=data==null?null:data.getData();if(u==null)return;documentUri=u;documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();ocrPage();return;}
            if(request==IMAGE && data!=null && data.getClipData()!=null){
                int n=data.getClipData().getItemCount();
                if(n>=2){Uri f=data.getClipData().getItemAt(0).getUri();Uri b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(f);backBitmap=loadBitmap(b);frontImagePath=f.toString();backImagePath=b.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentKind=1;reviewDniPair();return;}
            }
            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}
            else {frontBitmap=currentBitmap;frontImagePath=currentImagePath;}
            reviewDniPair();
        }catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }'''
if old in s:
    s = s.replace(old, new, 1)

marker = '    private void processCurrentDocument(){'
if marker in s and 'private void reviewDniPair()' not in s:
    methods = '''    private void reviewDniPair(){\n        LinearLayout wrap=col();wrap.setPadding(dp(8),dp(4),dp(8),dp(4));\n        wrap.addView(tv("COMPRUEBA ANVERSO Y REVERSO ANTES DEL OCR",16,BLUE,true));\n        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);\n        ImageView f=new ImageView(this),b=new ImageView(this);f.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setScaleType(ImageView.ScaleType.FIT_CENTER);\n        f.setBackground(box(Color.WHITE,12));b.setBackground(box(Color.WHITE,12));if(frontBitmap!=null)f.setImageBitmap(frontBitmap);if(backBitmap!=null)b.setImageBitmap(backBitmap);\n        row.addView(f,new LinearLayout.LayoutParams(0,dp(210),1));row.addView(b,new LinearLayout.LayoutParams(0,dp(210),1));wrap.addView(row);\n        wrap.addView(tv((frontBitmap!=null?"✓ Anverso":"✗ Falta anverso")+"     "+(backBitmap!=null?"✓ Reverso":"✗ Falta reverso"),15,TEXT,true));\n        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Revisión del DNI/NIE").setView(wrap).setNegativeButton("RECHAZAR / CAMBIAR",null).setPositiveButton("PROCESAR OCR",null).create();\n        dlg.setOnShowListener(v->{Button ok=dlg.getButton(AlertDialog.BUTTON_POSITIVE);ok.setOnClickListener(x->{if(frontBitmap==null||backBitmap==null){Toast.makeText(this,"Un DNI/NIE necesita ANVERSO y REVERSO.",Toast.LENGTH_LONG).show();return;}dlg.dismiss();processDniPairOcr();});});dlg.show();\n    }\n\n    private void processDniPairOcr(){\n        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);\n        if(frontBitmap==null||backBitmap==null){r.close();return;}\n        r.process(InputImage.fromBitmap(frontBitmap,0)).addOnSuccessListener(f->{frontText=f==null?"":f.getText();r.process(InputImage.fromBitmap(backBitmap,0)).addOnSuccessListener(b->{backText=b==null?"":b.getText();currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;r.close();showIdentityReview(parseEssential(frontText+"\\n"+backText));}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR reverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR anverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});\n    }\n\n'''
    s = s.replace(marker, methods + marker, 1)

old = 'x.put("image",currentImagePath);if(!x.has("policies"))x.put("policies",new JSONArray());'
new = 'x.put("image",currentImagePath);x.put("frontImage",frontImagePath);x.put("backImage",backImagePath);if(!x.has("policies"))x.put("policies",new JSONArray());'
if old in s:
    s = s.replace(old, new, 1)

P.write_text(s, encoding='utf-8')
print('DNI two-side JPEG workflow patched')
