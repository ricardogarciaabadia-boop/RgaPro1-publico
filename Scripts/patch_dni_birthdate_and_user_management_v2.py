from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivity.java")
s = MAIN.read_text(encoding="utf-8")

# DNI: conservar siempre la fecha de nacimiento.
s = s.replace(
    'if(dniMode){cif.setVisibility(View.GONE);birth.setVisibility(View.GONE);nationality.setVisibility(View.GONE);sex.setVisibility(View.GONE);birthPlace.setVisibility(View.GONE);parents.setVisibility(View.GONE);support.setVisibility(View.GONE);issue.setVisibility(View.GONE);validity.setVisibility(View.GONE);}',
    'if(dniMode){cif.setVisibility(View.GONE);nationality.setVisibility(View.GONE);sex.setVisibility(View.GONE);birthPlace.setVisibility(View.GONE);parents.setVisibility(View.GONE);support.setVisibility(View.GONE);issue.setVisibility(View.GONE);validity.setVisibility(View.GONE);birth.setVisibility(View.VISIBLE);birth.setHint("Fecha de nacimiento (IMPORTANTE)");}'
)
s = s.replace(
    'String[] remove={"birthDate","nationality","sex","birthPlace","parents","supportNumber","issueDate","validityDate","expiry","cif","products","insureds","product","ahorroModalidad"};',
    'String[] remove={"nationality","sex","birthPlace","parents","supportNumber","issueDate","validityDate","expiry","cif","products","insureds","product","ahorroModalidad"};'
)

# Menú de usuarios: usar Salir como ancla estable; Seguridad como respaldo.
if 'Button users=sideButton("👥  Usuarios de la aplicación")' not in s:
    line='Button users=sideButton("👥  Usuarios de la aplicación"); users.setOnClickListener(v->users()); side.addView(users,new LinearLayout.LayoutParams(-1,dp(68)));'
    m=re.search(r'(?m)^(\s*Button\s+logout\s*=\s*sideButton\("Salir"\);.*?side\.addView\(logout,\s*new\s+LinearLayout\.LayoutParams\(-1,\s*dp\(\s*56\s*\)\)\s*;)',s)
    if not m:
        m=re.search(r'(?m)^(\s*Button\s+security\s*=\s*sideButton\([^;]+\);.*?side\.addView\(security,\s*new\s+LinearLayout\.LayoutParams\(-1,\s*dp\(\s*\d+\s*\)\)\s*;)',s)
    if m:
        indent=re.match(r'^\s*',m.group(1)).group(0)
        s=s[:m.start(1)]+indent+line+'\n'+m.group(1)+s[m.end(1):]
    else:
        raise SystemExit('home user menu anchor not found')

# Crear/normalizar almacén de usuarios.
if 'private JSONArray appUsers()' not in s:
    helper='''    private JSONArray appUsers(){\n        try{String raw=prefs.getString("appUsers","");if(!raw.isEmpty())return new JSONArray(raw);JSONArray a=new JSONArray();String u=prefs.getString("user","");String p=prefs.getString("pin","");if(!u.isEmpty())a.put(new JSONObject().put("name",u).put("pin",p).put("active",true).put("role","ADMIN"));prefs.edit().putString("appUsers",a.toString()).apply();return a;}catch(Exception e){return new JSONArray();}\n    }\n    private void saveAppUsers(JSONArray a){prefs.edit().putString("appUsers",a.toString()).apply();}\n'''
    marker='    private void security(){'
    if marker in s:s=s.replace(marker,helper+marker,1)

# Sustituir login usando el inicio y fin del método, sin depender de su formato/indentación.
start=s.find('private void login(){')
if start>=0 and 'private void biometricLogin(){' in s[start:]:
    end=s.find('private void biometricLogin(){',start)
    login='''private void login(){LinearLayout l=col();EditText u=edit("Usuario"),p=edit("Clave de 6 dígitos");p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(u,new LinearLayout.LayoutParams(-1,dp(52)));l.addView(p,new LinearLayout.LayoutParams(-1,dp(52)));AlertDialog d=new AlertDialog.Builder(this).setTitle("Entrar en RgaPro").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Entrar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String un=u.getText().toString().trim(),pw=p.getText().toString();JSONArray a=appUsers();for(int i=0;i<a.length();i++){JSONObject q=a.optJSONObject(i);if(q!=null&&q.optBoolean("active",true)&&un.equalsIgnoreCase(q.optString("name",""))&&pw.equals(q.optString("pin",""))){currentUser=q.optString("name",un);prefs.edit().putString("user",currentUser).putString("pin",pw).apply();d.dismiss();home();return;}}p.setError("Usuario o clave incorrectos, o usuario desactivado");}));d.show();}\n    '''
    s=s[:start]+login+s[end:]

# Gestión de usuarios: insertar antes de security().
if 'private void users(){' not in s:
    marker='    private void security(){'
    users='''    private void users(){page("Usuarios de la aplicación","Gestiona el acceso local a RgaPro");Button add=action("＋ Añadir usuario",true);content.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));LinearLayout list=col();content.addView(list);Runnable refresh=()->{list.removeAllViews();JSONArray a=appUsers();for(int i=0;i<a.length();i++){final int idx=i;JSONObject q=a.optJSONObject(i);if(q==null)continue;String n=q.optString("name","Sin nombre");boolean active=q.optBoolean("active",true);Button b=action((active?"🟢 ":"⚪ ")+n+(active?" · Activo":" · Desactivado"),false);b.setOnClickListener(v->editAppUser(idx,refresh));list.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));}};add.setOnClickListener(v->addAppUser(refresh));refresh.run();}\n    private void addAppUser(Runnable refresh){LinearLayout l=col();EditText n=edit("Nombre de usuario"),p=edit("Clave de 6 dígitos");p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(n);l.addView(p);AlertDialog d=new AlertDialog.Builder(this).setTitle("Añadir usuario").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String name=n.getText().toString().trim(),pin=p.getText().toString();if(name.isEmpty()||!pin.matches("\\\\d{6}")){p.setError("Clave de 6 dígitos obligatoria");return;}try{JSONArray a=appUsers();a.put(new JSONObject().put("name",name).put("pin",pin).put("active",true).put("role","USUARIO"));saveAppUsers(a);d.dismiss();refresh.run();}catch(Exception e){p.setError("No se pudo guardar");}}));d.show();}\n    private void editAppUser(int idx,Runnable refresh){JSONArray a=appUsers();JSONObject q=a.optJSONObject(idx);if(q==null)return;LinearLayout l=col();EditText n=edit("Nombre");n.setText(q.optString("name",""));EditText p=edit("Nueva clave (opcional)");p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(n);l.addView(p);AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar usuario").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{q.put("name",n.getText().toString().trim());if(!p.getText().toString().isEmpty())q.put("pin",p.getText().toString());a.put(idx,q);saveAppUsers(a);d.dismiss();refresh.run();}catch(Exception e){n.setError("No se pudo guardar");}}));d.show();}\n\n'''
    if marker in s:s=s.replace(marker,users+marker,1)

MAIN.write_text(s,encoding='utf-8')
print('Robust DNI/user-management patch applied')
