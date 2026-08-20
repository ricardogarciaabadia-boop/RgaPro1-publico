from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')

helpers = r'''
    private boolean isPolicyDocumentV4(String raw){
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT);
        int score=0;
        if(u.matches("(?s).*\\b(P[ÓO]LIZA|POLIZA|N[ÚU]MERO\\s+DE\\s+P[ÓO]LIZA|Nº\\s*P[ÓO]LIZA|TOMADOR|CONTRATANTE|ASEGURADO|ASEGURADOS|PRIMA|COBERTURA|CAPITAL\\s+ASEGURADO|CONDICIONES\\s+PARTICULARES|FECHA\\s+EFECTO|FECHA\\s+VENCIMIENTO)\\b.*"))score+=3;
        if(u.matches("(?s).*\\b(VIDA|DECESO|DECESOS|SEPELIO|HOGAR|SALUD|AUTO|AUTOM[ÓO]VIL|ACCIDENTE|AHORRO|COMUNIDAD|EMPRESA|RESPONSABILIDAD\\s+CIVIL)\\b.*"))score+=2;
        boolean id=Pattern.compile("(?<![0-9])[0-9]{8}[A-Z](?![A-Z0-9])").matcher(u).find()||Pattern.compile("(?<![A-Z0-9])[XYZ][0-9]{7}[A-Z](?![A-Z0-9])").matcher(u).find();
        if(id && score<3) return false;
        return score>=3;
    }
    private String policyTypeV4(String raw,String selected){
        String t=selected==null?"":selected.trim();
        if(!t.isEmpty()&&!t.equalsIgnoreCase("Cliente / DNI")&&!t.equalsIgnoreCase("Cliente / NIE")&&!t.equalsIgnoreCase("Otros"))return t.equalsIgnoreCase("Decesos")?"Deceso":t;
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT);
        if(u.matches("(?s).*\\b(DECESO|DECESOS|SEPELIO|FUNERAL)\\b.*"))return "Deceso";
        if(u.matches("(?s).*\\b(VIDA|FALLECIMIENTO|CAPITAL\\s+ASEGURADO\\s+VIDA)\\b.*"))return "Vida";
        if(u.matches("(?s).*\\b(HOGAR|VIVIENDA|CONTINENTE|CONTENIDO)\\b.*"))return "Hogar";
        if(u.matches("(?s).*\\b(SALUD|ASISTENCIA\\s+SANITARIA|CUADRO\\s+MEDICO|CUADRO\\s+MÉDICO)\\b.*"))return "Salud";
        if(u.matches("(?s).*\\b(AUTO|AUTOM[ÓO]VIL|VEH[ÍI]CULO)\\b.*"))return "Auto";
        if(u.matches("(?s).*\\b(ACCIDENTE|ACCIDENTES|INVALIDEZ\\s+ACCIDENTAL)\\b.*"))return "Accidente";
        if(u.matches("(?s).*\\b(PIAS|AHORRO|APORTE\\s+EXTRAORDINARIO|FLEXIBLE)\\b.*"))return "Ahorro";
        if(u.matches("(?s).*\\b(COMUNIDAD|COMUNIDADES|FINCA\\s+COMUNIDAD)\\b.*"))return "Comunidades";
        if(u.matches("(?s).*\\b(EMPRESA|PYME|NEGOCIO|COMERCIO)\\b.*"))return "Empresa";
        if(u.matches("(?s).*\\b(RESPONSABILIDAD\\s+CIVIL|RC\\s+GENERAL)\\b.*"))return "Responsabilidad civil";
        return "Otros";
    }
    private String labelV4(String raw,String... labels){
        String[] lines=(raw==null?"":raw.replace('\r','\n')).split("\\n");
        for(int i=0;i<lines.length;i++){String line=lines[i].trim();String up=line.toUpperCase(Locale.ROOT);for(String label:labels){String lab=label.toUpperCase(Locale.ROOT);int p=up.indexOf(lab);if(p>=0){String v=line.substring(Math.min(line.length(),p+lab.length())).replaceFirst("^[\\s:.-]+","").trim();if(!v.isEmpty())return v;if(i+1<lines.length)return lines[i+1].trim();}}}
        return "";
    }
    private String idV4(String raw){Matcher m=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b",Pattern.CASE_INSENSITIVE).matcher(raw==null?"":raw);return m.find()?m.group().toUpperCase(Locale.ROOT):"";}
    private String phoneV4(String raw){Matcher m=Pattern.compile("(?<!\\d)(?:\\+34[\\s.-]?)?[6789]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}(?!\\d)").matcher(raw==null?"":raw);return m.find()?m.group().replaceAll("[\\s.-]",""):"";}
    private String emailV4(String raw){Matcher m=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",Pattern.CASE_INSENSITIVE).matcher(raw==null?"":raw);return m.find()?m.group():"";}
    private String policyHolderV4(String raw){String v=labelV4(raw,"TOMADOR","TOMADORA","CONTRATANTE","TITULAR");if(!v.isEmpty())return v;String n=labelV4(raw,"NOMBRE");String a=labelV4(raw,"APELLIDOS");return (n+" "+a).trim();}
    private JSONArray insuredsV4(String raw,String holder){
        JSONArray out=new JSONArray();String text=raw==null?"":raw.replace('\r','\n');
        Matcher sec=Pattern.compile("(?is)(?:PERSONAS\\s+ASEGURADAS|ASEGURADOS?|ASEGURADAS?)\\s*[:\\-]?\\s*(.*?)(?=\\n\\s*(?:TOMADOR(?:A)?|CONTRATANTE|BENEFICIARIO(?:S)?|PRIMA|RECIBO|FORMA\\s+DE\\s+PAGO|DATOS\\s+DE\\s+LA\\s+P[ÓO]LIZA|OBSERVACIONES|COBERTURAS?)\\b|$)").matcher(text);
        String block=sec.find()?sec.group(1):"";if(block.trim().isEmpty())block=labelV4(text,"ASEGURADOS","ASEGURADAS");
        for(String line:block.split("\\n|;")){String x=line.trim();if(x.length()<3||x.length()>220)continue;String u=x.toUpperCase(Locale.ROOT);if(u.matches(".*\\b(TOMADOR|CONTRATANTE|ASEGURADORA|P[ÓO]LIZA|PRIMA|RECIBO|BENEFICIARIO|COBERTURA|CONDICIONES|DOMICILIO\\s+DE\\s+LA\\s+ASEGURADORA)\\b.*"))continue;JSONObject p=new JSONObject();try{Matcher id=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b",Pattern.CASE_INSENSITIVE).matcher(x);if(id.find()){p.put("identityNumber",id.group().toUpperCase(Locale.ROOT));x=x.replace(id.group(),"");}Matcher ph=Pattern.compile("(?<!\\d)(?:\\+34[\\s.-]?)?[6789]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}(?!\\d)").matcher(x);if(ph.find()){p.put("phone",ph.group().replaceAll("[\\s.-]",""));x=x.substring(0,ph.start())+" "+x.substring(ph.end());}Matcher dt=Pattern.compile("\\b\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4}\\b").matcher(x);if(dt.find()){p.put("birthDate",dt.group());x=x.substring(0,dt.start())+" "+x.substring(dt.end());}x=x.replaceAll("(?i)^(?:nombre|asegurado|asegurada)\\s*[:.-]?\\s*","").replaceAll("\\s+"," ").trim();if(x.length()<2)continue;p.put("name",x);p.put("role","ASEGURADO");boolean dup=false;String key=personKey(p);for(int i=0;i<out.length();i++){JSONObject old=out.optJSONObject(i);if(old!=null&&!key.isEmpty()&&key.equals(personKey(old))){dup=true;break;}}if(!dup)out.put(p);}catch(Exception ignored){}}
        if(out.length()==0&&!holder.trim().isEmpty())try{JSONObject p=new JSONObject();p.put("name",holder.trim());p.put("role","ASEGURADO");out.put(p);}catch(Exception ignored){}
        return out;
    }
    private String insuredTextV4(JSONArray a){StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;if(b.length()>0)b.append('\n');b.append(p.optString("name",""));if(!p.optString("identityNumber","").isEmpty())b.append(" | ").append(p.optString("identityNumber"));if(!p.optString("birthDate","").isEmpty())b.append(" | ").append(p.optString("birthDate"));if(!p.optString("phone","").isEmpty())b.append(" | ").append(p.optString("phone"));}return b.toString();}
    private void linkPolicyPeopleV4(JSONObject policy){try{JSONArray people;try{people=new JSONArray(prefs.getString("people","[]"));}catch(Exception e){people=new JSONArray();}JSONArray insureds=policy.optJSONArray("insureds");if(insureds==null)insureds=new JSONArray();ArrayList<JSONObject> persons=new ArrayList<>();JSONObject h=new JSONObject();h.put("name",policy.optString("holder",""));h.put("surname",policy.optString("surname",""));h.put("identityNumber",policy.optString("identityNumber",policy.optString("holderDni","")));h.put("phone",policy.optString("phone",""));h.put("email",policy.optString("email",""));h.put("role","TOMADOR");persons.add(h);for(int i=0;i<insureds.length();i++){JSONObject x=insureds.optJSONObject(i);if(x!=null){JSONObject q=new JSONObject(x.toString());q.put("role","ASEGURADO");persons.add(q);}}for(JSONObject p:persons){String key=personKey(p);if(key.isEmpty())continue;int idx=-1;for(int i=0;i<people.length();i++){JSONObject old=people.optJSONObject(i);if(old!=null&&key.equals(personKey(old))){idx=i;break;}}JSONObject target=idx>=0?people.optJSONObject(idx):new JSONObject();for(String k:new String[]{"name","surname","identityNumber","phone","email"})if(!p.optString(k,"").isEmpty())target.put(k,p.optString(k));JSONArray ids=target.optJSONArray("policyIds");if(ids==null)ids=new JSONArray();String pid=policy.optString("policyId","");boolean has=false;for(int j=0;j<ids.length();j++)if(pid.equals(ids.optString(j)))has=true;if(!has&&!pid.isEmpty())ids.put(pid);target.put("policyIds",ids);JSONArray roles=target.optJSONArray("roles");if(roles==null)roles=new JSONArray();String role=p.optString("role","ASEGURADO");boolean hr=false;for(int j=0;j<roles.length();j++)if(role.equalsIgnoreCase(roles.optString(j)))hr=true;if(!hr)roles.put(role);target.put("roles",roles);if(idx>=0)people.put(idx,target);else people.put(target);}prefs.edit().putString("people",people.toString()).apply();}catch(Exception ignored){}}

'''

anchor='    private void showOcrResult(String raw){'
if 'private boolean isPolicyDocumentV4(' not in s:
    s=s.replace(anchor,helpers+anchor,1)

# Replace showOcrResult with a document-aware editor. It never exposes DNI-only fields for policies.
def replace_method(src, signature, replacement):
    start=src.find(signature)
    if start<0: raise SystemExit('method not found: '+signature)
    brace=src.find('{',start);depth=0
    for i in range(brace,len(src)):
        if src[i]=='{':depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return src[:start]+replacement+src[i+1:]
    raise SystemExit('unbalanced method: '+signature)

show=r'''    private void showOcrResult(String raw){
        final boolean policyDoc=isPolicyDocumentV4(raw);
        if(policyDoc){
            LinearLayout box=col();box.setPadding(dp(10),dp(4),dp(10),dp(4));
            String detected=policyTypeV4(raw,"");
            box.addView(tv("✓ PÓLIZA IDENTIFICADA · "+detected,15,Color.rgb(25,110,70),true));
            box.addView(tv("No se tratará como DNI aunque aparezca un DNI/NIE dentro del documento.",13,MUTED,false));
            EditText product=edit("Tipo de producto: Vida, Deceso, Hogar, Salud…"),holder=edit("Titular / tomador"),surname=edit("Apellidos del titular"),name=edit("Nombre del titular"),dni=edit("DNI / NIE del titular"),cif=edit("CIF del titular (si empresa)"),address=edit("Domicilio del titular"),phone=edit("Teléfono del titular"),email=edit("Email del titular"),number=edit("Nº de póliza"),insured=edit("Asegurados · uno por línea: Nombre | DNI/NIE | Fecha nacimiento | Teléfono");
            product.setText(detected);holder.setText(policyHolderV4(raw));surname.setText(labelV4(raw,"APELLIDOS"));name.setText(labelV4(raw,"NOMBRE"));dni.setText(idV4(raw));cif.setText(labelV4(raw,"CIF","NIF"));address.setText(labelV4(raw,"DOMICILIO","DIRECCIÓN","DIRECCION"));phone.setText(phoneV4(raw));email.setText(emailV4(raw));number.setText(labelV4(raw,"Nº PÓLIZA","Nº POLIZA","NUMERO DE POLIZA","NÚMERO DE PÓLIZA"));JSONArray ins=insuredsV4(raw,holder.getText().toString());insured.setSingleLine(false);insured.setMinLines(5);insured.setText(insuredTextV4(ins));
            for(EditText e:new EditText[]{product,holder,surname,name,dni,cif,address,phone,email,number})box.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));box.addView(tv("Asegurados distintos del titular",13,MUTED,true));box.addView(insured,new LinearLayout.LayoutParams(-1,dp(170)));
            ScrollView sc=new ScrollView(this);sc.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Datos de la póliza").setView(sc).setNegativeButton("Descartar",null).setPositiveButton("Guardar póliza",null).create();dialog.setOnShowListener(q->dialog.getButton(-1).setOnClickListener(v->{savePolicyV4(product,holder,surname,name,dni,cif,address,phone,email,number,insured,raw);dialog.dismiss();}));dialog.show();return;
        }
        OcrData d=parseOcr(raw);LinearLayout box=col();box.setPadding(dp(10),dp(4),dp(10),dp(4));String aviso=d.confidence<70?"⚠ OCR con datos incompletos. Revisa los campos antes de guardar.":"✓ DNI/NIE identificado. Revisa los datos antes de guardar.";box.addView(tv(aviso+"  Confianza estimada: "+d.confidence+"%",14,d.confidence<70?Color.rgb(170,95,0):Color.rgb(25,110,70),true));box.addView(tv("Documento de identidad: solo se guardarán los campos propios del DNI/NIE.",13,MUTED,false));EditText holder=edit("Titular / nombre completo"),surname=edit("Apellidos"),name=edit("Nombre"),dni=edit("DNI / NIE"),address=edit("Domicilio"),phone=edit("Teléfono"),email=edit("Email");holder.setText(d.holder);surname.setText(d.surname);name.setText(d.name);dni.setText(d.dni);address.setText(d.address);phone.setText(d.phone);email.setText(d.email);for(EditText e:new EditText[]{holder,surname,name,dni,address,phone,email})box.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));ScrollView sc=new ScrollView(this);sc.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Datos del DNI/NIE").setView(sc).setNegativeButton("Descartar",null).setPositiveButton("Guardar documento",null).create();dialog.setOnShowListener(q->dialog.getButton(-1).setOnClickListener(v->{saveDniV4(holder,surname,name,dni,address,phone,email,raw);dialog.dismiss();}));dialog.show();
    }
'''
s=replace_method(s,'    private void showOcrResult(String raw){',show)

save_policy=r'''    private void savePolicyV4(EditText product,EditText holder,EditText surname,EditText name,EditText dni,EditText cif,EditText address,EditText phone,EditText email,EditText number,EditText insured,String raw){try{JSONArray a=data();JSONObject p=new JSONObject();String pid="P-"+System.currentTimeMillis();String pn=number.getText().toString().trim();for(int i=0;i<a.length();i++){JSONObject old=a.optJSONObject(i);if(old!=null&&!pn.isEmpty()&&pn.equalsIgnoreCase(old.optString("number",""))){pid=old.optString("policyId",pid);break;}}p.put("policyId",pid);p.put("documentKind","POLIZA");p.put("type",policyTypeV4(raw,product.getText().toString()));p.put("product",product.getText().toString().trim());p.put("holder",holder.getText().toString().trim());p.put("surname",surname.getText().toString().trim());p.put("name",name.getText().toString().trim());p.put("holderDni",dni.getText().toString().trim().toUpperCase(Locale.ROOT));p.put("identityNumber",dni.getText().toString().trim().toUpperCase(Locale.ROOT));p.put("identityType",dni.getText().toString().trim().isEmpty()?"":(dni.getText().toString().trim().matches("[XYZ][0-9]{7}[A-Z]")?"NIE":"DNI"));p.put("cif",cif.getText().toString().trim().toUpperCase(Locale.ROOT));p.put("address",address.getText().toString().trim());p.put("phone",phone.getText().toString().trim());p.put("email",email.getText().toString().trim());p.put("number",pn);p.put("ocrText",raw);p.put("updatedAt",System.currentTimeMillis());JSONArray ins=new JSONArray();for(String line:insured.getText().toString().split("\\r?\\n")){String x=line.trim();if(x.isEmpty())continue;String[] q=x.split("\\s*\\|\\s*");JSONObject z=new JSONObject();z.put("name",q[0].trim());if(q.length>1)z.put("identityNumber",q[1].trim().toUpperCase(Locale.ROOT));if(q.length>2)z.put("birthDate",q[2].trim());if(q.length>3)z.put("phone",q[3].trim());z.put("role","ASEGURADO");ins.put(z);}if(ins.length()==0)ins=insuredsV4(raw,holder.getText().toString());p.put("insureds",ins);JSONArray docs=new JSONArray();for(int i=0;i<sessionPaths.size();i++){JSONObject d=new JSONObject();d.put("path",sessionPaths.get(i));d.put("side","POLIZA");d.put("addedAt",System.currentTimeMillis());docs.put(d);}p.put("documentPhotos",docs);boolean replaced=false;for(int i=0;i<a.length();i++){JSONObject old=a.optJSONObject(i);if(old!=null&&pid.equals(old.optString("policyId",""))){a.put(i,p);replaced=true;break;}}if(!replaced)a.put(p);save(a);linkPolicyPeopleV4(p);Toast.makeText(this,"Póliza guardada: "+p.optString("type")+" · "+ins.length()+" asegurado(s) vinculados.",Toast.LENGTH_LONG).show();home();}catch(Exception e){Toast.makeText(this,"No se pudo guardar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
'''

save_dni=r'''    private void saveDniV4(EditText holder,EditText surname,EditText name,EditText dni,EditText address,EditText phone,EditText email,String raw){try{JSONArray a=data();JSONObject p=new JSONObject();p.put("policyId","D-"+System.currentTimeMillis());p.put("documentKind","DNI");p.put("type","Cliente");p.put("holder",holder.getText().toString().trim());p.put("surname",surname.getText().toString().trim());p.put("name",name.getText().toString().trim());p.put("holderDni",dni.getText().toString().trim().toUpperCase(Locale.ROOT));p.put("identityNumber",dni.getText().toString().trim().toUpperCase(Locale.ROOT));p.put("identityType",dni.getText().toString().trim().matches("[XYZ][0-9]{7}[A-Z]")?"NIE":"DNI");p.put("address",address.getText().toString().trim());p.put("phone",phone.getText().toString().trim());p.put("email",email.getText().toString().trim());p.put("ocrText",raw);p.put("updatedAt",System.currentTimeMillis());JSONArray docs=new JSONArray();for(String path:sessionPaths){JSONObject d=new JSONObject();d.put("path",path);d.put("side",dniMode?"DNI":"DOCUMENTO");d.put("addedAt",System.currentTimeMillis());docs.put(d);}p.put("documentPhotos",docs);a.put(p);save(a);Toast.makeText(this,"DNI/NIE guardado sin campos de póliza.",Toast.LENGTH_LONG).show();home();}catch(Exception e){Toast.makeText(this,"No se pudo guardar el DNI/NIE: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
'''

# Insert the new save methods immediately before saveClient, leaving legacy saveClient unused.
if 'private void savePolicyV4(' not in s:
    s=s.replace('    private void saveClient(',save_policy+'\n'+save_dni+'\n    private void saveClient(',1)

MAIN.write_text(s,encoding='utf-8')
print('v4 document-aware policy/DNI pipeline applied')
