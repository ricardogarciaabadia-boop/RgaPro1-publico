from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s = MAIN.read_text(encoding='utf-8')


def replace_method(src: str, signature: str, replacement: str) -> str:
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = src.find('{', start)
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0: return src[:start] + replacement + src[i + 1:]
    raise SystemExit(f'unbalanced method: {signature}')

helpers = r'''    private String normalizeOcrIdentity(String s){
        return (s==null?"":s).toUpperCase(Locale.ROOT)
            .replace("NACIMlENTO","NACIMIENTO").replace("NACIMlENT0","NACIMIENTO")
            .replace("N0MBRE","NOMBRE").replace("APELLlDOS","APELLIDOS").replace("VALlDEZ","VALIDEZ");
    }
    private boolean validDniLetter(String v){
        if(v==null||!v.matches("\\d{8}[A-Z]"))return false;
        String t="TRWAGMYFPDXBNJZSQVHLCKE"; try{return t.charAt(Integer.parseInt(v.substring(0,8))%23)==v.charAt(8);}catch(Exception e){return false;}
    }
    private String extractDniRobust(String text){
        String u=normalizeOcrIdentity(text).replaceAll("[^A-Z0-9<]"," ");
        Matcher m=Pattern.compile("(?<![0-9])([0-9]{8}[A-Z])(?![A-Z0-9])").matcher(u);
        while(m.find())if(validDniLetter(m.group(1)))return m.group(1);
        m=Pattern.compile("(?<![0-9])([0-9]{4})\\s*([0-9]{4})\\s*([A-Z])(?![A-Z0-9])").matcher(u);
        while(m.find()){String v=m.group(1)+m.group(2)+m.group(3);if(validDniLetter(v))return v;}
        m=Pattern.compile("IDESP[A-Z0-9<]*?([0-9]{8}[A-Z])").matcher(u);
        while(m.find())if(validDniLetter(m.group(1)))return m.group(1); return "";
    }
    private String extractBirthDateRobust(String text){
        String u=normalizeOcrIdentity(text);
        Matcher m=Pattern.compile("(?:NACIMIENTO|FECHA\\s+DE\\s+NACIMIENTO)[^0-9]{0,30}(\\d{2})\\s*[./ -]\\s*(\\d{2})\\s*[./ -]\\s*(\\d{4})").matcher(u);
        if(m.find())return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
        m=Pattern.compile("(\\d{6})[0-9][MF<](\\d{6})[0-9]").matcher(u.replace(" ",""));
        if(m.find()){String d=m.group(1);int yy=Integer.parseInt(d.substring(0,2));int year=yy>=30?1900+yy:2000+yy;return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(d.substring(4,6)),Integer.parseInt(d.substring(2,4)),year);} return "";
    }
    private String extractNameRobust(String text){
        String u=normalizeOcrIdentity(text); Matcher m=Pattern.compile("(?m)^APELLIDOS?\\s*[:.-]?\\s*(.+?)(?=\\n(?:NOMBRE|SEXO|NACIONALIDAD|NACIMIENTO)|$)").matcher(u);
        String sur=m.find()?m.group(1).trim():""; m=Pattern.compile("(?m)^NOMBRES?\\s*[:.-]?\\s*(.+?)(?=\\n(?:SEXO|NACIONALIDAD|NACIMIENTO)|$)").matcher(u);
        String nam=m.find()?m.group(1).trim():""; m=Pattern.compile("([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)+)<<([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)+)").matcher(u.replace(" ",""));
        if(m.find()){if(sur.isEmpty())sur=m.group(1).replace('<',' ').trim();if(nam.isEmpty())nam=m.group(2).replace('<',' ').trim();} return (nam+" "+sur).trim().replaceAll("\\s+"," ");
    }
    private JSONObject parseEssentialRobust(String text){
        JSONObject x=parseEssential(text); try{String dni=extractDniRobust(text);if(!dni.isEmpty())x.put("identityNumber",dni);String birth=extractBirthDateRobust(text);if(!birth.isEmpty())x.put("birthDate",birth);String full=extractNameRobust(text);if(!full.isEmpty())x.put("fullName",full);int c=0;if(!dni.isEmpty())c+=45;if(!birth.isEmpty())c+=35;if(!full.isEmpty())c+=20;x.put("confidence",Math.max(x.optInt("confidence",0),c));}catch(Exception ignored){} return x;
    }
'''
if 'parseEssentialRobust' not in s:
    s=s.replace('    private void reviewDniPair(){',helpers+'\n    private void reviewDniPair(){',1)

pair=r'''    private void processDniPairOcr(){
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); if(frontBitmap==null||backBitmap==null){r.close();return;}
        r.process(InputImage.fromBitmap(frontBitmap,0)).addOnSuccessListener(f->{frontText=f==null?"":f.getText();r.process(InputImage.fromBitmap(backBitmap,0)).addOnSuccessListener(b->{backText=b==null?"":b.getText();currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;r.close();showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR reverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR anverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }
'''
s=replace_method(s,'    private void processDniPairOcr(){',pair)

image=r'''    private void processImage(){
        if(currentBitmap==null){Toast.makeText(this,"Primero selecciona un JPEG válido.",Toast.LENGTH_LONG).show();return;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);r.process(InputImage.fromBitmap(currentBitmap,0)).addOnSuccessListener(t->{String text=t.getText()==null?"":t.getText();if(side==2)backText=text;else frontText=text;r.close();showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }
'''
s=replace_method(s,'    private void processImage(){',image)

old='else {frontBitmap=currentBitmap;frontImagePath=currentImagePath;}'
new='else {if(side==2 || (frontBitmap!=null && backBitmap==null)){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}'
if old in s: s=s.replace(old,new,1)
elif 'backBitmap=currentBitmap;backImagePath=currentImagePath;' not in s: raise SystemExit('single JPEG assignment not found')

MAIN.write_text(s,encoding='utf-8');print('DNI OCR accuracy patch applied')
