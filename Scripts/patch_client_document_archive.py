from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s = MAIN.read_text(encoding='utf-8')


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

helpers = r'''    private String copyDocumentToArchive(Uri source, String prefix, String extension) throws Exception {
        if (source == null) return "";
        File dir = new File(getExternalFilesDir("documents"), "clients");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se pudo crear el archivo de documentos");
        String ext = extension == null || extension.isEmpty() ? ".bin" : (extension.startsWith(".") ? extension : "." + extension);
        File out = new File(dir, prefix + "_" + System.currentTimeMillis() + ext);
        try (InputStream in = getContentResolver().openInputStream(source); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Documento no disponible");
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) >= 0) { if (n > 0) os.write(buf, 0, n); }
        }
        return out.getAbsolutePath();
    }
    private void addArchivedDocument(JSONObject client, String localPath, String sourceUri, String title, String type, String policyNumber) throws Exception {
        if (localPath == null || localPath.isEmpty()) return;
        JSONArray docs = client.optJSONArray("documents");
        if (docs == null) docs = new JSONArray();
        String source = sourceUri == null ? "" : sourceUri;
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.optJSONObject(i);
            if (d != null && ((source.length() > 0 && source.equals(d.optString("sourceUri", ""))) || localPath.equals(d.optString("localPath", "")))) return;
        }
        JSONObject d = new JSONObject();
        d.put("id", UUID.randomUUID().toString());
        d.put("title", title == null || title.isEmpty() ? "Documento" : title);
        d.put("type", type == null ? "document" : type);
        d.put("localPath", localPath);
        d.put("sourceUri", source);
        d.put("policyNumber", policyNumber == null ? "" : policyNumber);
        d.put("createdAt", System.currentTimeMillis());
        docs.put(d);
        client.put("documents", docs);
    }
    private JSONObject findClientForPolicy(String id, String holder, String phone, String email) {
        JSONObject c = findClientById(id);
        if (c != null) return c;
        String q = normalizeSearch(holder);
        if (!q.isEmpty()) {
            JSONArray a = clientsData();
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && q.equals(normalizeSearch(clientKey(x)))) return x;
            }
        }
        if (phone != null && !phone.trim().isEmpty()) {
            JSONArray a = clientsData();
            String p = normalizeSearch(phone);
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && p.equals(normalizeSearch(x.optString("phone", "")))) return x;
            }
        }
        if (email != null && !email.trim().isEmpty()) {
            JSONArray a = clientsData();
            String e = email.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && e.equals(x.optString("email", "").trim().toLowerCase(Locale.ROOT))) return x;
            }
        }
        return null;
    }
    private void openArchivedDocument(String path, String type) {
        try {
            File f = new File(path);
            if (!f.exists()) { Toast.makeText(this, "El documento ya no está disponible en el dispositivo.", Toast.LENGTH_LONG).show(); return; }
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            String mime = "application/octet-stream";
            if (type != null && type.toLowerCase(Locale.ROOT).contains("pdf")) mime = "application/pdf";
            else if (type != null && type.toLowerCase(Locale.ROOT).contains("image")) mime = "image/*";
            i.setDataAndType(u, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No hay una aplicación para abrir este documento.", Toast.LENGTH_LONG).show();
        }
    }
    private void addDocumentsToDetail(LinearLayout target, JSONObject client) {
        target.addView(tv("DOCUMENTOS ADJUNTOS",16,BLUE,true));
        JSONArray docs = client.optJSONArray("documents");
        if (docs == null || docs.length() == 0) {
            target.addView(tv("No hay documentos archivados todavía.",14,MUTED,false));
            return;
        }
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.optJSONObject(i); if (d == null) continue;
            String title = d.optString("title", "Documento");
            String policy = d.optString("policyNumber", "");
            String label = "📎 " + title + (policy.isEmpty() ? "" : " · Póliza " + policy);
            Button b = btn(label, false);
            String path = d.optString("localPath", "");
            String type = d.optString("type", "document");
            b.setOnClickListener(v -> openArchivedDocument(path, type));
            target.addView(b, new LinearLayout.LayoutParams(-1, dp(58)));
        }
    }
'''
if 'copyDocumentToArchive' not in s:
    anchor = '    private JSONObject findClientById(String id){'
    s = s.replace(anchor, helpers + '\n' + anchor, 1)

save_identity = r'''    private void saveIdentity(){
        try{
            String full=fullNameE.getText().toString().trim(),id=dniE.getText().toString().trim().toUpperCase(Locale.ROOT),birth=birthE.getText().toString().trim();
            if(!isValidIdentity(id)){Toast.makeText(this,"DNI/NIE no válido. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            if(!validDate(birth)){Toast.makeText(this,"Fecha de nacimiento no válida.",Toast.LENGTH_LONG).show();return;}
            JSONObject x=findClientById(id);if(x==null)x=new JSONObject();
            x.put("holder",full);x.put("name",full);x.put("surname","");x.put("identityNumber",id);x.put("birthDate",birth);x.put("address",addressE.getText().toString().trim());x.put("phone",phoneE.getText().toString().trim());
            if(!x.has("policies"))x.put("policies",new JSONArray());
            if(frontImagePath!=null&&!frontImagePath.isEmpty()){
                try{String p=copyDocumentToArchive(Uri.parse(frontImagePath),"dni_front","jpg");addArchivedDocument(x,p,frontImagePath,"DNI/NIE · anverso","image","");}catch(Exception ignored){}
            }
            if(backImagePath!=null&&!backImagePath.isEmpty()){
                try{String p=copyDocumentToArchive(Uri.parse(backImagePath),"dni_back","jpg");addArchivedDocument(x,p,backImagePath,"DNI/NIE · reverso","image","");}catch(Exception ignored){}
            }
            upsertClient(x);Toast.makeText(this,"Cliente guardado con sus documentos.",Toast.LENGTH_LONG).show();detail(x);
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
'''
s = replace_method(s, '    private void saveIdentity(){', save_identity)

save_policy = r'''    private void savePolicy(String raw,JSONArray insured){
        try{
            String id=policyDniE.getText().toString().trim().toUpperCase(Locale.ROOT),number=policyNumberE.getText().toString().trim();
            JSONObject parsed=OcasoPolicyParser.parse(raw);
            if(id.isEmpty()) id=parsed.optString("identityNumber","").trim().toUpperCase(Locale.ROOT);
            String holder=holderE.getText().toString().trim(); if(holder.isEmpty()) holder=parsed.optString("holder","").trim();
            String address=policyAddressE.getText().toString().trim(); if(address.isEmpty()) address=parsed.optString("address","").trim();
            String phone=policyPhoneE.getText().toString().trim(); if(phone.isEmpty()) phone=parsed.optString("phone","").trim();
            String email=policyEmailE.getText().toString().trim(); if(email.isEmpty()) email=parsed.optString("email","").trim();
            if(number.isEmpty()) number=parsed.optString("number","").trim();
            if(number.isEmpty()){Toast.makeText(this,"El número de póliza es obligatorio.",Toast.LENGTH_LONG).show();return;}
            if(holder.isEmpty()){Toast.makeText(this,"No se ha podido identificar al tomador. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            if(!id.isEmpty()&&!isValidIdentity(id)){Toast.makeText(this,"DNI/NIE no válido. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            String product=parsed.optString("policyType",currentPolicyProduct(raw));
            JSONObject c=findClientForPolicy(id,holder,phone,email);
            boolean newClient=c==null;
            if(c==null)c=new JSONObject();
            c.put("holder",holder);c.put("name",holder);c.put("surname","");
            if(!id.isEmpty())c.put("identityNumber",id); if(!address.isEmpty())c.put("address",address); if(!phone.isEmpty())c.put("phone",phone); if(!email.isEmpty())c.put("email",email);
            JSONArray ps=c.optJSONArray("policies");if(ps==null)ps=new JSONArray();
            JSONObject pol=new JSONObject();
            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holder);pol.put("identityNumber",id);pol.put("address",address);pol.put("phone",phone);pol.put("email",email);pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());
            if("Decesos".equalsIgnoreCase(product)){pol.put("decesos",decesosE.getText().toString().trim());pol.put("decesosLevelada",decesosLeveladaE.getText().toString().trim());}else{pol.put("decesos","");pol.put("decesosLevelada","");}
            pol.put("insured",insured==null?new JSONArray():insured);pol.put("documentUri",documentUri==null?"":documentUri.toString());pol.put("ocrText",raw);pol.put("ocrConfidence",parsed.optInt("confidence",0));pol.put("ocrWarnings",parsed.optJSONArray("warnings"));
            String localPdf="";
            if(documentUri!=null){
                try{localPdf=copyDocumentToArchive(documentUri,"policy_"+(number.isEmpty()?"document":number),"pdf");}catch(Exception e){Toast.makeText(this,"Aviso: no se pudo archivar el PDF localmente.",Toast.LENGTH_LONG).show();}
            }
            if(!localPdf.isEmpty())pol.put("localDocumentPath",localPdf);
            boolean replaced=false;for(int i=0;i<ps.length();i++){JSONObject old=ps.optJSONObject(i);if(old!=null&&number.equals(old.optString("number",""))){
                if(localPdf.isEmpty()&&!old.optString("localDocumentPath","").isEmpty())pol.put("localDocumentPath",old.optString("localDocumentPath",""));
                ps.put(i,pol);replaced=true;break;
            }}
            if(!replaced)ps.put(pol);c.put("policies",ps);
            if(!localPdf.isEmpty())addArchivedDocument(c,localPdf,documentUri==null?"":documentUri.toString(),"Póliza OCASO · "+number,"pdf",number);
            upsertClient(c);
            Toast.makeText(this,newClient?"Nuevo cliente creado y póliza archivada.":"Póliza asociada al cliente y archivada.",Toast.LENGTH_LONG).show();
            detail(c);
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
'''
s = replace_method(s, '    private void savePolicy(String raw,JSONArray insured){', save_policy)

# Add local document action to policy detail without changing the rest of the policy model.
old_policy_detail_fragment = '        if(decesos){\n            addRead(l,"Capital de decesos",p.optString("capital",""));'
new_policy_detail_fragment = '        String localDoc=p.optString("localDocumentPath","");\n        if(!localDoc.isEmpty()){Button open=btn("📎 ABRIR DOCUMENTO ORIGINAL",true);open.setOnClickListener(v->openArchivedDocument(localDoc,"pdf"));l.addView(open,new LinearLayout.LayoutParams(-1,dp(58)));}\n        if(decesos){\n            addRead(l,"Capital de decesos",p.optString("capital",""));'
if old_policy_detail_fragment in s and 'ABRIR DOCUMENTO ORIGINAL' not in s:
    s=s.replace(old_policy_detail_fragment,new_policy_detail_fragment,1)

# Expose every archived document from the client record.
old_detail = '        body.addView(tv("PÓLIZAS",16,BLUE,true));'
new_detail = '        addDocumentsToDetail(body,x);body.addView(tv("PÓLIZAS",16,BLUE,true));'
if old_detail in s and 'addDocumentsToDetail(body,x);' not in s:
    s=s.replace(old_detail,new_detail,1)

MAIN.write_text(s,encoding='utf-8')
print('client document archive patch applied')
