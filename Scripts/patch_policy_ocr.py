from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s = MAIN.read_text(encoding='utf-8')


def replace_method(src: str, pattern: str, replacement: str) -> str:
    m = re.search(pattern, src)
    if not m:
        raise SystemExit(f'method not found: {pattern}')
    start = m.start()
    brace = src.find('{', m.end())
    if brace < 0:
        raise SystemExit(f'opening brace not found: {pattern}')
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + replacement + src[i+1:]
    raise SystemExit(f'unbalanced method: {pattern}')


replacement = r'''    private OcrData parsePolicyOcr(String raw,String kind){
        OcrData d=new OcrData();
        d.documentKind="POLIZA";
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT).replace('\r','\n').replace("\t"," ");
        String[] lines=u.split("\\n");
        d.policyType=classifyPolicyProduct(u);
        for(int i=0;i<lines.length;i++){
            String line=lines[i].replaceAll("\\s+"," ").trim();
            if(line.isEmpty()) continue;
            if(line.matches(".*N[ºO°]?\\.?\\s*DE\\s*P[ÓO]LIZA.*") || line.matches(".*N[ºO°]?\\.?\\s*P[ÓO]LIZA.*")){
                Matcher m=Pattern.compile("(?:N[ºO°]?\\.?\\s*(?:DE\\s*)?P[ÓO]LIZA)\\s*[:.-]*\\s*([0-9]{5,12})").matcher(line);
                if(m.find()) d.number=m.group(1); else if(i+1<lines.length){m=Pattern.compile("\\b([0-9]{5,12})\\b").matcher(lines[i+1]);if(m.find())d.number=m.group(1);}
            }
            if(line.contains("TOMADOR DEL SEGURO") || line.contains("TOMADOR")){
                Matcher m=Pattern.compile("TOMADOR(?: DEL SEGURO)?(?: Y DOMICILIO)?\\s*[:.-]*\\s*(.*?)(?=\\s+DOC\\.?\\s*ID|$)").matcher(line);
                if(m.find()&&!m.group(1).trim().isEmpty())d.holder=m.group(1).trim();
                Matcher id=Pattern.compile("(?:DOC\\.?\\s*ID|DNI|NIE)\\s*[-:]*\\s*([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]?)").matcher(line);
                if(id.find())d.dni=id.group(1);
                if(d.holder.isEmpty()&&i+1<lines.length){String n=lines[i+1].replaceAll("\\s+"," ").trim();if(!n.isEmpty())d.holder=n;}
            }
            if(line.contains("DOC. ID")||line.contains("DOC ID")||line.matches(".*\\bDNI\\b.*")||line.matches(".*\\bNIE\\b.*")){
                Matcher id=Pattern.compile("(?:DOC\\.?\\s*ID|DNI|NIE)\\s*[-:]*\\s*([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]?)").matcher(line);if(id.find())d.dni=id.group(1);
            }
            if(line.matches(".*(?:TELEFONO|TEL[ÉE]FONO|M[ÓO]VIL).*")){
                Matcher m=Pattern.compile("\\b([6789][0-9]{8})\\b").matcher(line);if(m.find())d.phone=m.group(1);
                if(d.phone.isEmpty()&&i+1<lines.length){m=Pattern.compile("\\b([6789][0-9]{8})\\b").matcher(lines[i+1]);if(m.find())d.phone=m.group(1);}
            }
            if(line.contains("DOMICILIO DE COBRO")||line.contains("DOMICILIO")){
                String v=line.replaceFirst(".*(?:DOMICILIO DE COBRO|DOMICILIO)\\s*[:.-]*\\s*","").trim();if(!v.isEmpty())d.address=v;else if(i+1<lines.length)d.address=lines[i+1].trim();
            }
            if(line.contains("EMAIL")||line.contains("E-MAIL")||line.contains("CORREO")){Matcher m=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}").matcher(line);if(m.find())d.email=m.group();}
            String receipt=moneyAfterLabels(line,"TOTAL RECIBO","TOTAL DEL RECIBO","TOTAL RECIBO POLIZA","PRIMA TOTAL","RECIBO");if(!receipt.isEmpty()&&d.receipt.isEmpty())d.receipt=receipt;
            String capital=moneyAfterLabels(line,"CAPITAL PRINCIPAL","SUMA ASEGURADA","CAPITAL ASEGURADO","CAPITAL CONTRATADO","CAPITAL");if(!capital.isEmpty()&&d.capital.isEmpty())d.capital=capital;
            String level=moneyAfterLabels(line,"DECESOS NIVELADA");if(!level.isEmpty())d.decesosLevelada=level;
            String totalDec=moneyAfterLabels(line,"TOTAL DECESOS");if(!totalDec.isEmpty())d.decesos=totalDec;
        }
        if(d.number.isEmpty()){Matcher m=Pattern.compile("N[ºO°]?\\.?\\s*(?:DE\\s*)?P[ÓO]LIZA[^0-9]{0,30}([0-9]{5,12})").matcher(u);if(m.find())d.number=m.group(1);}
        if(d.dni.isEmpty()){Matcher m=Pattern.compile("(?<![0-9])([0-9]{8})\\s*([A-Z])(?![A-Z0-9])").matcher(u);while(m.find()){String v=m.group(1)+m.group(2);if(validDniLetter(v)){d.dni=v;break;}}}
        if(d.holder.isEmpty()){Matcher m=Pattern.compile("TOMADOR(?: DEL SEGURO)?(?: Y DOMICILIO)?\\s*(?:DOC\\.?\\s*ID.?\\s*[-:]*\\s*[0-9A-Z ]+)?\\s*\\n?\\s*([A-ZÁÉÍÓÚÑ]+(?:\\s+[A-ZÁÉÍÓÚÑ]+){2,})").matcher(u);if(m.find())d.holder=m.group(1).trim();}
        if(!isDecesosProduct(d.policyType)){d.decesos="";d.decesosLevelada="";}
        int score=0;if(!d.number.isEmpty())score+=30;if(!d.holder.isEmpty())score+=25;if(!d.dni.isEmpty())score+=20;if(!d.phone.isEmpty())score+=10;if(!d.receipt.isEmpty())score+=5;if(!d.capital.isEmpty())score+=10;d.confidence=Math.min(100,score);return d;
    }

    private String classifyPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("HOGAR")||u.contains("MULTIRRIESGO HOGAR"))return "Hogar";
        if(u.contains("SALUD")||u.contains("ASISTENCIA SANITARIA"))return "Salud";
        if(u.contains("AUTOMOVIL")||u.contains("AUTOMÓVIL")||u.contains("VEHICULO")||u.contains("VEHÍCULO"))return "Auto";
        if(u.contains("AHORRO")||u.contains("PIAS")||u.contains("RENTA"))return "Ahorro";
        if(u.contains("COMUNIDAD")||u.contains("COMUNIDADES"))return "Comunidades";
        if(u.contains("RESPONSABILIDAD CIVIL"))return "Responsabilidad civil";
        if(u.contains("EMPRESA")||u.contains("PYME")||u.contains("COMERCIO"))return "Empresa";
        return "Otros";
    }

    private boolean isDecesosProduct(String product){return "Decesos".equalsIgnoreCase(product);}

    private String moneyAfterLabels(String line,String... labels){
        for(String label:labels){int p=line.indexOf(label);if(p<0)continue;String tail=line.substring(p+label.length());Matcher m=Pattern.compile("(?<![0-9])([0-9]{1,8}(?:[.,][0-9]{1,2})?)(?:\\s*(?:€|EUR))?").matcher(tail);if(m.find())return m.group(1).replace(',','.');}
        return "";
    }
'''
s = replace_method(s, r'private\s+OcrData\s+parsePolicyOcr\s*\(\s*String\s+raw\s*,\s*String\s+kind\s*\)', replacement)
MAIN.write_text(s, encoding='utf-8')
print('Policy OCR parser patched')
