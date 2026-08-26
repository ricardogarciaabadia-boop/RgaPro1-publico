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
            if depth == 0: return src[:start] + replacement + src[i+1:]
    raise SystemExit(f'unbalanced method: {signature}')

replacement = r'''    private OcrData parsePolicyOcr(String raw,String kind){
        OcrData d=new OcrData(); d.documentKind="POLIZA"; d.policyType="OCASO";
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT).replace('\r','\n').replace("\t"," ");
        String[] lines=u.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=lines[i].replaceAll("\\s+"," ").trim();
            if(line.isEmpty()) continue;
            if(line.matches(".*N[ºO°]?\\.?\\s*DE\\s*P[ÓO]LIZA.*") || line.matches(".*N[ºO°]?\\.?\\s*P[ÓO]LIZA.*")){
                Matcher m=Pattern.compile("(?:N[ºO°]?\\.?\\s*(?:DE\\s*)?P[ÓO]LIZA)\\s*[:.-]*\\s*([0-9]{5,10})").matcher(line);
                if(m.find()) d.number=m.group(1); else if(i+1<lines.length){m=Pattern.compile("\\b([0-9]{5,10})\\b").matcher(lines[i+1]);if(m.find())d.number=m.group(1);}
            }
            if(line.contains("TOMADOR DEL SEGURO") || line.contains("TOMADOR DEL SEGURO Y DOMICILIO")){
                Matcher m=Pattern.compile("TOMADOR DEL SEGURO(?: Y DOMICILIO)?\\s*[:.-]*\\s*(.*?)(?=\\s+DOC\\.?\\s*ID|$)").matcher(line);
                if(m.find() && !m.group(1).trim().isEmpty()) d.holder=m.group(1).trim();
                Matcher id=Pattern.compile("DOC\\.?\\s*ID\\.?\\s*[-:]*\\s*([0-9]{8})\\s*([A-Z])?").matcher(line);
                if(id.find()) d.dni=id.group(1)+(id.group(2)==null?"":id.group(2));
                if(d.holder.isEmpty() && i+1<lines.length) d.holder=lines[i+1].replaceAll("\\s+"," ").trim();
            }
            if(line.contains("DOC. ID") || line.contains("DOC ID")){
                Matcher id=Pattern.compile("DOC\\.?\\s*ID\\.?\\s*[-:]*\\s*([0-9]{8})\\s*([A-Z])?").matcher(line);
                if(id.find()) d.dni=id.group(1)+(id.group(2)==null?"":id.group(2));
            }
            if(line.matches(".*(?:TELEFONO|TEL[ÉE]FONO).*")){
                Matcher m=Pattern.compile("\\b([6789][0-9]{8})\\b").matcher(line);if(m.find())d.phone=m.group(1);
                if(d.phone.isEmpty() && i+1<lines.length){m=Pattern.compile("\\b([6789][0-9]{8})\\b").matcher(lines[i+1]);if(m.find())d.phone=m.group(1);}
            }
            if(line.contains("DOMICILIO DE COBRO")){String v=line.replaceFirst(".*DOMICILIO DE COBRO\\s*[:.-]*\\s*","").trim();if(!v.isEmpty())d.address=v;else if(i+1<lines.length)d.address=lines[i+1].trim();}
            if(line.contains("EMAIL") || line.contains("E-MAIL")){Matcher m=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}").matcher(line);if(m.find())d.email=m.group();}
            if(line.contains("PRIMA NETA") || line.contains("TOTAL RECIBO")){Matcher m=Pattern.compile("(?:TOTAL RECIBO|TOTAL)\\s*[:.-]*\\s*([0-9]+(?:[.,][0-9]{1,2})?)").matcher(line);if(m.find())d.receipt=m.group(1).replace(',','.');}
            if(line.contains("CAPITAL PRINCIPAL") || line.contains("SUMA ASEGURADA")){Matcher m=Pattern.compile("(?:SUMA ASEGURADA|CAPITAL PRINCIPAL).*?([0-9]{2,3}(?:[.,][0-9]{2})?)").matcher(line);if(m.find())d.capital=m.group(1).replace(',','.');}
            if(line.contains("DECESOS") && !line.contains("NIVELADA")){Matcher m=Pattern.compile("DECESOS.*?([0-9]{2,3}(?:[.,][0-9]{2})?)").matcher(line);if(m.find())d.decesos=m.group(1).replace(',','.');}
            if(line.contains("DECESOS NIVELADA")){Matcher m=Pattern.compile("DECESOS NIVELADA.*?([0-9]{2,3}(?:[.,][0-9]{2})?)").matcher(line);if(m.find())d.decesosLevelada=m.group(1).replace(',','.');}
        }
        if(d.number.isEmpty()){
            Matcher m=Pattern.compile("N[ºO°]?\\.?\\s*(?:DE\\s*)?P[ÓO]LIZA[^0-9]{0,20}([0-9]{5,10})").matcher(u);if(m.find())d.number=m.group(1);
        }
        if(d.dni.isEmpty()){
            Matcher m=Pattern.compile("(?<![0-9])([0-9]{8})\\s*([A-Z])(?![A-Z0-9])").matcher(u);while(m.find()){String v=m.group(1)+m.group(2);if(validDniLetter(v)){d.dni=v;break;}}
        }
        if(d.holder.isEmpty()){
            Matcher m=Pattern.compile("TOMADOR DEL SEGURO(?: Y DOMICILIO)?\\s*(?:DOC\\.?\\s*ID\\.?\\s*[-:]*\\s*[0-9A-Z ]+)?\\s*\\n?\\s*([A-ZÁÉÍÓÚÑ]+(?:\\s+[A-ZÁÉÍÓÚÑ]+){2,})").matcher(u);if(m.find())d.holder=m.group(1).trim();
        }
        int score=0;if(!d.number.isEmpty())score+=35;if(!d.holder.isEmpty())score+=25;if(!d.dni.isEmpty())score+=20;if(!d.phone.isEmpty())score+=10;if(!d.receipt.isEmpty()||!d.capital.isEmpty())score+=10;d.confidence=Math.min(100,score);
        return d;
    }
'''
s = replace_method(s, '    private OcrData parsePolicyOcr(String raw,String kind){', replacement)
MAIN.write_text(s, encoding='utf-8')
print('Policy OCR parser patched')
