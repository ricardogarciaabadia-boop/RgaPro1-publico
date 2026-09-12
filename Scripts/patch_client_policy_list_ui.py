from pathlib import Path

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=MAIN.read_text(encoding='utf-8')

def replace_method(src,sig,new):
    a=src.find(sig)
    if a<0: raise SystemExit('method not found: '+sig)
    b=src.find('{',a);d=0
    for i in range(b,len(src)):
        if src[i]=='{': d+=1
        elif src[i]=='}':
            d-=1
            if d==0:return src[:a]+new+src[i+1:]
    raise SystemExit('unbalanced method: '+sig)

detail=r'''    private void detail(JSONObject x){
        shell("Ficha de cliente",clientKey(x));
        body.addView(tv(clientKey(x),26,TEXT,true));

        body.addView(tv("DATOS DEL TOMADOR",17,BLUE,true));
        addRead(body,"DNI/NIE",x.optString("identityNumber",x.optString("holderDni","")));
        addRead(body,"Fecha de nacimiento",x.optString("birthDate",""));
        addRead(body,"Dirección",x.optString("address",""));
        addRead(body,"Teléfono",x.optString("phone",""));

        boolean hasDni=false;
        JSONArray docs=x.optJSONArray("documents");
        if(docs!=null) for(int i=0;i<docs.length();i++){
            JSONObject d=docs.optJSONObject(i); if(d==null) continue;
            String type=d.optString("type","").toLowerCase(Locale.ROOT), title=d.optString("title","").toLowerCase(Locale.ROOT);
            if(type.contains("image") && (title.contains("dni")||title.contains("nie"))){
                String path=d.optString("localPath","");
                if(!path.isEmpty()){
                    Button b=btn("📄 VER DNI ORIGINAL",false);
                    b.setOnClickListener(v->openArchivedDocument(path,"image"));
                    body.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));hasDni=true;break;
                }
            }
        }
        if(!hasDni){
            JSONArray photos=x.optJSONArray("documentPhotos");
            if(photos!=null) for(int i=0;i<photos.length();i++){
                Object it=photos.opt(i);String path=it instanceof JSONObject?((JSONObject)it).optString("path",""):String.valueOf(it);
                String low=path.toLowerCase(Locale.ROOT);
                if(!path.isEmpty()&&(low.contains("dni")||low.contains("nie"))){
                    Button b=btn("📄 VER DNI ORIGINAL",false);b.setOnClickListener(v->openDocument(path));
                    body.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));hasDni=true;break;
                }
            }
        }

        body.addView(tv("PÓLIZAS",17,BLUE,true));
        JSONArray ps=x.optJSONArray("policies");
        if(ps==null||ps.length()==0){
            body.addView(tv("Este cliente no tiene pólizas archivadas.",14,MUTED,false));
        }else{
            for(int i=0;i<ps.length();i++){
                JSONObject p=ps.optJSONObject(i);if(p==null)continue;
                String type=p.optString("policyType","Póliza").trim();
                String icon=policyIcon(type);
                String number=p.optString("number","Sin número").trim();
                Button b=btn(icon+"  "+type+"  ·  "+number,false);
                b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                b.setOnClickListener(v->policyDetail(p));
                body.addView(b,new LinearLayout.LayoutParams(-1,dp(64)));
            }
        }

        Button edit=btn("✏️ EDITAR DATOS DEL TOMADOR",false);
        edit.setOnClickListener(v->editClient(x));
        body.addView(edit,new LinearLayout.LayoutParams(-1,dp(58)));
    }

    private String policyIcon(String type){
        String t=type==null?"":type.toLowerCase(Locale.ROOT);
        if(t.contains("hogar"))return "🏠";
        if(t.contains("deceso"))return "⚰️";
        if(t.contains("vida"))return "❤️";
        if(t.contains("accidente"))return "🛡️";
        if(t.contains("responsabilidad"))return "⚖️";
        if(t.contains("ahorro"))return "💶";
        if(t.contains("comunidad"))return "🏢";
        if(t.contains("salud"))return "🩺";
        return "📄";
    }
'''
s=replace_method(s,'    private void detail(JSONObject x){',detail)
MAIN.write_text(s,encoding='utf-8')
print('client ficha redesigned: DNI access + policy type buttons')
