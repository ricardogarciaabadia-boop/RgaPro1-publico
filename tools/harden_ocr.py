from pathlib import Path
import re

path = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = path.read_text(encoding='utf-8')

# Preserve the camera/photo resolution and let ML Kit read EXIF orientation.
old_img = re.compile(r'    private void processImageFile\(String path,ImageCallback cb\)\{.*?\n    \}\n    private String copyFileToSession', re.S)
new_img = '''    private void processImageFile(String path,ImageCallback cb){
        new Thread(()->{
            TextRecognizer rec=null;
            try{
                File f=new File(path);
                if(!f.exists()||f.length()<1024)throw new IOException("Imagen no válida o vacía");
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);
                if(bounds.outWidth<700||bounds.outHeight<700)throw new IOException("La foto tiene poca resolución. Haz otra foto más cerca y con buena luz.");
                // fromFilePath conserva la orientación EXIF de la cámara; no reducimos la imagen antes del OCR.
                InputImage image=InputImage.fromFilePath(MainActivity.this,Uri.fromFile(f));
                rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                TextRecognizer finalRec=rec;
                rec.process(image).addOnSuccessListener(r->{
                    String text=r.getText();
                    finalRec.close();
                    cb.ok(text==null?"":text);
                }).addOnFailureListener(e->{
                    finalRec.close();
                    cb.error(e);
                });
            }catch(Exception e){if(rec!=null)rec.close();cb.error(e);}
        },"RgaPro-ImageOcr").start();
    }
    private String copyFileToSession'''
s, n = old_img.subn(new_img, s, count=1)
if n != 1:
    raise SystemExit('No se encontró processImageFile; no se aplica el parche')

# Replace the permissive in-activity parser with the validated DNI/NIE parser already in the project.
old_parse = re.compile(r'    private OcrData parseOcr\(String raw\)\{.*?\n    \}\n    private String clean', re.S)
new_parse = '''    private OcrData parseOcr(String raw){
        OcrData d=new OcrData();
        DniOcrParser.Result r=DniOcrParser.parse(raw);
        d.holder=r.holder;d.surname=r.surname;d.name=r.name;d.dni=r.dni;d.birthDate=r.birthDate;
        d.nationality=r.nationality;d.sex=r.sex;d.address=r.address;d.birthPlace=r.birthPlace;
        d.parents=r.parents;d.supportNumber=r.supportNumber;d.issueDate=r.issueDate;d.validityDate=r.validityDate;
        d.identityType=r.dni.matches("[XYZ].*")?"NIE":(!r.dni.isEmpty()?"DNI":"");
        d.confidence=r.confidence;
        PolicyOcrParser.parse(raw).keys().forEachRemaining(k->{try{
            JSONObject p=PolicyOcrParser.parse(raw);
            if(d.validityDate.isEmpty())d.validityDate=p.optString("expiry",p.optString("validityDate",""));
        }catch(Exception ignored){}});
        return d;
    }
    private String clean'''
s, n = old_parse.subn(new_parse, s, count=1)
if n != 1:
    raise SystemExit('No se encontró parseOcr; no se aplica el parche')

path.write_text(s, encoding='utf-8')
print('OCR hardening applied')
