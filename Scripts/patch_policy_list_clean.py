from pathlib import Path

P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')

def replace_method(src, sig, replacement):
    start=src.find(sig)
    if start<0: raise SystemExit('method not found: '+sig)
    brace=src.find('{',start); depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return src[:start]+replacement+src[i+1:]
    raise SystemExit('unbalanced method: '+sig)

# Pólizas remains an internal list; creation starts from OCR, not this menu.
method=r'''    private void policies(){
        shell("Pólizas","Listado de pólizas Ocaso");
        body.addView(tv("LISTADO DE PÓLIZAS",24,TEXT,true));
        JSONArray a=clientsData(); boolean any=false;
        for(int i=0;i<a.length();i++){
            JSONObject c=a.optJSONObject(i); if(c==null)continue;
            JSONArray ps=c.optJSONArray("policies"); if(ps==null)continue;
            for(int j=0;j<ps.length();j++){
                JSONObject p=ps.optJSONObject(j); if(p==null)continue; any=true;
                String product=p.optString("policyType",p.optString("type","OCASO"));
                String number=p.optString("number","—");
                String holder=clientKey(c);
                String icon="Hogar".equalsIgnoreCase(product)?"🏠":("Decesos".equalsIgnoreCase(product)?"⚰️":("Vida".equalsIgnoreCase(product)?"❤️":"📄"));
                Button b=btn(icon+" "+product+" · "+number+System.lineSeparator()+holder,false);
                b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT); b.setOnClickListener(v->policyDetail(p));
                body.addView(b,new LinearLayout.LayoutParams(-1,dp(76)));
            }
        }
        if(!any)body.addView(tv("No hay pólizas guardadas.",15,MUTED,false));
    }
'''
s=replace_method(s,'    private void policies(){',method)

# Only three top-level sections: Inicio, Clientes, OCR.
old='Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false),p=btn("Pólizas",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());p.setOnClickListener(v->policies());\n        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(p,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);'
new='Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->startOcrClean());\n        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);'
if old in s:s=s.replace(old,new,1)
s=s.replace('Button ps=btn("📄  PÓLIZAS",false);ps.setOnClickListener(v->policies());body.addView(ps,new LinearLayout.LayoutParams(-1,dp(58)));','',1)
s=s.replace('Button scan=btn("📷  OCR / DOCUMENTOS",true);scan.setOnClickListener(v->ocrPage());','Button scan=btn("📷  OCR / DOCUMENTOS",true);scan.setOnClickListener(v->startOcrClean());',1)

ocr=r'''    private void startOcrClean(){
        documentUri=null;cameraUri=null;currentBitmap=null;previewBitmap=null;frontBitmap=null;backBitmap=null;
        currentImagePath="";frontImagePath="";backImagePath="";frontText="";backText="";documentKind=0;side=0;
        ocrPage();
    }

    private void ocrPage(){
        shell("OCR","DNI/NIE o póliza");
        body.addView(tv("DOCUMENTO",24,TEXT,true));
        body.addView(tv("Selecciona cómo introducirlo. RgaPro identifica automáticamente qué documento es y lo asocia al cliente.",14,MUTED,false));
        Button cam=btn("📷  CÁMARA",true);
        cam.setOnClickListener(v->{side=1;frontBitmap=null;backBitmap=null;frontImagePath="";backImagePath="";takePhoto();});
        body.addView(cam,new LinearLayout.LayoutParams(-1,dp(64)));
        Button file=btn("📁  ARCHIVO",false);
        file.setOnClickListener(v->chooseUniversalFile());
        body.addView(file,new LinearLayout.LayoutParams(-1,dp(64)));
    }
'''
s=replace_method(s,'    private void ocrPage(){',ocr)

# Generic file picker: one button accepts JPEG/JPG/PNG/WEBP/PDF. Type is inferred from MIME/extension.
chooser='''    private void chooseUniversalFile(){\n        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        i.setType("*/*");\n        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/jpg","image/png","image/webp","application/pdf"});\n        i.addCategory(Intent.CATEGORY_OPENABLE);\n        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);\n        startActivityForResult(i,8106);\n    }\n\n'''
marker='    private void chooseImage()'
if marker not in s: raise SystemExit('chooseImage marker not found')
s=s.replace(marker,chooser+marker,1)

# One generic FILE result. Existing OCR processing then performs document classification.
needle='try{\n            if(request==POLICY_CAMERA){'
branch='''try{\n            if(request==8106){\n                Uri u=data==null?null:data.getData(); if(u==null)return;\n                documentUri=u; currentImagePath=u.toString();\n                String mime=getContentResolver().getType(u);\n                boolean pdf="application/pdf".equalsIgnoreCase(mime);\n                if(!pdf){String low=u.toString().toLowerCase(Locale.ROOT);pdf=low.endsWith(".pdf");}\n                documentKind=pdf?2:1;\n                if(documentKind==2)previewBitmap=renderPdfFirstPage(u); else {currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;}\n                ocrPage(); return;\n            }\n            if(request==POLICY_CAMERA){'''
if needle not in s: raise SystemExit('activity result marker not found')
s=s.replace(needle,branch,1)

# Camera is sequential: first shot is anverso, then the app asks for reverso.
oldcam='''            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}\n            else if(frontBitmap==null){frontBitmap=currentBitmap;frontImagePath=currentImagePath;}\n            else {backBitmap=currentBitmap;backImagePath=currentImagePath;}\n            if(frontBitmap!=null && backBitmap==null){Toast.makeText(this,"Anverso cargado. Ahora selecciona el REVERSO.",Toast.LENGTH_LONG).show();ocrPage();}\n            else {reviewDniPair();}'''
newcam='''            if(request==CAMERA){\n                if(side==1){frontBitmap=currentBitmap;frontImagePath=currentImagePath;side=2;\n                    new AlertDialog.Builder(this).setTitle("Ahora toma el REVERSO").setMessage("RgaPro usará ambos lados para identificar y completar el DNI/NIE.").setPositiveButton("📷 TOMAR REVERSO",(d,w)->takePhoto()).setNegativeButton("Cancelar",null).show();\n                }else{backBitmap=currentBitmap;backImagePath=currentImagePath;side=0;reviewDniPair();}\n                return;\n            }\n            if(frontBitmap==null){frontBitmap=currentBitmap;frontImagePath=currentImagePath;} else {backBitmap=currentBitmap;backImagePath=currentImagePath;}\n            reviewDniPair();'''
if oldcam in s:s=s.replace(oldcam,newcam,1)

# Clean OCR navigation must not retain a previous document; selecting a file itself does not clear it.
P.write_text(s,encoding='utf-8')
print('Unified OCR entry applied')
