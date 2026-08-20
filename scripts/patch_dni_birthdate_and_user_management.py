from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivity.java")
s = MAIN.read_text(encoding="utf-8")

# DNI: la fecha de nacimiento es un dato obligatorio/importante del OCR de DNI.
# No dependemos de una cadena exacta del parche anterior: ocultamos solo los campos
# que no corresponden al DNI y conservamos birth.
s = s.replace(
    'if(dniMode){cif.setVisibility(View.GONE);birth.setVisibility(View.GONE);nationality.setVisibility(View.GONE);sex.setVisibility(View.GONE);birthPlace.setVisibility(View.GONE);parents.setVisibility(View.GONE);support.setVisibility(View.GONE);issue.setVisibility(View.GONE);validity.setVisibility(View.GONE);}',
    'if(dniMode){cif.setVisibility(View.GONE);nationality.setVisibility(View.GONE);sex.setVisibility(View.GONE);birthPlace.setVisibility(View.GONE);parents.setVisibility(View.GONE);support.setVisibility(View.GONE);issue.setVisibility(View.GONE);validity.setVisibility(View.GONE);birth.setVisibility(View.VISIBLE);birth.setHint("Fecha de nacimiento (IMPORTANTE)");}'
)
s = s.replace(
    'String[] remove={"birthDate","nationality","sex","birthPlace","parents","supportNumber","issueDate","validityDate","expiry","cif","products","insureds","product","ahorroModalidad"};',
    'String[] remove={"nationality","sex","birthPlace","parents","supportNumber","issueDate","validityDate","expiry","cif","products","insureds","product","ahorroModalidad"};'
)

# Menú visible de usuarios.
# No dependemos de que el bloque "Seguridad" conserve exactamente el formato
# generado por parches anteriores. Primero intentamos insertar antes de "Salir",
# que es un ancla estable del menú lateral. Como respaldo, usamos "Seguridad" y,
# finalmente, la inserción justo antes de main.addView(side,...).
if 'Button users=sideButton("👥  Usuarios de la aplicación")' not in s:
    users_line = 'Button users=sideButton("👥  Usuarios de la aplicación"); users.setOnClickListener(v->users()); side.addView(users,new LinearLayout.LayoutParams(-1,dp(68)));'

    # 1. Ancla preferente: botón Salir.
    # Insertar Usuarios inmediatamente antes de Salir evita depender del formato
    # concreto que tengan Seguridad/Futuras bajas después de otros parches.
    logout_re = r'(?m)^(\s*Button\s+logout\s*=\s*sideButton\("Salir"\);.*?side\.addView\(logout,\s*new\s+LinearLayout\.LayoutParams\(-1,\s*dp\(\s*56\s*\)\)\s*;)'
    m = re.search(logout_re, s)

    if m:
        anchor = m.group(1)
        indent = re.match(r'^\s*', anchor).group(0)
        insert = indent + users_line + '\n' + anchor
        s = s[:m.start(1)] + insert + s[m.end(1):]
    else:
        # 2. Segundo intento: localizar Seguridad sin exigir icono, espacios
        # concretos ni una línea con formato exacto.
        security_re = r'(?m)^(\s*Button\s+security\s*=\s*sideButton\([^;]+\);.*?side\.addView\(security,\s*new\s+LinearLayout\.LayoutParams\(-1,\s*dp\(\s*\d+\s*\)\)\s*;)'
        m = re.search(security_re, s)

        if m:
            anchor = m.group(1)
            indent = re.match(r'^\s*', anchor).group(0)
            insert = anchor + '\n' + indent + users_line
            s = s[:m.start(1)] + insert + s[m.end(1):]
        else:
            # 3. Último respaldo: insertar antes de añadir el menú lateral
            # al layout principal.
            side_re = r'(?m)^(\s*main\.addView\(side,\s*new\s+LinearLayout\.LayoutParams\(dp\(\s*150\s*\),-1\)\);)'
            m = re.search(side_re, s)

            if m:
                indent = re.match(r'^\s*', m.group(1)).group(0)
                insert = indent + users_line + '\n' + m.group(1)
                s = s[:m.start(1)] + insert + s[m.end(1):]
            else:
                raise SystemExit("home menu insertion anchor not found")

# Primera cuenta -> almacén local de usuarios.
old_create = 'prefs.edit().putString("user",u.getText().toString().trim()).putString("pin",p.getText().toString()).putBoolean("biometric",true).apply();currentUser=u.getText().toString().trim();home();'
new_create = 'String nu=u.getText().toString().trim();String np=p.getText().toString();prefs.edit().putString("user",nu).putString("pin",np).putBoolean("biometric",true).putString("appUsers",new JSONArray().put(new JSONObject().put("name",nu).put("pin",np).put("active",true).put("role","ADMIN")).toString()).apply();currentUser=nu;home();'
if old_create in s:
    s = s.replace(old_create, new_create, 1)

# Login multiusuario.
if 'private JSONArray appUsers()' not in s:
    old = re.search(r'    private void login\(\)\{.*?\n    \}\n    private void biometricLogin\(\)', s, re.S)
    if not old:
        raise SystemExit("login method block not found")
    replacement = r'''    private JSONArray appUsers(){
        try{
            String raw=prefs.getString("appUsers","");
            if(raw.isEmpty()){
                JSONArray a=new JSONArray();String u=prefs.getString("user","");String pin=prefs.getString("pin","");
                if(!u.isEmpty())a.put(new JSONObject().put("name",u).put("pin",pin).put("active",true).put("role","ADMIN"));
                prefs.edit().putString("appUsers",a.toString()).apply();return a;
            }
            return new JSONArray(raw);
        }catch(Exception e){return new JSONArray();}
    }
    private void saveAppUsers(JSONArray a){prefs.edit().putString("appUsers",a.toString()).apply();}
    private void login(){
        LinearLayout l=col();EditText u=edit("Usuario"),p=edit("Clave de 6 dígitos");
        p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        l.addView(u,new LinearLayout.LayoutParams(-1,dp(52)));l.addView(p,new LinearLayout.LayoutParams(-1,dp(52)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Entrar en RgaPro").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Entrar",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            String un=u.getText().toString().trim(),pw=p.getText().toString();boolean ok=false;JSONArray a=appUsers();
            for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);if(item!=null&&item.optBoolean("active",true)&&un.equalsIgnoreCase(item.optString("name","") )&&pw.equals(item.optString("pin",""))){ok=true;currentUser=item.optString("name",un);prefs.edit().putString("user",currentUser).putString("pin",pw).apply();break;}}
            if(ok){d.dismiss();home();}else p.setError("Usuario o clave incorrectos, o usuario desactivado");
        }));d.show();
    }
    private void biometricLogin(){'''
    s = s[:old.start()] + replacement + s[old.end():]

# Pantalla de gestión de usuarios.
if 'private void users(){' not in s:
    marker='    private void security(){'
    if marker not in s:
        raise SystemExit("security method marker not found")
    users_method=r'''    private void users(){
        page("Usuarios de la aplicación","Gestiona quién puede acceder a RgaPro");
        content.addView(tv("Crea, edita, activa/desactiva o elimina usuarios locales. El usuario actual no se puede eliminar.",13,MUTED,false));
        Button add=action("＋  Añadir usuario",true);content.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout list=col();content.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();JSONArray a=appUsers();
            for(int i=0;i<a.length();i++){
                final int idx=i;JSONObject item=a.optJSONObject(i);if(item==null)continue;String name=item.optString("name","Sin nombre");boolean active=item.optBoolean("active",true);String role=item.optString("role","USUARIO");
                LinearLayout row=col();row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackground(bg(Color.WHITE,14));
                row.addView(tv((active?"🟢 ":"⚪ ")+name+" · "+role,16,TEXT,true));row.addView(tv(active?"Activo":"Desactivado",13,active?Color.rgb(25,110,70):MUTED,false));
                LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);Button editUser=action("Editar",false),toggle=action(active?"Desactivar":"Activar",false),del=action("Eliminar",false);
                actions.addView(editUser,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(toggle,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(del,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(actions);
                editUser.setOnClickListener(v->editAppUser(idx,refresh));
                toggle.setOnClickListener(v->{try{JSONObject x=a.optJSONObject(idx);if(x!=null){x.put("active",!active);saveAppUsers(a);refresh.run();}}catch(Exception e){Toast.makeText(this,"No se pudo cambiar el estado",Toast.LENGTH_SHORT).show();}});
                del.setEnabled(!name.equalsIgnoreCase(currentUser));del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Eliminar usuario").setMessage("¿Eliminar a "+name+" de este dispositivo?").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{JSONArray now=appUsers();if(idx>=0&&idx<now.length()){now.remove(idx);saveAppUsers(now);refresh.run();}}).show());
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(146));rp.bottomMargin=dp(10);list.addView(row,rp);
            }
            if(a.length()==0)list.addView(tv("No hay usuarios configurados.",15,MUTED,false));
        };
        add.setOnClickListener(v->addAppUser(refresh));refresh.run();
    }
    private void addAppUser(Runnable refresh){
        LinearLayout l=col();EditText n=edit("Nombre de usuario"),p=edit("Clave de 6 dígitos"),p2=edit("Repite la clave");p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);p2.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(n);l.addView(p);l.addView(p2);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Añadir usuario").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String name=n.getText().toString().trim(),pin=p.getText().toString();if(name.isEmpty()||!pin.matches("\\d{6}")||!pin.equals(p2.getText().toString())){p2.setError("Nombre y clave válida obligatorios");return;}JSONArray a=appUsers();for(int i=0;i<a.length();i++){JSONObject q=a.optJSONObject(i);if(q!=null&&name.equalsIgnoreCase(q.optString("name",""))){n.setError("Ese usuario ya existe");return;}}try{a.put(new JSONObject().put("name",name).put("pin",pin).put("active",true).put("role","USUARIO"));saveAppUsers(a);d.dismiss();refresh.run();}catch(Exception e){Toast.makeText(this,"No se pudo crear el usuario",Toast.LENGTH_SHORT).show();}}));d.show();
    }
    private void editAppUser(int idx,Runnable refresh){
        JSONArray a=appUsers();JSONObject old=a.optJSONObject(idx);if(old==null)return;String oldName=old.optString("name","");LinearLayout l=col();EditText n=edit("Nombre de usuario"),p=edit("Nueva clave (6 dígitos, opcional)");n.setText(oldName);p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(n);l.addView(p);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar usuario").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String name=n.getText().toString().trim(),pin=p.getText().toString();if(name.isEmpty()){n.setError("Nombre obligatorio");return;}if(!pin.isEmpty()&&!pin.matches("\\d{6}")){p.setError("La clave debe tener 6 dígitos");return;}try{old.put("name",name);if(!pin.isEmpty())old.put("pin",pin);a.put(idx,old);saveAppUsers(a);if(oldName.equalsIgnoreCase(currentUser)){currentUser=name;prefs.edit().putString("user",name).putString("pin",old.optString("pin",prefs.getString("pin",""))).apply();}d.dismiss();refresh.run();}catch(Exception e){Toast.makeText(this,"No se pudo editar el usuario",Toast.LENGTH_SHORT).show();}}));d.show();
    }

'''
    s=s.replace(marker,users_method+marker,1)

MAIN.write_text(s,encoding="utf-8")
print("DNI birth date restored and app user management added")
