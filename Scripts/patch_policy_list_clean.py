from pathlib import Path

P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')

def replace_method(src, sig, replacement):
    start=src.find(sig)
    if start<0: raise SystemExit('method not found: '+sig)
    brace=src.find('{',start)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return src[:start]+replacement+src[i+1:]
    raise SystemExit('unbalanced method: '+sig)

method=r'''    private void policies(){
        shell("Pólizas","Listado de pólizas Ocaso");
        body.addView(tv("LISTADO DE PÓLIZAS",24,TEXT,true));
        JSONArray a=clientsData();
        boolean any=false;
        for(int i=0;i<a.length();i++){
            JSONObject c=a.optJSONObject(i); if(c==null)continue;
            JSONArray ps=c.optJSONArray("policies"); if(ps==null)continue;
            for(int j=0;j<ps.length();j++){
                JSONObject p=ps.optJSONObject(j); if(p==null)continue;
                any=true;
                String product=p.optString("policyType",p.optString("type","OCASO"));
                String number=p.optString("number","—");
                String holder=clientKey(c);
                String icon="Hogar".equalsIgnoreCase(product)?"🏠":("Decesos".equalsIgnoreCase(product)?"⚰️":("Vida".equalsIgnoreCase(product)?"❤️":"📄"));
                Button b=btn(icon+" "+product+" · "+number+System.lineSeparator()+holder,false);
                b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
                b.setOnClickListener(v->policyDetail(p));
                body.addView(b,new LinearLayout.LayoutParams(-1,dp(76)));
            }
        }
        if(!any)body.addView(tv("No hay pólizas guardadas.",15,MUTED,false));
        Button add=btn("＋ NUEVA PÓLIZA",true);
        add.setOnClickListener(v->showNewPolicyOptions());
        body.addView(add,new LinearLayout.LayoutParams(-1,dp(60)));
    }

    private void showNewPolicyOptions(){
        LinearLayout l=col();
        Button pdf=btn("📄 SUBIR PÓLIZA PDF",false);
        Button camera=btn("📷 FOTOGRAFIAR PÓLIZA · VARIAS PÁGINAS",false);
        l.addView(pdf,new LinearLayout.LayoutParams(-1,dp(60)));
        l.addView(camera,new LinearLayout.LayoutParams(-1,dp(64)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Nueva póliza").setView(l).setNegativeButton("Cancelar",null).create();
        pdf.setOnClickListener(v->{d.dismiss();choosePdf();});
        camera.setOnClickListener(v->{d.dismiss();policyPageUris.clear();policyPageBitmaps.clear();policyCameraFlow=true;startPolicyPageCamera();});
        d.show();
    }
'''
s=replace_method(s,'    private void policies(){',method)
P.write_text(s,encoding='utf-8')
print('clean policy list applied')
