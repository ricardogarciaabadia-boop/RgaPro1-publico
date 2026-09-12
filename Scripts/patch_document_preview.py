from pathlib import Path

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=MAIN.read_text(encoding='utf-8')

def insert_before(src, marker, block):
    if block.strip() in src:
        return src
    p=src.find(marker)
    if p<0: raise SystemExit('marker not found: '+marker)
    return src[:p]+block+'\n'+src[p:]

preview='''    private void showDocumentPreview(ArrayList<Uri> uris, ArrayList<Bitmap> bitmaps, Runnable onContinue){
        if(bitmaps==null||bitmaps.isEmpty()){onContinue.run();return;}
        LinearLayout box=col();
        TextView info=tv(bitmaps.size()==1?"Vista previa del archivo":"Vista previa de los archivos ("+bitmaps.size()+")",18,TEXT,true);box.addView(info);
        int shown=Math.min(bitmaps.size(),6);
        for(int i=0;i<shown;i++){
            Bitmap b=bitmaps.get(i); if(b==null)continue;
            ImageView iv=new ImageView(this);iv.setImageBitmap(b);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);iv.setBackground(box(Color.WHITE,12));
            box.addView(iv,new LinearLayout.LayoutParams(-1,dp(190)));
            box.addView(tv(bitmaps.size()==1?"Archivo seleccionado":"Página "+(i+1),13,MUTED,false));
        }
        new AlertDialog.Builder(this).setTitle("DOCUMENTO SELECCIONADO").setView(box)
            .setNegativeButton("✕ CANCELAR",null)
            .setPositiveButton("✓ CONTINUAR",(d,w)->onContinue.run()).show();
    }

'''
s=insert_before(s,'    private void processSelectedSingleFile(){',preview)

# Single PDF: render and show preview before OCR.
old='if(pdf){previewBitmap=renderPdfFirstPage(u);processCurrentDocument();}else{currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;processSelectedSingleFile();}return;'
new='if(pdf){previewBitmap=renderPdfFirstPage(u);ArrayList<Uri> pu=new ArrayList<>();pu.add(u);ArrayList<Bitmap> pb=new ArrayList<>();pb.add(previewBitmap);showDocumentPreview(pu,pb,()->processCurrentDocument());}else{currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;ArrayList<Uri> pu=new ArrayList<>();pu.add(u);ArrayList<Bitmap> pb=new ArrayList<>();pb.add(currentBitmap);showDocumentPreview(pu,pb,()->processSelectedSingleFile());}return;'
if old in s:s=s.replace(old,new,1)

# The separate PDF request path, if still reachable, also gets preview.
old='documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();processCurrentDocument();return;'
new='documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();ArrayList<Uri> pu=new ArrayList<>();pu.add(u);ArrayList<Bitmap> pb=new ArrayList<>();pb.add(previewBitmap);showDocumentPreview(pu,pb,()->processCurrentDocument());return;'
if old in s:s=s.replace(old,new,1)

# Separate image request path gets preview.
old='documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();processSelectedSingleFile();return;'
new='documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();ArrayList<Uri> pu=new ArrayList<>();pu.add(u);ArrayList<Bitmap> pb=new ArrayList<>();pb.add(currentBitmap);showDocumentPreview(pu,pb,()->processSelectedSingleFile());return;'
if old in s:s=s.replace(old,new,1)

# Universal single-image path variant.
old='if(pdf){previewBitmap=renderPdfFirstPage(u);processCurrentDocument();}else{currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;processSelectedSingleFile();}return;'
if old in s:s=s.replace(old,new,1)

MAIN.write_text(s,encoding='utf-8')
print('Document preview restored before OCR')
