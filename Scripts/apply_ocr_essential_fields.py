from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')


def replace_method(src: str, signature: str, replacement: str) -> str:
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = src.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    i = brace
    while i < len(src):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + replacement + src[i+1:]
        i += 1
    raise SystemExit(f'unbalanced braces: {signature}')


# 1) Never downsample the source photo before OCR. Small DNI text (DNI/date) is too easy to lose.
process_image = r'''    private void processImageFile(String path,ImageCallback cb){
        new Thread(()->{
            try{
                BitmapFactory.Options o=new BitmapFactory.Options();
                o.inSampleSize=1;
                o.inScaled=false;
                Bitmap bm=BitmapFactory.decodeFile(path,o);
                if(bm==null)throw new IOException("Imagen no válida");
                InputImage image=InputImage.fromBitmap(bm,0);
                TextRecognizer rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                rec.process(image).addOnSuccessListener(r->{String text=r.getText();bm.recycle();rec.close();cb.ok(text);})
                  .addOnFailureListener(e->{bm.recycle();rec.close();cb.error(e);});
            }catch(Exception e){cb.error(e);}
        }).start();
    }
'''
s = replace_method(s, '    private void processImageFile(String path,ImageCallback cb){', process_image)

# 2) Add focused parsing helpers once.
helpers_marker = '    private String clean(String s){'
helpers = r'''    private String normalizeIdentityOcr(String s){
        return (s==null?"":s).toUpperCase(Locale.ROOT)
            .replace("APELLlDOS","APELLIDOS")
            .replace("APELLlDO","APELLIDO")
            .replace("NACIMlENTO","NACIMIENTO")
            .replace("NACIMlENT0","NACIMIENTO")
            .replace("VALlDEZ","VALIDEZ")
            .replace("EMlSION","EMISION")
            .replace("N0MBRE","NOMBRE")
            .replace("N0MBRES","NOMBRES");
    }

    private boolean isIdentityLabel(String line){
        String x=clean(line);
        return x.matches("^(APELLIDOS|APELLIDO|NOMBRE|SEXO|NACIONALIDAD|FECHA DE NACIMIENTO|NACIMIENTO|EMISION|VALIDEZ|CADUCIDAD|NUM SOPORTE|Nº SOPORTE|N° SOPORTE|DOMICILIO|LUGAR DE NACIMIENTO|HIJO/A DE|HIJO DE|FIRMA|SIGNATURE).*");
    }

    private String collectIdentityField(String[] lines,int index){
        StringBuilder out=new StringBuilder();
        for(int j=index+1;j<Math.min(lines.length,index+4);j++){
            String v=clean(lines[j]);
            if(v.isEmpty() || isIdentityLabel(v)) break;
            // Ignore obvious non-person numeric/footer noise.
            if(v.matches("^[0-9 /.-]{4,}$")){
                if(out.length()>0) break;
                continue;
            }
            if(v.matches("^(NATIONAL IDENTITY CARD|DOCUMENTO NACIONAL DE IDENTIDAD|REINO DE ESPAÑA|ESPAÑA)$")) break;
            if(out.length()>0) out.append(' ');
            out.append(v);
        }
        return out.toString().replaceAll("\\s+"," ").trim();
    }

    private String findDateNearIdentityLabel(String[] lines,String... labels){
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            for(String label:labels){
                if(line.contains(label)){
                    Matcher m=Pattern.compile("\\b(\\d{2})[ /.-](\\d{2})[ /.-](\\d{4})\\b").matcher(line);
                    if(m.find()) return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
                    for(int j=i+1;j<Math.min(lines.length,i+3);j++){
                        m=Pattern.compile("\\b(\\d{2})[ /.-](\\d{2})[ /.-](\\d{4})\\b").matcher(lines[j]);
                        if(m.find()) return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
                    }
                }
            }
        }
        return "";
    }

    private String extractValidDni(String text){
        String u=normalizeIdentityOcr(text);
        Matcher spaced=Pattern.compile("(?<![0-9])([0-9]{4})\\s*([0-9]{4})\\s*([A-Z])(?![A-Z0-9])").matcher(u);
        while(spaced.find()){
            String candidate=spaced.group(1)+spaced.group(2)+spaced.group(3);
            if(isValidDniLocal(candidate)) return candidate;
        }
        Matcher plain=Pattern.compile("(?<![0-9])([0-9]{8}[A-Z])(?![A-Z0-9])").matcher(u);
        while(plain.find()){
            String candidate=plain.group(1);
            if(isValidDniLocal(candidate)) return candidate;
        }
        Matcher nie=Pattern.compile("(?<![A-Z0-9])([XYZ][0-9]{7}[A-Z])(?![A-Z0-9])").matcher(u);
        while(nie.find()){
            String candidate=nie.group(1);
            if(isValidNieLocal(candidate)) return candidate;
        }
        return "";
    }

    private boolean isValidDniLocal(String value){
        if(value==null || !value.matches("\\d{8}[A-Z]")) return false;
        final String letters="TRWAGMYFPDXBNJZSQVHLCKE";
        try{return letters.charAt(Integer.parseInt(value.substring(0,8))%23)==value.charAt(8);}catch(Exception e){return false;}
    }

    private boolean isValidNieLocal(String value){
        if(value==null || !value.matches("[XYZ]\\d{7}[A-Z]")) return false;
        String numeric=(value.charAt(0)=='X'?"0":value.charAt(0)=='Y'?"1":"2")+value.substring(1,8);
        final String letters="TRWAGMYFPDXBNJZSQVHLCKE";
        try{return letters.charAt(Integer.parseInt(numeric)%23)==value.charAt(8);}catch(Exception e){return false;}
    }

    private int essentialConfidence(OcrData d){
        int score=0;
        if(!d.dni.isEmpty())score+=35;
        if(!d.name.isEmpty())score+=20;
        if(!d.surname.isEmpty())score+=20;
        if(!d.birthDate.isEmpty())score+=20;
        if(!d.validityDate.isEmpty())score+=5;
        return Math.min(100,score);
    }

'''
if 'private String normalizeIdentityOcr(String s){' not in s:
    s=s.replace(helpers_marker,helpers+helpers_marker,1)

# 3) Replace the internal DNI parser used by the UI. It now collects multi-line surnames/names and uses the DNI control letter.
parse = r'''    private OcrData parseOcr(String raw){
        OcrData d=new OcrData();
        String text=raw==null?"":raw.replace('\\r','\\n');
        String norm=normalizeIdentityOcr(text);
        String documentKind=classifyDocumentKind(norm);
        if("POLIZA".equals(documentKind)||"DOCUMENTO".equals(documentKind)) return parsePolicyOcr(norm,documentKind);

        d.documentKind="DNI";
        d.dni=extractValidDni(norm);
        d.identityType=d.dni.matches("[XYZ].*")?"NIE":"DNI";

        String[] lines=norm.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            if(line.matches("^APELLIDOS.*") || line.matches("^APELLIDO.*")){
                String v=valueAfterLabel(line,line.startsWith("APELLIDOS")?"APELLIDOS":"APELLIDO");
                if(v.isEmpty()) v=collectIdentityField(lines,i);
                d.surname=v;
            }else if(line.matches("^NOMBRE.*") || line.matches("^NOMBRES.*")){
                String lab=line.startsWith("NOMBRES")?"NOMBRES":"NOMBRE";
                String v=valueAfterLabel(line,lab);
                if(v.isEmpty()) v=collectIdentityField(lines,i);
                d.name=v;
            }else if(line.startsWith("NACIONALIDAD")){
                String v=valueAfterLabel(line,"NACIONALIDAD"); if(v.isEmpty()&&i+1<lines.length)v=clean(lines[i+1]); d.nationality=v;
            }else if(line.startsWith("SEXO")){
                String v=valueAfterLabel(line,"SEXO"); if(v.isEmpty()&&i+1<lines.length)v=clean(lines[i+1]); d.sex=v;
            }else if(line.contains("DOMICILIO")){
                d.address=valueAfterLabel(line,"DOMICILIO");
            }else if(line.contains("LUGAR DE NACIMIENTO")){
                d.birthPlace=valueAfterLabel(line,"LUGAR DE NACIMIENTO");
            }else if(line.contains("HIJO/A DE")||line.contains("HIJO DE")){
                String lab=line.contains("HIJO/A DE")?"HIJO/A DE":"HIJO DE";d.parents=valueAfterLabel(line,lab);
            }else if(line.contains("NUM SOPORTE")||line.contains("Nº SOPORTE")||line.contains("N° SOPORTE")){
                String lab=line.contains("NUM SOPORTE")?"NUM SOPORTE":line.contains("Nº SOPORTE")?"Nº SOPORTE":"N° SOPORTE";d.supportNumber=valueAfterLabel(line,lab);
            }
        }

        d.birthDate=findDateNearIdentityLabel(lines,"FECHA DE NACIMIENTO","NACIMIENTO");
        d.issueDate=findDateNearIdentityLabel(lines,"EMISION","FECHA DE EMISION","FECHA DE EXPEDICION");
        d.validityDate=findDateNearIdentityLabel(lines,"VALIDEZ","CADUCIDAD","FECHA DE CADUCIDAD");

        // MRZ (cuando se captura el reverso) confirma DNI, apellidos, nombre, nacimiento y validez.
        StringBuilder mrz=new StringBuilder();
        for(String line:lines){String compact=line.replace(" ","").replaceAll("[^A-Z0-9<]","");if(compact.contains("IDESP")||compact.contains("<<"))mrz.append(compact).append('\\n');}
        String mrzText=mrz.toString();
        if(!mrzText.isEmpty()){
            Matcher id=Pattern.compile("IDESP(?:C)?(?:ID)?([0-9]{8}[A-Z])").matcher(mrzText);if(id.find() && isValidDniLocal(id.group(1))) d.dni=id.group(1);
            Matcher nm=Pattern.compile("([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)<<([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)").matcher(mrzText);if(nm.find()){d.surname=nm.group(1).replace('<',' ').trim().replaceAll("\\s+"," ");d.name=nm.group(2).replace('<',' ').trim().replaceAll("\\s+"," ");}
            Matcher dates=Pattern.compile("(\\d{6})\\d([MF<])(\\d{6})\\d").matcher(mrzText.replace("<",""));if(dates.find()){d.birthDate=mrzDate(dates.group(1));d.sex=dates.group(2);d.validityDate=mrzDate(dates.group(3));}
            d.mrz=mrzText;
        }

        if(d.surname.isEmpty() && d.name.isEmpty()){
            // Fallback for layouts where the OCR drops the labels entirely.
            Matcher f=Pattern.compile("\\b([A-ZÁÉÍÓÚÑ]{3,}(?:\\s+[A-ZÁÉÍÓÚÑ]{3,})?)\\s+([A-ZÁÉÍÓÚÑ]{3,})\\b").matcher(norm);
            if(f.find()){d.surname=f.group(1);d.name=f.group(2);}
        }
        d.holder=(d.name+" "+d.surname).trim();
        d.confidence=essentialConfidence(d);
        return d;
    }
'''
s=replace_method(s,'    private OcrData parseOcr(String raw){',parse)

# 4) Replace the result dialog: only the essential fields are shown, everything else stays out of the user's way.
show = r'''    private void showOcrResult(String raw){
        OcrData d=parseOcr(raw);
        LinearLayout box=col();box.setPadding(dp(10),dp(4),dp(10),dp(4));
        boolean policy="POLIZA".equals(d.documentKind)||"DOCUMENTO".equals(d.documentKind);
        String aviso=policy?(d.confidence<60?"⚠ Póliza con datos incompletos. Revisa antes de guardar.":"✓ Póliza identificada. Revisa antes de guardar."):(d.confidence<80?"⚠ DNI con datos incompletos. Revisa antes de guardar.":"✓ DNI identificado. Revisa antes de guardar.");
        box.addView(tv(aviso+"  Confianza: "+d.confidence+"%",14,d.confidence<80?Color.rgb(170,95,0):Color.rgb(25,110,70),true));
        box.addView(tv("Solo mostramos los datos esenciales. El resto del documento queda conservado para consulta.",13,MUTED,false));

        Spinner idType=new Spinner(this);String[] idTypes={"DNI","NIE","CIF empresa","Sin identificar"};idType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,idTypes));int selected="NIE".equals(d.identityType)?1:"CIF".equals(d.identityType)?2:0;idType.setSelection(selected);idType.setVisibility(policy?View.GONE:View.VISIBLE);
        box.addView(tv("Tipo de identificación",13,MUTED,false));box.addView(idType,new LinearLayout.LayoutParams(-1,dp(52)));

        EditText holder=edit("Tomador / titular"),surname=edit("Apellidos"),name=edit("Nombre"),dni=edit("DNI / NIE"),cif=edit("CIF de empresa"),birth=edit("Fecha de nacimiento"),nationality=edit("Nacionalidad"),sex=edit("Sexo"),address=edit("Dirección"),birthPlace=edit("Lugar de nacimiento"),parents=edit("Padres"),support=edit("Nº soporte"),issue=edit("Fecha de emisión"),validity=edit("Fecha de vencimiento / caducidad"),phone=edit("Teléfono"),email=edit("Email"),number=edit("Nº de póliza");
        holder.setText(d.holder);surname.setText(d.surname);name.setText(d.name);dni.setText(d.dni);cif.setText(d.cif);birth.setText(d.birthDate);nationality.setText(d.nationality);sex.setText(d.sex);address.setText(d.address);birthPlace.setText(d.birthPlace);parents.setText(d.parents);support.setText(d.supportNumber);issue.setText(d.issueDate);validity.setText(d.validityDate);number.setText(d.number);

        if(policy){
            addField(box,"TIPO DE PRODUCTO",d.policyType,edit("Tipo de producto"));
            EditText prod=lastEditable(box); if(prod!=null) prod.setText(d.policyType); // populated by helper
            box.addView(tv("Nº DE PÓLIZA",13,MUTED,true));box.addView(number,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("TOMADOR",13,MUTED,true));box.addView(holder,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("VENCIMIENTO",13,MUTED,true));box.addView(validity,new LinearLayout.LayoutParams(-1,dp(54)));
        }else{
            box.addView(tv("NOMBRE",13,MUTED,true));box.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("APELLIDOS",13,MUTED,true));box.addView(surname,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("DNI / NIE",13,MUTED,true));box.addView(dni,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("FECHA DE NACIMIENTO",13,MUTED,true));box.addView(birth,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("FECHA DE CADUCIDAD",13,MUTED,true));box.addView(validity,new LinearLayout.LayoutParams(-1,dp(54)));
        }

        Button editAll=action("✏️ Editar datos",false),saveBtn=action("💾 Guardar",true);editAll.setTextSize(16);saveBtn.setTextSize(16);box.addView(editAll,new LinearLayout.LayoutParams(-1,dp(56)));box.addView(saveBtn,new LinearLayout.LayoutParams(-1,dp(58)));
        EditText[] editable={holder,surname,name,dni,cif,birth,nationality,sex,address,birthPlace,parents,support,issue,validity,phone,email,number};
        for(EditText e:editable)e.setEnabled(false);
        editAll.setOnClickListener(v->{for(EditText e:editable)e.setEnabled(true);editAll.setEnabled(false);});
        saveBtn.setOnClickListener(v->{String chosenId=policy?(d.cif.isEmpty()?"Sin identificar":"CIF empresa"):String.valueOf(idType.getSelectedItem());String chosenType=policy?(d.policyType.isEmpty()?"Otros":d.policyType):("CIF empresa".equals(chosenId)?"Empresa":"Cliente / "+chosenId);saveClient(holder,surname,name,dni,cif,birth,nationality,sex,address,birthPlace,parents,support,issue,validity,phone,email,number,chosenId,chosenType,raw);});

        ScrollView sc=new ScrollView(this);sc.addView(box);new AlertDialog.Builder(this).setTitle("Datos esenciales detectados").setView(sc).setNegativeButton("Descartar",null).show();
    }

    private final ArrayList<EditText> _lastEditableFields=new ArrayList<>();
    private void addField(LinearLayout box,String label,String value,EditText field){box.addView(tv(label,13,MUTED,true));box.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));_lastEditableFields.add(field);}
    private EditText lastEditable(LinearLayout box){return _lastEditableFields.isEmpty()?null:_lastEditableFields.get(_lastEditableFields.size()-1);}
'''
# The compact dialog uses helpers; replace only once.
s=replace_method(s,'    private void showOcrResult(String raw){',show)

# 5) Make policy/OCR document separation durable in the main build, but do not alter policy extraction logic here.
MAIN.write_text(s,encoding='utf-8')
print('Applied essential OCR UI + robust DNI parsing + full-resolution input.')
