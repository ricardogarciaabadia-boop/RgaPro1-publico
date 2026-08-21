from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')

old = 'private static class OcrData{String holder="",surname="",name="",dni="",birthDate="",nationality="",sex="",address="",birthPlace="",parents="",supportNumber="",issueDate="",validityDate="",phone="",email="",identityType="",cif="";int confidence=0;}'
new = 'private static class OcrData{String holder="",surname="",name="",dni="",birthDate="",nationality="",sex="",address="",birthPlace="",parents="",supportNumber="",issueDate="",validityDate="",phone="",email="",identityType="",cif="",number="",documentKind="UNKNOWN",policyType="";int confidence=0;}'
if old in s:
    s = s.replace(old, new, 1)

needle = 'String norm=text.toUpperCase(Locale.ROOT);Matcher cif='
replacement = 'String norm=text.toUpperCase(Locale.ROOT);String documentKind=classifyDocumentKind(norm);if("POLIZA".equals(documentKind)||"DOCUMENTO".equals(documentKind))return parsePolicyOcr(norm,documentKind);Matcher cif='
if needle in s and 'classifyDocumentKind(norm)' not in s:
    s = s.replace(needle, replacement, 1)

marker = '    private String clean(String s){'
if 'private String classifyDocumentKind(String u){' not in s:
    helpers = r'''    private String classifyDocumentKind(String u){
        boolean policy=Pattern.compile("\\b(P[ÓO]LIZA|N[ÚU]MERO\\s+DE\\s+P[ÓO]LIZA|N[ÚU]MERO\\s+P[ÓO]LIZA|TOMADOR(?:A)?|CONTRATANTE|ASEGURAD(?:O|A|ORA|OS|AS)|PRIMA|RECIBO|COBERTURA|CAPITAL(?:ES)?\\s+ASEGURADO|CONDICIONES\\s+(?:PARTICULARES|GENERALES)|VENCIMIENTO|EFECTO|FRANQUICIA|BENEFICIARIO(?:S)?|RIESGO|GARANT[ÍI]A(?:S)?)\\b").matcher(u).find();
        int dniLabels=0;
        for(String label:new String[]{"DNI","NOMBRE","APELLIDOS","FECHA DE NACIMIENTO","LUGAR DE NACIMIENTO","NACIONALIDAD","SEXO","SOPORTE","FECHA DE EXPEDICIÓN","VALIDEZ","DOMICILIO","IDESP"}) if(u.contains(label)) dniLabels++;
        boolean dniId=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b").matcher(u).find();
        boolean mrz=u.contains("IDESP") && Pattern.compile("[A-Z0-9<]{20,}").matcher(u).find();
        if(!policy && mrz && dniId) return u.matches(".*\\b[XYZ][0-9]{7}[A-Z]\\b.*")?"NIE":"DNI";
        if(!policy && dniId && dniLabels>=2) return u.matches(".*\\b[XYZ][0-9]{7}[A-Z]\\b.*")?"NIE":"DNI";
        if(policy) return "POLIZA";
        return "DOCUMENTO";
    }

    private OcrData parsePolicyOcr(String norm,String kind){
        OcrData d=new OcrData();d.documentKind=kind;
        Matcher cif=Pattern.compile("\\b[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]\\b").matcher(norm);if(cif.find())d.cif=cif.group();
        d.policyType=classifyPolicyTypeFinal(norm,d.policyType);
        d.number=extractLabel(norm,"N[ÚU]MERO\\s+DE\\s+P[ÓO]LIZA","N[ÚU]MERO\\s+P[ÓO]LIZA","P[ÓO]LIZA");
        d.holder=extractLabel(norm,"TOMADOR(?:A)?","CONTRATANTE","ASEGURADO(?:A)?","CLIENTE");
        d.issueDate=extractDateAfter(norm,"EFECTO","INICIO","FECHA DE EFECTO","FECHA DE INICIO");
        d.validityDate=extractDateAfter(norm,"VENCIMIENTO","FIN","FECHA DE VENCIMIENTO","FECHA DE FIN","CADUCIDAD");
        d.address=extractLabel(norm,"DOMICILIO","DIRECCI[ÓO]N");
        d.phone=extractLabel(norm,"TEL[ÉE]FONO","M[ÓO]VIL");
        d.email=extractLabel(norm,"EMAIL","CORREO ELECTR[ÓO]NICO");
        int found=0;if(!d.number.isEmpty())found+=25;if(!d.holder.isEmpty())found+=25;if(!d.policyType.isEmpty())found+=20;if(!d.issueDate.isEmpty())found+=10;if(!d.validityDate.isEmpty())found+=10;if(!d.cif.isEmpty())found+=10;d.confidence=Math.min(100,found);return d;
    }

    private String extractLabel(String text,String... labels){
        String[] lines=text.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            for(String label:labels){Matcher m=Pattern.compile("^.*?"+label+"\\s*[:#.-]?\\s*(.*)$",Pattern.CASE_INSENSITIVE).matcher(line);if(m.find()){String v=clean(m.group(1));if(!v.isEmpty()&&!v.matches("^[\\-–—]+$"))return v;if(i+1<lines.length){String n=clean(lines[i+1]);if(!n.isEmpty())return n;}}}
        }
        return "";
    }

    private String extractDateAfter(String text,String... labels){
        String[] lines=text.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            for(String label:labels){if(Pattern.compile("\\b"+label+"\\b",Pattern.CASE_INSENSITIVE).matcher(line).find()){Matcher m=Pattern.compile("\\d{2}[ /.-]\\d{2}[ /.-]\\d{4}").matcher(line);if(m.find())return m.group();if(i+1<lines.length){m=Pattern.compile("\\d{2}[ /.-]\\d{2}[ /.-]\\d{4}").matcher(lines[i+1]);if(m.find())return m.group();}}}
        }
        return "";
    }

    private String classifyPolicyTypeFinal(String raw,String fallback){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Deceso";
        if(u.contains("HOGAR")||u.contains("MULTIRRIESGO HOGAR"))return "Hogar";
        if(u.contains("AUTOMÓVIL")||u.contains("AUTOMOVIL")||u.contains("VEHÍCULO")||u.contains("VEHICULO"))return "Auto";
        if(u.contains("SALUD")||u.contains("ASISTENCIA SANITARIA"))return "Salud";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("AHORRO")||u.contains("PIAS")||u.contains("RENTA"))return "Ahorro";
        if(u.contains("ACCIDENTE")||u.contains("ACCIDENTES"))return "Accidente";
        if(u.contains("COMUNIDAD")||u.contains("COMUNIDADES"))return "Comunidades";
        if(u.contains("RESPONSABILIDAD CIVIL"))return "Responsabilidad civil";
        if(u.contains("EMPRESA")||u.contains("PYME")||u.contains("COMERCIO"))return "Empresa";
        return fallback==null||fallback.isEmpty()?"Otros":fallback;
    }

'''
    if marker not in s: raise SystemExit('clean() marker not found')
    s=s.replace(marker,helpers+marker,1)

old = 'holder.setText(d.holder);surname.setText(d.surname);name.setText(d.name);dni.setText(d.dni);cif.setText(d.cif);'
new = 'holder.setText(d.holder);surname.setText(d.surname);name.setText(d.name);dni.setText(d.dni);cif.setText(d.cif);number.setText(d.number);'
if old in s and 'number.setText(d.number)' not in s:s=s.replace(old,new,1)

# Use one common policy taxonomy and auto-select the detected policy type.
s=s.replace('String[] types={"Cliente / DNI","Cliente / NIE","Auto","Hogar","Vida","Salud","Decesos","Empresa","Otros"};','String[] types={"Deceso","Hogar","Vida","Accidente","Ahorro","Comunidades","Empresa","Responsabilidad civil","Salud","Otros"};',1)
needle='type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));box.addView(type,new LinearLayout.LayoutParams(-1,dp(52)));'
if needle in s and 'if("POLIZA".equals(d.documentKind)' not in s:
    repl=needle+'if("POLIZA".equals(d.documentKind)){int psel=9;for(int i=0;i<types.length;i++)if(types[i].equalsIgnoreCase(d.policyType)){psel=i;break;}type.setSelection(psel);}'
    s=s.replace(needle,repl,1)

# Prevent policy documents from being stored as DNI records even if the user leaves the default fields blank.
needle='p.put("holder",holder.getText().toString().trim());'
if needle in s and 'boolean policyDocument=' not in s:
    s=s.replace(needle,needle+'boolean policyDocument=!type.startsWith("Cliente") && !"DNI".equalsIgnoreCase(idType) && !"NIE".equalsIgnoreCase(idType) && !"CIF empresa".equalsIgnoreCase(idType);',1)
needle2='p.put("holderDni",dni.getText().toString().trim().toUpperCase(Locale.ROOT));'
if needle2 in s and 'if(policyDocument){p.put("documentKind","POLIZA")' not in s:
    s=s.replace(needle2,needle2+'if(policyDocument){p.put("documentKind","POLIZA");p.remove("birthDate");p.remove("nationality");p.remove("sex");p.remove("birthPlace");p.remove("parents");p.remove("supportNumber");p.remove("issueDate");p.remove("validityDate");p.remove("expiry");p.remove("holderDni");p.put("identityType","");p.put("identityNumber","");}',1)

MAIN.write_text(s,encoding='utf-8')
print('OCR separation patch applied')
