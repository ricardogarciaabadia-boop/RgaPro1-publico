from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')
needle='''    private void processSelectedSingleFile(){
        if(currentBitmap==null){Toast.makeText(this,"Archivo de imagen vacío.",Toast.LENGTH_LONG).show();return;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);'''
replacement='''    private void processSelectedSingleFile(){
        if(currentBitmap==null){Toast.makeText(this,"Archivo de imagen vacío.",Toast.LENGTH_LONG).show();return;}
        Bitmap straightened=DniImagePreprocessor.deskew(currentBitmap);
        if(straightened!=null&&straightened!=currentBitmap){currentBitmap=straightened;previewBitmap=straightened;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);'''
if needle in s:s=s.replace(needle,replacement,1)
P.write_text(s,encoding='utf-8')
print('Universal image ingestion now deskews images before classification/OCR')
