from pathlib import Path

p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Ensure every local installation has a public/private identity for secure sharing.
needle='prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);if(!prefs.contains("user"))createUser();else showLogin();'
repl='prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);try{if(prefs.contains("user"))SecureShareManager.ensureIdentity(this,prefs.getString("user",""));}catch(Exception ignored){}if(!prefs.contains("user"))createUser();else showLogin();'
if needle in s:s=s.replace(needle,repl,1)

# Add security-management shortcuts beside the existing security button.
needle='Button security=sideButton("🔒  Seguridad"); security.setOnClickListener(v->security()); side.addView(security,new LinearLayout.LayoutParams(-1,dp(60)));'
repl=needle+'\n        Button users=sideButton("👥  Usuarios autorizados"); users.setOnClickListener(v->authorizedUsers()); side.addView(users,new LinearLayout.LayoutParams(-1,dp(60)));\n        Button imports=sideButton("📥  Importar compartido"); imports.setOnClickListener(v->importSecureShare()); side.addView(imports,new LinearLayout.LayoutParams(-1,dp(60)));'
if needle in s and 'authorizedUsers();' not in s:s=s.replace(needle,repl,1)

# Inject methods before final class closing brace.
if 'private void authorizedUsers()' not in s:
    methods=r'''
    private JSONArray authorizedUsersData(){try{return new JSONArray(prefs.getString("authorized_users","[]"));}catch(Exception e){return new JSONArray();}}
    private void saveAuthorizedUsers(JSONArray a){prefs.edit().putString("authorized_users",a.toString()).apply();}
    private void authorizedUsers(){
        page("Usuarios autorizados","Control local de acceso y compartición cifrada");
        content.addView(tv("Los usuarios deben estar registrados y autorizados antes de recibir datos. La clave privada nunca sale de su dispositivo.",14,MUTED,false));
        Button invite=action("➕  Registrar / autorizar usuario",true);content.addView(invite,new LinearLayout.LayoutParams(-1,dp(58)));invite.setOnClickListener(v->addAuthorizedUser());
        LinearLayout list=col();content.addView(list);JSONArray a=authorizedUsersData();
        for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u==null)continue;LinearLayout row=col();row.setPadding(dp(10),dp(8),dp(10),dp(8));row.setBackground(bg(Color.WHITE,14));row.addView(tv((u.optBoolean("enabled",true)?"🟢 ":"🔴 ")+u.optString("username","Usuario"),17,TEXT,true));row.addView(tv("Autorizado: "+u.optString("authorizedAt","")+"\nClave pública registrada",12,MUTED,false));Button revoke=action(u.optBoolean("enabled",true)?"Revocar acceso":"Reactivar acceso",false);final int ix=i;revoke.setOnClickListener(vv->{try{JSONObject x=a.optJSONObject(ix);x.put("enabled",!x.optBoolean("enabled",true));saveAuthorizedUsers(a);authorizedUsers();}catch(Exception ignored){}});row.addView(revoke,new LinearLayout.LayoutParams(-1,dp(48)));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(120));rp.bottomMargin=dp(8);list.addView(row,rp);}
    }
    private void addAuthorizedUser(){
        LinearLayout f=col();EditText name=edit("Nombre/usuario");EditText reg=edit("Registro seguro (JSON)");f.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));f.addView(reg,new LinearLayout.LayoutParams(-1,dp(130)));AlertDialog d=new AlertDialog.Builder(this).setTitle("Autorizar usuario").setMessage("En el otro dispositivo: Seguridad → Registrar dispositivo → compartir el registro. Pégalo aquí.").setView(f).setNegativeButton("Cancelar",null).setPositiveButton("Autorizar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{JSONObject r=new JSONObject(reg.getText().toString().trim());if(!r.has("publicKey")||r.optString("publicKey").isEmpty())throw new IllegalArgumentException();JSONArray a=authorizedUsersData();JSONObject u=new JSONObject();u.put("username",name.getText().toString().trim().isEmpty()?r.optString("username","Usuario"):name.getText().toString().trim());u.put("publicKey",r.getString("publicKey"));u.put("enabled",true);u.put("authorizedAt",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date()));a.put(u);saveAuthorizedUsers(a);d.dismiss();authorizedUsers();}catch(Exception e){Toast.makeText(this,"Registro no válido",Toast.LENGTH_LONG).show();}}));d.show();
    }
    private void registerSecureDevice(){
        try{String code=prefs.getString("activation_code","");if(code.isEmpty()){code=UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);prefs.edit().putString("activation_code",code).apply();}String reg=SecureShareManager.exportRegistration(this,prefs.getString("user",""),code);AlertDialog d=new AlertDialog.Builder(this).setTitle("Registro seguro").setMessage("Comparte este registro con el administrador para que te autorice:\n\n"+reg).setPositiveButton("Compartir",null).setNegativeButton("Cerrar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,reg);startActivity(Intent.createChooser(i,"Compartir registro seguro"));}));d.show();}catch(Exception e){Toast.makeText(this,"No se pudo generar el registro",Toast.LENGTH_LONG).show();}
    }
    private void importSecureShare(){
        EditText e=edit("Pega aquí el paquete RGAPRO_SECURE_SHARE");AlertDialog d=new AlertDialog.Builder(this).setTitle("Importar datos compartidos").setMessage("La importación exige autorización y descifrado local. Comprueba el remitente antes de aceptar.").setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Autorizar y descifrar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->authorizeAndImport(e.getText().toString().trim(),d)));d.show();
    }
    private void authorizeAndImport(String text,AlertDialog dialog){
        try{int r=BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);if(r==BiometricManager.BIOMETRIC_SUCCESS){BiometricPrompt bp=new BiometricPrompt(this,biometricExecutor,new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){runOnUiThread(()->finishSecureImport(text,dialog));}});bp.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Autorizar importación").setSubtitle("Confirma que quieres recibir estos datos").setNegativeButtonText("Cancelar").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build());}else finishSecureImport(text,dialog);}catch(Exception e){Toast.makeText(this,"No se pudo autorizar la importación",Toast.LENGTH_LONG).show();}}
    private void finishSecureImport(String text,AlertDialog dialog){try{JSONObject data=SecureShareManager.decrypt(this,text);JSONArray a=data();a.put(data);save(a);dialog.dismiss();Toast.makeText(this,"✅ Datos recibidos y descifrados localmente",Toast.LENGTH_LONG).show();home();}catch(Exception e){Toast.makeText(this,"Paquete no válido o no destinado a este dispositivo",Toast.LENGTH_LONG).show();}}
    private void shareSecureClient(JSONObject client){
        try{JSONArray users=authorizedUsersData();ArrayList<String> names=new ArrayList<>();ArrayList<Integer> indexes=new ArrayList<>();for(int i=0;i<users.length();i++){JSONObject u=users.optJSONObject(i);if(u!=null&&u.optBoolean("enabled",true)&&!u.optString("publicKey","").isEmpty()){names.add(u.optString("username","Usuario"));indexes.add(i);}}if(names.isEmpty()){Toast.makeText(this,"No hay usuarios autorizados. Registra primero al destinatario.",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle("Compartir cliente").setItems(names.toArray(new String[0]),(d,w)->confirmSecureShare(client,users.optJSONObject(indexes.get(w)))).setNegativeButton("Cancelar",null).show();}catch(Exception e){Toast.makeText(this,"No se pudo abrir el selector",Toast.LENGTH_LONG).show();}}
    private void confirmSecureShare(JSONObject client,JSONObject user){new AlertDialog.Builder(this).setTitle("Autorizar envío").setMessage("Vas a enviar datos de: "+client.optString("holder","cliente")+"\nDestinatario: "+user.optString("username","usuario")+"\n\nLos datos se cifrarán para ese usuario y no podrán abrirse con otra cuenta.").setNegativeButton("Cancelar",null).setPositiveButton("Autorizar con biometría",(d,w)->doBiometricShare(client,user)).show();}
    private void doBiometricShare(JSONObject client,JSONObject user){int r=BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);if(r!=BiometricManager.BIOMETRIC_SUCCESS){Toast.makeText(this,"Se requiere biometría disponible para autorizar el envío",Toast.LENGTH_LONG).show();return;}BiometricPrompt bp=new BiometricPrompt(this,biometricExecutor,new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){runOnUiThread(()->{try{String pkg=SecureShareManager.encryptForRecipient(MainActivity.this,client,user.getString("publicKey"),currentUser);Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,pkg);startActivity(Intent.createChooser(i,"Enviar cliente cifrado"));}catch(Exception e){Toast.makeText(MainActivity.this,"No se pudo cifrar el cliente",Toast.LENGTH_LONG).show();}});}});bp.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Autorizar envío").setSubtitle("Confirma el uso compartido de datos").setNegativeButtonText("Cancelar").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build());}
'''
    marker='    private void security(){'
    idx=s.find(marker)
    if idx>=0:
        close=s.find('\n    }',idx)
        if close>=0:
            sec='''\n        Button register=action("📱  Registrar este dispositivo con el administrador",true);content.addView(register,new LinearLayout.LayoutParams(-1,dp(58)));register.setOnClickListener(v->registerSecureDevice());\n        Button users=action("👥  Usuarios autorizados",false);content.addView(users,new LinearLayout.LayoutParams(-1,dp(58)));users.setOnClickListener(v->authorizedUsers());\n'''
            s=s[:close]+sec+s[close:]
    pos=s.rfind('\n}')
    s=s[:pos]+methods+s[pos:]

# Add a share button to client detail without depending on exact existing UI layout.
if 'shareSecureClient(p);' not in s:
    marker='private void detail(JSONObject p)'
    idx=s.find(marker)
    if idx>=0:
        openb=s.find('{',idx)
        if openb>=0:s=s[:openb+1]+'\n        Button secureShare=action("🔐  Compartir cliente con usuario autorizado",true);secureShare.setOnClickListener(v->shareSecureClient(p));content.addView(secureShare,new LinearLayout.LayoutParams(-1,dp(58)));'+s[openb+1:]

# Session security: lock whenever the screen is turned off, Home is pressed, or Back exits the app.
if 'private BroadcastReceiver securityLockReceiver;' not in s:
    s=s.replace('import android.content.Intent;','import android.content.Intent;\nimport android.content.BroadcastReceiver;\nimport android.content.Context;\nimport android.content.IntentFilter;',1)
    s=s.replace('private final Executor biometricExecutor=Executors.newSingleThreadExecutor();','private final Executor biometricExecutor=Executors.newSingleThreadExecutor();\n    private BroadcastReceiver securityLockReceiver;\n    private boolean securitySessionActive=false;',1)
    s=s.replace('@Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(NAVY);prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);','@Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(NAVY);prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);securityLockReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))lockForSecurity();}};registerReceiver(securityLockReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF));',1)
    s=s.replace('currentUser=u.getText().toString().trim();home();','currentUser=u.getText().toString().trim();securitySessionActive=true;home();',1)
    s=s.replace('currentUser=prefs.getString("user","");home();','currentUser=prefs.getString("user","");securitySessionActive=true;home();',1)
    methods2=r'''
    private void lockForSecurity(){securitySessionActive=false;currentUser=null;showLogin();}
    @Override public void onUserLeaveHint(){super.onUserLeaveHint();if(securitySessionActive)lockForSecurity();}
    @Override public void onBackPressed(){if(securitySessionActive)lockForSecurity();else super.onBackPressed();}
    @Override protected void onDestroy(){try{if(securityLockReceiver!=null)unregisterReceiver(securityLockReceiver);}catch(Exception ignored){}super.onDestroy();}
'''
    pos=s.rfind('\n}')
    s=s[:pos]+methods2+s[pos:]
s=s.replace('Button logout=sideButton("Salir"); logout.setOnClickListener(v->showLogin());','Button logout=sideButton("Salir"); logout.setOnClickListener(v->lockForSecurity());',1)

p.write_text(s,encoding='utf-8')
print('secure users/sharing/session lock patch prepared')
