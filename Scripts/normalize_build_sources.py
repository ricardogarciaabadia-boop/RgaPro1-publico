from pathlib import Path

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java")
text = MAIN.read_text(encoding="utf-8")

old = 'if((!id.isEmpty()&&id.equalsIgnoreCase(old.optString("identityNumber","")))||(!holder.isEmpty()&&normalizeSearch(holder).equals(normalizeSearch(old.optString("holder","")))){'
new = 'if (((!id.isEmpty()) && id.equalsIgnoreCase(old.optString("identityNumber", ""))) || ((!holder.isEmpty()) && normalizeSearch(holder).equals(normalizeSearch(old.optString("holder", ""))))) {'
if old in text: text = text.replace(old,new,1)
text = text.replace('.createPage();','.create();')

def end_of_method(s,start):
    brace=s.find('{',start)
    if brace<0: raise SystemExit('opening brace not found')
    depth=0
    for i in range(brace,len(s)):
        if s[i]=='{': depth+=1
        elif s[i]=='}':
            depth-=1
            if depth==0:return i+1
    raise SystemExit('unbalanced method')

def remove_all_methods(src,signatures):
    while True:
        hits=[(src.find(sig),sig) for sig in signatures];hits=[x for x in hits if x[0]>=0]
        if not hits:return src
        pos,sig=min(hits,key=lambda x:x[0]);end=end_of_method(src,pos);src=src[:pos]+src[end:]

text=remove_all_methods(text,('    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){','    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){'))
text=text.replace('    private void chooseImage()', '''    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(grantResults.length==0||grantResults[0]!=PackageManager.PERMISSION_GRANTED){if(requestCode==POLICY_CAMERA_PERMISSION)Toast.makeText(this,"Permiso de cámara denegado.",Toast.LENGTH_LONG).show();return;}
        if(requestCode==CAMERA)takePhoto(); else if(requestCode==POLICY_CAMERA_PERMISSION)startPolicyPageCamera();
    }

    private void chooseImage()''',1)

def remove_duplicate_methods(src,signature):
    first=src.find(signature)
    if first<0:return src
    end=end_of_method(src,first);prefix=src[:end];rest=src[end:]
    while True:
        pos=rest.find(signature)
        if pos<0:break
        e=end_of_method(rest,pos);rest=rest[:pos]+rest[e:]
    return prefix+rest
for sig in ('    private void addPolicyField(String label,String value,EditText field){','    private String currentPolicyProduct(String raw){'):
    text=remove_duplicate_methods(text,sig)

MAIN.write_text(text,encoding='utf-8')
exec(Path("Scripts/patch_policy_hogar_values.py").read_text(encoding="utf-8"),{"__name__":"__build_patch__"})
fixed=MAIN.read_text(encoding='utf-8')
fixed=fixed.replace('if ("Hogar".equalsIgnoreCase(product)) {','if ("Hogar".equalsIgnoreCase(p.optString("policyType", ""))) {',1)
fixed=fixed.replace('if (!"Hogar".equalsIgnoreCase(product)) addPolicyField("CAPITAL",null,capitalE);','if (!"Hogar".equalsIgnoreCase(p.optString("policyType", ""))) addPolicyField("CAPITAL",null,capitalE);',1)
needle='            JSONObject x=findClientById(id);if(x==null)x=new JSONObject();'
replacement='''            JSONObject x=findClientById(id);
            if(x==null&&!full.isEmpty()&&!birth.isEmpty()){
                JSONArray all=clientsData(); String nq=normalizeSearch(full);
                for(int ci=0;ci<all.length();ci++){JSONObject oldClient=all.optJSONObject(ci);if(oldClient==null)continue;String oldName=normalizeSearch(clientKey(oldClient));String oldBirth=oldClient.optString("birthDate","").trim();if(nq.equals(oldName)&&birth.equals(oldBirth)){x=oldClient;break;}}
            }
            if(x==null)x=new JSONObject();'''
if needle in fixed: fixed=fixed.replace(needle,replacement,1)
MAIN.write_text(fixed,encoding='utf-8')
exec(Path("Scripts/patch_client_policy_list_ui.py").read_text(encoding="utf-8"),{"__name__":"__build_patch__"})
exec(Path("Scripts/patch_policy_list_clean.py").read_text(encoding="utf-8"),{"__name__":"__build_patch__"})
exec(Path("Scripts/patch_unified_file_ingest.py").read_text(encoding="utf-8"),{"__name__":"__build_patch__"})
print("Source normalization + unified automatic file ingestion + client association complete")
