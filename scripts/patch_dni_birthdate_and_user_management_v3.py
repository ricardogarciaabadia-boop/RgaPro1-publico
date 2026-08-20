from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivity.java")
s = MAIN.read_text(encoding="utf-8")

# DNI visibility block must not reference OCR-local EditTexts from another scope.
s = re.sub(r'(?m)^\s*if\(dniMode\)\{[^\n]*\}\s*$', '', s)
s = re.sub(r'if\(dniMode\)\{[^{}\n]*\}', '', s)

# Java escape hygiene.
s = s.replace("replace('\\\\r','\\\\n')", "replace('\\r','\\n')")
s = s.replace('replace("\\\\r","\\\\n")', 'replace("\\r","\\n")')
s = s.replace('}else else askMorePages();', '}else askMorePages();')
s = s.replace('} else else askMorePages();', '} else askMorePages();')

# Always add the navigation entry if missing.
if 'sideButton("👥  Usuarios de la aplicación")' not in s:
    line = '        Button users=sideButton("👥  Usuarios de la aplicación"); users.setOnClickListener(v->users()); side.addView(users,new LinearLayout.LayoutParams(-1,dp(68)));'
    logout = re.search(r'(?m)^\s*Button logout=sideButton\("Salir"\);.*$', s)
    if logout:
        s = s[:logout.start()] + line + '\n' + s[logout.start():]

# Persistent application users.
if 'private JSONArray appUsers()' not in s:
    helper = '''    private JSONArray appUsers(){
        try{String raw=prefs.getString("appUsers","");if(!raw.isEmpty())return new JSONArray(raw);JSONArray a=new JSONArray();String u=prefs.getString("user","");String p=prefs.getString("pin","");if(!u.isEmpty())a.put(new JSONObject().put("name",u).put("pin",p).put("active",true).put("role","ADMIN"));prefs.edit().putString("appUsers",a.toString()).apply();return a;}catch(Exception e){return new JSONArray();}
    }
    private void saveAppUsers(JSONArray a){prefs.edit().putString("appUsers",a.toString()).apply();}
'''
    marker = '    private void security(){'
    if marker in s: s=s.replace(marker,helper+marker,1)

# Guarantee the methods exist. Use an array holder because refresh is self-referential.
if 'private void users()' not in s:
    users = '''    private void users(){
        page("Usuarios de la aplicación","Gestiona el acceso local a RgaPro");
        Button add=action("＋ Añadir usuario",true);
        content.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout list=col();
        content.addView(list);
        final Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            list.removeAllViews();
            JSONArray a=appUsers();
            for(int i=0;i<a.length();i++){
                final int idx=i;
                JSONObject q=a.optJSONObject(i);
                if(q==null)continue;
                String n=q.optString("name","Sin nombre");
                boolean active=q.optBoolean("active",true);
                Button b=action((active?"🟢 ":"⚪ ")+n+(active?" · Activo":" · Desactivado"),false);
                b.setOnClickListener(v->editAppUser(idx,refresh[0]));
                list.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));
            }
        };
        add.setOnClickListener(v->addAppUser(refresh[0]));
        refresh[0].run();
    }
    private void addAppUser(Runnable refresh){
        LinearLayout l=col();EditText n=edit("Nombre de usuario"),p=edit("Clave de 6 dígitos");
        p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(n);l.addView(p);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Añadir usuario").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String name=n.getText().toString().trim(),pin=p.getText().toString();if(name.isEmpty()||!pin.matches("\\\\d{6}")){p.setError("Clave de 6 dígitos obligatoria");return;}try{JSONArray a=appUsers();a.put(new JSONObject().put("name",name).put("pin",pin).put("active",true).put("role","USUARIO"));saveAppUsers(a);d.dismiss();refresh.run();}catch(Exception e){p.setError("No se pudo guardar");}}));d.show();
    }
    private void editAppUser(int idx,Runnable refresh){
        JSONArray a=appUsers();JSONObject q=a.optJSONObject(idx);if(q==null)return;LinearLayout l=col();EditText n=edit("Nombre");n.setText(q.optString("name",""));EditText p=edit("Nueva clave (opcional)");
        p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(n);l.addView(p);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar usuario").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{q.put("name",n.getText().toString().trim());if(!p.getText().toString().isEmpty())q.put("pin",p.getText().toString());a.put(idx,q);saveAppUsers(a);d.dismiss();refresh.run();}catch(Exception e){n.setError("No se pudo guardar");}}));d.show();
    }

'''
    marker='    private void security(){'
    if marker in s:s=s.replace(marker,users+marker,1)

# Compatibility overload for older OCR call sites.
if 'private void saveClient(EditText a1,EditText a2,EditText a3,EditText a4,EditText a5,EditText a6,EditText a7,EditText a8,EditText a9,EditText a10,EditText a11,EditText a12,EditText a13,EditText a14,EditText a15,EditText a16,EditText a17,String s1,String s2,String s3)' not in s:
    marker=re.search(r'(?m)^\s*private void saveClient\s*\(',s)
    if marker:
        overload='''    private void saveClient(EditText a1,EditText a2,EditText a3,EditText a4,EditText a5,EditText a6,EditText a7,EditText a8,EditText a9,EditText a10,EditText a11,EditText a12,EditText a13,EditText a14,EditText a15,EditText a16,EditText a17,String s1,String s2,String s3){saveClient(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,s1,s2,s3,"","","");}

'''
        s=s[:marker.start()]+overload+s[marker.start():]

MAIN.write_text(s,encoding="utf-8")
print("Robust DNI/user-management patch applied")
