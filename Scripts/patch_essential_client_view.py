from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
CLIENT = Path('app/src/main/java/com/rgapro1/ocaso/Client360Activity.java')


def replace_method(src: str, signature: str, replacement: str) -> str:
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = src.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + replacement + src[i + 1:]
    raise SystemExit(f'unbalanced method: {signature}')


def replace_once(src: str, old: str, new: str) -> str:
    if old not in src:
        raise SystemExit(f'text not found: {old[:80]}')
    return src.replace(old, new, 1)


s = MAIN.read_text(encoding='utf-8')

# The OCR may read many auxiliary values for validation, but the client UI exposes only
# the five requested fields. Policy internals remain available to association/archive code.
detail = r'''    private void detail(JSONObject x){
        shell("Ficha de cliente",clientKey(x));
        body.addView(tv(clientKey(x),25,TEXT,true));
        body.addView(tv("DATOS DEL CLIENTE",16,BLUE,true));
        addRead(body,"DNI/NIE",x.optString("identityNumber",x.optString("holderDni","")));
        addRead(body,"Fecha de nacimiento",x.optString("birthDate",""));
        addRead(body,"Dirección",x.optString("address",""));
        addRead(body,"Teléfono",x.optString("phone",""));
        body.addView(tv("DOCUMENTOS Y PÓLIZAS",16,BLUE,true));
        JSONArray docs=x.optJSONArray("documentPhotos");
        if(docs!=null) for(int i=0;i<docs.length();i++){
            Object item=docs.opt(i); String path=item instanceof JSONObject?((JSONObject)item).optString("path",""):String.valueOf(item);
            if(path==null||path.trim().isEmpty()) continue;
            Button d=btn("📄 "+new File(path).getName(),false);
            d.setOnClickListener(v->openDocument(path));
            body.addView(d,new LinearLayout.LayoutParams(-1,dp(56)));
        }
        JSONArray ps=x.optJSONArray("policies");
        if(ps!=null) for(int i=0;i<ps.length();i++){
            JSONObject p=ps.optJSONObject(i); if(p==null) continue;
            Button b=btn("▣ Póliza · "+p.optString("number","Sin número"),false);
            b.setOnClickListener(v->policyDetail(p));
            body.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));
        }
        Button edit=btn("✏️ EDITAR",true); edit.setOnClickListener(v->editClient(x));
        body.addView(edit,new LinearLayout.LayoutParams(-1,dp(58)));
    }
'''
s = replace_method(s, '    private void detail(JSONObject x){', detail)

edit = r'''    private void editClient(JSONObject old){
        LinearLayout l=col();
        EditText n=input("Nombre y apellidos"),d=input("DNI/NIE"),b=input("Fecha de nacimiento dd/MM/yyyy"),ad=input("Dirección"),ph=input("Teléfono");
        if(old!=null){
            n.setText(clientKey(old));
            d.setText(old.optString("identityNumber",old.optString("holderDni","")));
            b.setText(old.optString("birthDate",""));
            ad.setText(old.optString("address",""));
            ph.setText(old.optString("phone",""));
        }
        for(EditText e:new EditText[]{n,d,b,ad,ph}) l.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));
        new AlertDialog.Builder(this).setTitle(old==null?"Nuevo cliente":"Editar cliente").setView(l)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(di,w)->{
                try{
                    JSONObject x=old==null?new JSONObject():old;
                    String full=n.getText().toString().trim();
                    x.put("holder",full); x.put("name",full); x.put("surname","");
                    x.put("identityNumber",d.getText().toString().trim().toUpperCase(Locale.ROOT));
                    x.put("birthDate",b.getText().toString().trim());
                    x.put("address",ad.getText().toString().trim());
                    x.put("phone",ph.getText().toString().trim());
                    if(!x.has("policies")) x.put("policies",new JSONArray());
                    if(!x.has("documentPhotos")) x.put("documentPhotos",new JSONArray());
                    upsertClient(x); clients();
                }catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
            }).show();
    }
'''
s = replace_method(s, '    private void editClient(JSONObject old){', edit)

# Open archived files from the client card.
marker = '    private void addRead(LinearLayout p,String label,String value){'
helper = r'''    private void openDocument(String path){
        try{
            File f=new File(path); if(!f.exists()){Toast.makeText(this,"No se encuentra el documento",Toast.LENGTH_LONG).show();return;}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(u,mime(path)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        }catch(Exception e){Toast.makeText(this,"No se pudo abrir el documento",Toast.LENGTH_LONG).show();}
    }
    private String mime(String p){String x=p.toLowerCase(Locale.ROOT);if(x.endsWith(".pdf"))return "application/pdf";if(x.endsWith(".png"))return "image/png";if(x.endsWith(".webp"))return "image/webp";return "image/jpeg";}

'''
if 'private void openDocument(String path)' not in s:
    s = s.replace(marker, helper + marker, 1)

# Keep the policy review operationally rich internally, but display only the requested client fields.
review = r'''    private void showPolicyReview(JSONObject p,String raw){
        shell("Revisión de póliza","Comprueba los datos del tomador antes de guardar");
        body.addView(tv("DATOS DEL CLIENTE",18,BLUE,true));
        int confidence=p.optInt("confidence",p.optInt("ocrConfidence",0));
        body.addView(tv("Confianza de lectura: "+confidence+"%",14,confidence>=85?GREEN:(confidence>=60?TEXT:Color.rgb(180,80,40)),true));
        body.addView(tv("Solo se muestran los datos esenciales. El documento completo se archivará en el cliente.",13,MUTED,false));

        policyNumberE=input("Número de póliza");
        holderE=input("Nombre y apellidos");
        policyDniE=input("DNI/NIE");
        policyAddressE=input("Dirección");
        policyPhoneE=input("Teléfono");
        policyEmailE=input("Email");
        receiptE=input("Precio / total recibo");
        capitalE=input("Capital");
        decesosE=input("Total decesos");
        decesosLeveladaE=input("Decesos nivelada");

        policyNumberE.setText(p.optString("number",""));
        holderE.setText(p.optString("holder",p.optString("name","")));
        policyDniE.setText(p.optString("identityNumber",p.optString("dni",p.optString("holderDni",""))));
        policyAddressE.setText(p.optString("address",""));
        policyPhoneE.setText(p.optString("phone",""));
        policyEmailE.setText(p.optString("email",""));
        receiptE.setText(p.optString("receipt",""));
        capitalE.setText(p.optString("capital",""));
        decesosE.setText(p.optString("decesos",""));
        decesosLeveladaE.setText(p.optString("decesosLevelada",""));

        addPolicyField("NOMBRE Y APELLIDOS",null,holderE);
        addPolicyField("DNI / NIE",null,policyDniE);
        addPolicyField("FECHA DE NACIMIENTO",null,input("Fecha de nacimiento"));
        EditText birth=lastEditable(body); if(birth!=null) birth.setText(p.optString("birthDate",""));
        addPolicyField("DIRECCIÓN",null,policyAddressE);
        addPolicyField("TELÉFONO",null,policyPhoneE);

        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);
        body.addView(accept,new LinearLayout.LayoutParams(-1,dp(64))); body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));
        accept.setOnClickListener(v->savePolicy(raw,p.optJSONArray("insured")));
        reject.setOnClickListener(v->ocrPage());
    }
'''
# Avoid depending on lastEditable helper: use a direct local field replacement after creation.
review = review.replace('        addPolicyField("FECHA DE NACIMIENTO",null,input("Fecha de nacimiento"));\n        EditText birth=lastEditable(body); if(birth!=null) birth.setText(p.optString("birthDate",""));',
'''        EditText birth=input("Fecha de nacimiento");
        birth.setText(p.optString("birthDate",""));
        addPolicyField("FECHA DE NACIMIENTO",null,birth);''')
s = replace_method(s, '    private void showPolicyReview(JSONObject p,String raw){', review)

# Ensure the archived policy file is also attached to the client-level document archive.
save_sig='    private void savePolicy(String raw,JSONArray insured){'
save_start=s.find(save_sig)
if save_start<0: raise SystemExit('savePolicy not found')
# Inject document archive after the client is loaded/created, before upsertClient(c).
needle='            if(c==null)c=new JSONObject();\n'
inject='''            if(c==null)c=new JSONObject();\n            if(!c.has("documentPhotos")) c.put("documentPhotos",new JSONArray());\n            JSONArray clientDocs=c.optJSONArray("documentPhotos");\n            String archivedUri=documentUri==null?"":documentUri.toString();\n            if(!archivedUri.isEmpty()) {\n                boolean already=false;\n                for(int di=0;di<clientDocs.length();di++){Object it=clientDocs.opt(di);String path=it instanceof JSONObject?((JSONObject)it).optString("path",""):String.valueOf(it);if(archivedUri.equals(path)){already=true;break;}}\n                if(!already){JSONObject d=new JSONObject();d.put("path",archivedUri);d.put("kind","policy");d.put("createdAt",System.currentTimeMillis());clientDocs.put(d);}\n            }\n            c.put("documentPhotos",clientDocs);\n'''
if needle not in s: raise SystemExit('savePolicy insertion point not found')
s=s.replace(needle,inject,1)

MAIN.write_text(s,encoding='utf-8')

# Client360: show/edit only the same five fields while retaining document archive access.
c=CLIENT.read_text(encoding='utf-8')
show = r'''    private void show(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(10),dp(8),dp(10),dp(8));head.setBackgroundColor(NAVY);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button back=btn("↩️ VOLVER");back.setTextColor(-1);back.setTextSize(17);back.setOnClickListener(v->finish());
        Button edit=btn("✏️ EDITAR");edit.setTextSize(16);edit.setOnClickListener(v->editClient());
        buttons.addView(back,new LinearLayout.LayoutParams(0,dp(58),1));buttons.addView(edit,new LinearLayout.LayoutParams(0,dp(58),1));
        head.addView(buttons);head.addView(t("🔵 CLIENTE 360º",22,true));root.addView(head);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(12),dp(12),dp(20));
        body.addView(t("👤 "+client.optString("holder",client.optString("name","Cliente")),23,true));
        String id=client.optString("identityNumber",client.optString("holderDni","—"));
        body.addView(t("DNI/NIE: "+id+"\nFecha de nacimiento: "+client.optString("birthDate","—")+"\nDirección: "+client.optString("address","—")+"\nTeléfono: "+client.optString("phone","—"),16,false));
        addGroup(body,"📦 PÓLIZAS",client);addGroup(body,"📄 DOCUMENTACIÓN",client);
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
'''
c=replace_method(c,'    private void show(){',show)
editc = r'''    private void editClient(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(8),0,dp(8),0);
        EditText holder=field("Nombre y apellidos",client.optString("holder",client.optString("name","")));
        EditText identity=field("DNI / NIE",client.optString("identityNumber",client.optString("holderDni","")));
        EditText birth=field("Fecha de nacimiento",client.optString("birthDate",""));
        EditText address=field("Dirección",client.optString("address",""));
        EditText phone=field("Teléfono",client.optString("phone",""));
        EditText[] fields=new EditText[]{holder,identity,birth,address,phone};
        for(EditText e:fields)form.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Editar cliente").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try{
                client.put("holder",holder.getText().toString().trim());
                client.put("name",holder.getText().toString().trim());client.put("surname","");
                client.put("identityNumber",identity.getText().toString().trim().toUpperCase(java.util.Locale.ROOT));
                client.put("holderDni",identity.getText().toString().trim().toUpperCase(java.util.Locale.ROOT));
                client.put("birthDate",birth.getText().toString().trim());client.put("address",address.getText().toString().trim());client.put("phone",phone.getText().toString().trim());
                client.put("updatedAt",System.currentTimeMillis());
                if(saveClient(client)){dialog.dismiss();show();Toast.makeText(this,"✅ Datos del cliente guardados",Toast.LENGTH_LONG).show();}
                else Toast.makeText(this,"No se encontró el cliente original",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"No se pudieron guardar los cambios",Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }
'''
c=replace_method(c,'    private void editClient(){',editc)

# Replace addGroup so policy and document access remain, but no auxiliary identity fields are displayed.
addgroup = r'''    private void addGroup(LinearLayout body,String title,JSONObject p){
        body.addView(t(title,18,true));
        if(title.contains("PÓLIZAS")){
            JSONArray ps=p.optJSONArray("policies");
            if(ps!=null) for(int i=0;i<ps.length();i++){
                JSONObject pol=ps.optJSONObject(i);if(pol==null)continue;
                Button product=btn("▣ Póliza · "+pol.optString("number","Sin número"));
                product.setOnClickListener(v->showProduct(pol));body.addView(product,new LinearLayout.LayoutParams(-1,dp(60)));
            }
        }
        if(title.contains("DOCUMENTACIÓN")){
            JSONArray docs=p.optJSONArray("documentPhotos");
            if(docs!=null) for(int i=0;i<docs.length();i++){
                String path=documentPath(docs.opt(i));if(path.isEmpty())continue;
                Button d=btn("📄 "+new File(path).getName());d.setOnClickListener(v->documentMenu(path));body.addView(d,new LinearLayout.LayoutParams(-1,dp(58)));
            }
        }
    }
'''
c=replace_method(c,'    private void addGroup(LinearLayout body,String title,JSONObject p){',addgroup)
CLIENT.write_text(c,encoding='utf-8')
print('Essential client view applied')
