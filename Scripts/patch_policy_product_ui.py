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
            if depth == 0:
                return src[:start] + replacement + src[i+1:]
    raise SystemExit(f'unbalanced method: {signature}')


review = r'''    private void showPolicyReview(JSONObject p,String raw){
        shell("Revisión póliza Ocaso","Comprueba los datos y el documento antes de guardar");
        body.addView(tv("2 · DOCUMENTO PDF",18,BLUE,true));
        if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);body.addView(iv,new LinearLayout.LayoutParams(-1,dp(300)));}
        body.addView(tv("3 · DATOS ÚTILES DETECTADOS",18,BLUE,true));

        String product=p.optString("policyType",p.optString("type","Otros"));
        boolean decesos="Decesos".equalsIgnoreCase(product);
        int confidence=p.optInt("confidence",0);
        body.addView(tv("Confianza de interpretación: "+confidence+"%",14,confidence>=85?GREEN:(confidence>=60?TEXT:Color.rgb(180,80,40)),true));
        JSONArray warnings=p.optJSONArray("warnings");
        if(warnings!=null&&warnings.length()>0){
            body.addView(tv("REVISA ESTAS ALERTAS",13,Color.rgb(180,80,40),true));
            for(int i=0;i<warnings.length();i++) body.addView(tv("⚠ "+warnings.optString(i,""),13,TEXT,false));
        }

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

        addPolicyField("PRODUCTO",product,null);
        addPolicyField("Nº DE PÓLIZA",null,policyNumberE);
        addPolicyField("TOMADOR",null,holderE);
        addPolicyField("DNI / NIE",null,policyDniE);
        addPolicyField("DIRECCIÓN",null,policyAddressE);
        addPolicyField("TELÉFONO",null,policyPhoneE);
        addPolicyField("EMAIL",null,policyEmailE);
        addPolicyField("PRECIO / RECIBO",null,receiptE);

        if(decesos){
            addPolicyField("CAPITAL DE DECESOS",null,capitalE);
            addPolicyField("TOTAL DECESOS",null,decesosE);
            addPolicyField("DECESOS NIVELADA",null,decesosLeveladaE);
        }else if(!capitalE.getText().toString().trim().isEmpty()){
            addPolicyField("CAPITAL",null,capitalE);
        }

        body.addView(tv("ASEGURADOS DETECTADOS",16,BLUE,true));
        JSONArray ins=p.optJSONArray("insured");
        if(ins!=null)for(int i=0;i<ins.length();i++){JSONObject a=ins.optJSONObject(i);if(a!=null)body.addView(tv("• "+a.optString("name","")+" · "+a.optString("identityNumber","—")+" · "+a.optString("birthDate","—"),14,TEXT,false));}
        Button accept=btn("✅ ACEPTAR DATOS Y ASOCIAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);
        body.addView(accept,new LinearLayout.LayoutParams(-1,dp(64)));body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));
        accept.setOnClickListener(v->savePolicy(raw,ins));reject.setOnClickListener(v->ocrPage());
    }

    private void addPolicyField(String label,String value,EditText field){
        body.addView(tv(label,13,MUTED,true));
        if(field!=null) body.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));
        else body.addView(tv(value==null?"":value,16,TEXT,false),new LinearLayout.LayoutParams(-1,dp(54)));
    }
'''
s = replace_method(s, '    private void showPolicyReview(JSONObject p,String raw){', review)

policy_detail = r'''    private void policyDetail(JSONObject p){
        LinearLayout l=col();
        String product=p.optString("policyType",p.optString("type","Póliza"));
        boolean decesos="Decesos".equalsIgnoreCase(product);
        addRead(l,"Producto",product);
        addRead(l,"Número de póliza",p.optString("number",""));
        addRead(l,"Tomador",p.optString("holder",""));
        addRead(l,"DNI/NIE",p.optString("identityNumber",""));
        addRead(l,"Dirección",p.optString("address",""));
        addRead(l,"Teléfono",p.optString("phone",""));
        addRead(l,"Email",p.optString("email",""));
        addRead(l,"Precio / recibo",p.optString("receipt",""));
        if(decesos){
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
s = replace_method(s, '    private void policyDetail(JSONObject p){', policy_detail)

save = r'''    private void savePolicy(String raw,JSONArray insured){
        try{
            String id=policyDniE.getText().toString().trim().toUpperCase(Locale.ROOT),number=policyNumberE.getText().toString().trim();
            if(number.isEmpty()){Toast.makeText(this,"El número de póliza es obligatorio.",Toast.LENGTH_LONG).show();return;}
            if(!id.isEmpty()&&!isValidIdentity(id)){Toast.makeText(this,"DNI/NIE no válido. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            JSONObject parsed=OcasoPolicyParser.parse(raw);
            String product=parsed.optString("policyType",currentPolicyProduct(raw));
            JSONObject c=findClientById(id);if(c==null)c=findClientByName(holderE.getText().toString().trim());if(c==null)c=new JSONObject();
            c.put("holder",holderE.getText().toString().trim());c.put("name",holderE.getText().toString().trim());c.put("surname","");
            if(!id.isEmpty())c.put("identityNumber",id);c.put("address",policyAddressE.getText().toString().trim());c.put("phone",policyPhoneE.getText().toString().trim());c.put("email",policyEmailE.getText().toString().trim());
            JSONArray ps=c.optJSONArray("policies");if(ps==null)ps=new JSONArray();
            JSONObject pol=new JSONObject();
            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holderE.getText().toString().trim());pol.put("identityNumber",id);pol.put("address",policyAddressE.getText().toString().trim());pol.put("phone",policyPhoneE.getText().toString().trim());pol.put("email",policyEmailE.getText().toString().trim());pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());
            if("Decesos".equalsIgnoreCase(product)){
                pol.put("decesos",decesosE.getText().toString().trim());pol.put("decesosLevelada",decesosLeveladaE.getText().toString().trim());
            }else{
                pol.put("decesos","");pol.put("decesosLevelada","");
            }
            pol.put("insured",insured==null?new JSONArray():insured);pol.put("documentUri",documentUri==null?"":documentUri.toString());pol.put("ocrText",raw);pol.put("ocrConfidence",parsed.optInt("confidence",0));pol.put("ocrWarnings",parsed.optJSONArray("warnings"));
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
s = replace_method(s, '    private void savePolicy(String raw,JSONArray insured){', save)

MAIN.write_text(s, encoding='utf-8')
print('Product-specific policy OCR UI hardened')
