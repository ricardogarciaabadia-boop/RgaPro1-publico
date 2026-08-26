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
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + replacement + src[i + 1:]
    raise SystemExit(f'unbalanced method: {signature}')


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
                if(m.find()) d.number=m.group(1);
                else if(i+1<lines.length){
                    m=Pattern.compile("\\b([0-9]{5,12})\\b").matcher(lines[i+1]);
                    if(m.find()) d.number=m.group(1);
                }
            }

            if(line.contains("TOMADOR DEL SEGURO") || line.contains("TOMADOR DEL SEGURO Y DOMICILIO") || line.contains("TOMADOR")){
                Matcher m=Pattern.compile("TOMADOR(?: DEL SEGURO)?(?: Y DOMICILIO)?\\s*[:.-]*\\s*(.*?)(?=\\s+DOC\\.?\\s*ID|$)").matcher(line);
                if(m.find() && !m.group(1).trim().isEmpty()) d.holder=m.group(1).trim();
                Matcher id=Pattern.compile("(?:DOC\\.?\\s*ID|DNI|NIE)\\s*[-:]*\\s*([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]?)").matcher(line);
                if(id.find()) d.dni=id.group(1);
                if(d.holder.isEmpty() && i+1<lines.length){
                    String n=lines[i+1].replaceAll("\\s+"," ").trim();
                    if(!n.isEmpty() && !n.matches(".*(?:DOC\\.?\\s*ID|DNI|NIE|TELEFONO|TEL[ÉE]FONO).*")) d.holder=n;
                }
            }

            if(line.contains("DOC. ID") || line.contains("DOC ID") || line.matches(".*\\bDNI\\b.*") || line.matches(".*\\bNIE\\b.*")){
                Matcher id=Pattern.compile("(?:DOC\\.?\\s*ID|DNI|NIE)\\s*[-:]*\\s*([0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]?)").matcher(line);
                if(id.find()) d.dni=id.group(1);
            }

            if(line.matches(".*(?:TELEFONO|TEL[ÉE]FONO|M[ÓO]VIL).*")){
                Matcher m=Pattern.compile("\\b([6789][0-9]{8})\\b").matcher(line);
                if(m.find()) d.phone=m.group(1);
                if(d.phone.isEmpty() && i+1<lines.length){
                    m=Pattern.compile("\\b([6789][0-9]{8})\\b").matcher(lines[i+1]);
                    if(m.find()) d.phone=m.group(1);
                }
            }

            if(line.contains("DOMICILIO DE COBRO") || line.contains("DOMICILIO")){
                String v=line.replaceFirst(".*(?:DOMICILIO DE COBRO|DOMICILIO)\\s*[:.-]*\\s*","").trim();
                if(!v.isEmpty() && !v.matches("^(DOC\\.?\\s*ID|DNI|NIE).*$")) d.address=v;
                else if(i+1<lines.length) d.address=lines[i+1].trim();
            }

            if(line.contains("EMAIL") || line.contains("E-MAIL") || line.contains("CORREO")){
                Matcher m=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}").matcher(line);
                if(m.find()) d.email=m.group();
            }

            String receipt=moneyAfterLabels(line,"TOTAL RECIBO","TOTAL DEL RECIBO","TOTAL RECIBO POLIZA","PRIMA TOTAL","RECIBO");
            if(!receipt.isEmpty() && d.receipt.isEmpty()) d.receipt=receipt;

            String capital=moneyAfterLabels(line,"CAPITAL PRINCIPAL","SUMA ASEGURADA","CAPITAL ASEGURADO","CAPITAL CONTRATADO","CAPITAL");
            if(!capital.isEmpty() && d.capital.isEmpty()) d.capital=capital;

            String level=moneyAfterLabels(line,"DECESOS NIVELADA","DECÉSOS NIVELADA");
            if(!level.isEmpty()) d.decesosLevelada=level;

            String totalDec=moneyAfterLabels(line,"TOTAL DECESOS","TOTAL DECÉSOS");
            if(!totalDec.isEmpty()) d.decesos=totalDec;
        }

        if(d.number.isEmpty()){
            Matcher m=Pattern.compile("N[ºO°]?\\.?\\s*(?:DE\\s*)?P[ÓO]LIZA[^0-9]{0,30}([0-9]{5,12})").matcher(u);
            if(m.find()) d.number=m.group(1);
        }

        if(d.dni.isEmpty()){
            Matcher m=Pattern.compile("(?<![0-9])([0-9]{8})\\s*([A-Z])(?![A-Z0-9])").matcher(u);
            while(m.find()){
                String v=m.group(1)+m.group(2);
                if(validDniLetter(v)){d.dni=v;break;}
            }
        }

        if(d.holder.isEmpty()){
            Matcher m=Pattern.compile("TOMADOR(?: DEL SEGURO)?(?: Y DOMICILIO)?\\s*(?:DOC\\.?\\s*ID\\.?\\s*[-:]*\\s*[0-9A-Z ]+)?\\s*\\n?\\s*([A-ZÁÉÍÓÚÑ]+(?:\\s+[A-ZÁÉÍÓÚÑ]+){2,})").matcher(u);
            if(m.find()) d.holder=m.group(1).trim();
        }

        // En decesos estos tres importes tienen significado propio.
        // En cualquier otro producto NO se rellenan ni se muestran.
        if(!isDecesosProduct(d.policyType)){
            d.decesos="";
            d.decesosLevelada="";
        }

        int score=0;
        if(!d.number.isEmpty()) score+=30;
        if(!d.holder.isEmpty()) score+=25;
        if(!d.dni.isEmpty()) score+=20;
        if(!d.phone.isEmpty()) score+=10;
        if(!d.receipt.isEmpty()) score+=5;
        if(!d.capital.isEmpty()) score+=10;
        d.confidence=Math.min(100,score);
        return d;
    }

    private String classifyPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS") || u.contains("ASISTENCIA FAMILIAR")) return "Decesos";
        if(u.contains("VIDA") || u.contains("FALLECIMIENTO")) return "Vida";
        if(u.contains("ACCIDENTE") || u.contains("ACCIDENTES")) return "Accidentes";
        if(u.contains("HOGAR") || u.contains("MULTIRRIESGO HOGAR")) return "Hogar";
        if(u.contains("SALUD") || u.contains("ASISTENCIA SANITARIA")) return "Salud";
        if(u.contains("AUTOMOVIL") || u.contains("AUTOMÓVIL") || u.contains("VEHICULO") || u.contains("VEHÍCULO")) return "Auto";
        if(u.contains("AHORRO") || u.contains("PIAS") || u.contains("RENTA")) return "Ahorro";
        if(u.contains("COMUNIDAD") || u.contains("COMUNIDADES")) return "Comunidades";
        if(u.contains("RESPONSABILIDAD CIVIL")) return "Responsabilidad civil";
        if(u.contains("EMPRESA") || u.contains("PYME") || u.contains("COMERCIO")) return "Empresa";
        return "Otros";
    }

    private boolean isDecesosProduct(String product){
        return "Decesos".equalsIgnoreCase(product);
    }

    private String moneyAfterLabels(String line,String... labels){
        for(String label:labels){
            int p=line.indexOf(label);
            if(p<0) continue;
            String tail=line.substring(p+label.length());
            Matcher m=Pattern.compile("(?<![0-9])([0-9]{1,8}(?:[.,][0-9]{1,2})?)(?:\\s*(?:€|EUR))?").matcher(tail);
            if(m.find()) return m.group(1).replace(',','.');
        }
        return "";
    }
'''
s = replace_method(s, '    private OcrData parsePolicyOcr(String raw,String kind){', replacement)

review_replacement = r'''    private void showPolicyReview(JSONObject p,String raw){
        shell("Revisión póliza Ocaso","Comprueba los datos y el documento antes de guardar");
        body.addView(tv("2 · DOCUMENTO PDF",18,BLUE,true));
        if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);body.addView(iv,new LinearLayout.LayoutParams(-1,dp(300)));}
        body.addView(tv("3 · DATOS ÚTILES DETECTADOS",18,BLUE,true));

        policyNumberE=input("Número de póliza");
        holderE=input("Tomador (nombre y apellidos)");
        policyDniE=input("DNI/NIE");
        policyAddressE=input("Dirección");
        policyPhoneE=input("Teléfono");
        policyEmailE=input("Email");
        receiptE=input("Precio / total recibo");
        capitalE=input("Capital");
        decesosE=input("Total decesos");
        decesosLeveladaE=input("Decesos nivelada");

        String product=p.optString("policyType",p.optString("type","Otros"));
        boolean decesos=isDecesosProduct(product);
        policyNumberE.setText(p.optString("number",""));
        holderE.setText(p.optString("holder",""));
        policyDniE.setText(p.optString("identityNumber",p.optString("dni","")));
        policyAddressE.setText(p.optString("address",""));
        policyPhoneE.setText(p.optString("phone",""));
        policyEmailE.setText(p.optString("email",""));
        receiptE.setText(p.optString("receipt",""));
        capitalE.setText(p.optString("capital",""));
        decesosE.setText(p.optString("decesos",""));
        decesosLeveladaE.setText(p.optString("decesosLevelada",""));

        addPolicyReviewField("PRODUCTO",product,body);
        addPolicyReviewField("Nº DE PÓLIZA",policyNumberE,body);
        addPolicyReviewField("TOMADOR",holderE,body);
        addPolicyReviewField("DNI / NIE",policyDniE,body);
        addPolicyReviewField("DIRECCIÓN",policyAddressE,body);
        addPolicyReviewField("TELÉFONO",policyPhoneE,body);
        addPolicyReviewField("EMAIL",policyEmailE,body);
        addPolicyReviewField("PRECIO / RECIBO",receiptE,body);

        if(decesos){
            addPolicyReviewField("CAPITAL DE DECESOS",capitalE,body);
            addPolicyReviewField("TOTAL DECESOS",decesosE,body);
            addPolicyReviewField("DECESOS NIVELADA",decesosLeveladaE,body);
        }else if(!capitalE.getText().toString().trim().isEmpty()){
            addPolicyReviewField("CAPITAL",capitalE,body);
        }

        body.addView(tv("ASEGURADOS DETECTADOS",16,BLUE,true));
        JSONArray ins=p.optJSONArray("insured");
        if(ins!=null)for(int i=0;i<ins.length();i++){JSONObject a=ins.optJSONObject(i);if(a!=null)body.addView(tv("• "+a.optString("name","")+" · "+a.optString("identityNumber","—")+" · "+a.optString("birthDate","—"),14,TEXT,false));}

        Button accept=btn("✅ ACEPTAR DATOS Y ASOCIAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);
        body.addView(accept,new LinearLayout.LayoutParams(-1,dp(64)));body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));
        accept.setOnClickListener(v->savePolicy(raw,ins));
        reject.setOnClickListener(v->ocrPage());
    }

    private void addPolicyReviewField(String label,String value,LinearLayout target){
        target.addView(tv(label,13,MUTED,true));
        EditText e=input(label);
        e.setText(value==null?"":value);
        target.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));
        if("Nº DE PÓLIZA".equals(label)) policyNumberE=e;
        else if("TOMADOR".equals(label)) holderE=e;
        else if("DNI / NIE".equals(label)) policyDniE=e;
        else if("DIRECCIÓN".equals(label)) policyAddressE=e;
        else if("TELÉFONO".equals(label)) policyPhoneE=e;
        else if("EMAIL".equals(label)) policyEmailE=e;
        else if("PRECIO / RECIBO".equals(label)) receiptE=e;
        else if("CAPITAL".equals(label)||"CAPITAL DE DECESOS".equals(label)) capitalE=e;
        else if("TOTAL DECESOS".equals(label)) decesosE=e;
        else if("DECESOS NIVELADA".equals(label)) decesosLeveladaE=e;
    }
'''
s = replace_method(s, '    private void showPolicyReview(JSONObject p,String raw){', review_replacement)

policy_detail_replacement = r'''    private void policyDetail(JSONObject p){
        LinearLayout l=col();
        addRead(l,"Producto",p.optString("policyType",p.optString("type","Póliza")));
        addRead(l,"Número de póliza",p.optString("number",""));
        addRead(l,"Tomador",p.optString("holder",""));
        addRead(l,"DNI/NIE",p.optString("identityNumber",""));
        addRead(l,"Dirección",p.optString("address",""));
        addRead(l,"Teléfono",p.optString("phone",""));
        addRead(l,"Email",p.optString("email",""));
        addRead(l,"Precio / recibo",p.optString("receipt",""));
        String product=p.optString("policyType",p.optString("type","Otros"));
        if(isDecesosProduct(product)){
            addRead(l,"Capital de decesos",p.optString("capital",""));
            addRead(l,"Total decesos",p.optString("decesos",""));
            addRead(l,"Decesos nivelada",p.optString("decesosLevelada",""));
        }else if(!p.optString("capital","").trim().isEmpty()){
            addRead(l,"Capital",p.optString("capital",""));
        }
        l.addView(tv("ASEGURADOS",16,BLUE,true));
        JSONArray ins=p.optJSONArray("insured");
        if(ins!=null)for(int i=0;i<ins.length();i++){JSONObject a=ins.optJSONObject(i);if(a==null)continue;addRead(l,a.optString("name","Asegurado"),"DNI: "+a.optString("identityNumber","—")+" · Nacimiento: "+a.optString("birthDate","—"));}
        new AlertDialog.Builder(this).setTitle("Póliza Ocaso").setView(l).setPositiveButton("Cerrar",null).show();
    }
'''
s = replace_method(s, '    private void policyDetail(JSONObject p){', policy_detail_replacement)

# Ensure non-decesos policies never retain decesos-specific values when saving.
save_replacement = r'''    private void savePolicy(String raw,JSONArray insured){
        try{
            String id=policyDniE.getText().toString().trim().toUpperCase(Locale.ROOT),number=policyNumberE.getText().toString().trim();
            if(number.isEmpty()){Toast.makeText(this,"El número de póliza es obligatorio.",Toast.LENGTH_LONG).show();return;}
            JSONObject c=findClientById(id);if(c==null)c=findClientByName(holderE.getText().toString().trim());if(c==null)c=new JSONObject();
            c.put("holder",holderE.getText().toString().trim());c.put("name",holderE.getText().toString().trim());c.put("surname","");
            if(!id.isEmpty())c.put("identityNumber",id);c.put("address",policyAddressE.getText().toString().trim());c.put("phone",policyPhoneE.getText().toString().trim());c.put("email",policyEmailE.getText().toString().trim());
            JSONArray ps=c.optJSONArray("policies");if(ps==null)ps=new JSONArray();
            JSONObject pol=new JSONObject();
            String product=currentPolicyProduct(raw);
            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holderE.getText().toString().trim());pol.put("identityNumber",id);
            pol.put("address",policyAddressE.getText().toString().trim());pol.put("phone",policyPhoneE.getText().toString().trim());pol.put("email",policyEmailE.getText().toString().trim());pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());
            if(isDecesosProduct(product)){
                pol.put("decesos",decesosE.getText().toString().trim());
                pol.put("decesosLevelada",decesosLeveladaE.getText().toString().trim());
            }else{
                pol.put("decesos","");
                pol.put("decesosLevelada","");
            }
            pol.put("insured",insured==null?new JSONArray():insured);pol.put("documentUri",documentUri==null?"":documentUri.toString());pol.put("ocrText",raw);
            boolean replaced=false;for(int i=0;i<ps.length();i++){JSONObject old=ps.optJSONObject(i);if(old!=null&&number.equals(old.optString("number",""))){ps.put(i,pol);replaced=true;break;}}
            if(!replaced)ps.put(pol);c.put("policies",ps);upsertClient(c);Toast.makeText(this,"Póliza guardada y asociada al cliente.",Toast.LENGTH_LONG).show();detail(c);
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private String currentPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("HOGAR"))return "Hogar";
        if(u.contains("SALUD"))return "Salud";
        if(u.contains("AUTOMOVIL")||u.contains("AUTOMÓVIL")||u.contains("VEHICULO")||u.contains("VEHÍCULO"))return "Auto";
        return "Otros";
    }
'''
s = replace_method(s, '    private void savePolicy(String raw,JSONArray insured){', save_replacement)

MAIN.write_text(s, encoding='utf-8')
print('Policy OCR parser/UI patched: product-aware capital fields')
