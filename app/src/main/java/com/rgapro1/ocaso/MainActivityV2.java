package com.rgapro1.ocaso;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

public class MainActivityV2 extends FragmentActivity {
    private static final int NAVY=Color.rgb(8,35,70), BLUE=Color.rgb(22,126,222), BG=Color.rgb(246,248,252);
    private static final int TEXT=Color.rgb(28,39,54), MUTED=Color.rgb(100,112,130), GREEN=Color.rgb(31,132,88);
    private static final int CAMERA=8101, IMAGE=8102, PDF=8103, POLICY_CAMERA=8104, POLICY_CAMERA_PERMISSION=8105, DOCUMENT=8107;
    private static final long SESSION_IDLE_LIMIT_MS=5*60*1000L;
    private SharedPreferences prefs; private LinearLayout body; private Uri cameraUri, documentUri; private int side=0;
    private SecurePinStore securePinStore; private final Handler sessionHandler=new Handler(Looper.getMainLooper());
    private boolean authenticated=false,clientStoreReadFailed=false; private long lastInteractionElapsed=0L;
    private final Runnable lockForInactivity=()->{if(authenticated){authenticated=false;showPinLogin();}};
    private String frontText="", backText="", currentImagePath="", frontImagePath="", backImagePath=""; private Bitmap currentBitmap, previewBitmap, frontBitmap, backBitmap;
    private int documentKind=0; // 1 imagen, 2 PDF
    private boolean awaitingDniBack=false, dniPairConfirmed=false;
    private final ArrayList<Uri> policyPageUris = new ArrayList<>();
    private final ArrayList<Bitmap> policyPageBitmaps = new ArrayList<>();
    private boolean policyCameraFlow = false;
    private EditText fullNameE,dniE,birthE,addressE,phoneE,emailE;
    private Button processOcrButton;
    private LinearLayout previewContainer;
    private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,policyBirthE,policyEffectiveE,policyExpiryE,receiptE,capitalE,decesosE,decesosLeveladaE;
    private TextView confidenceTv;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setPadding(dp(6),dp(6),dp(6),dp(6));return t;}
    private GradientDrawable box(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.rgb(222,228,236));return g;}
    private Button btn(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(box(primary?BLUE:Color.WHITE,18));b.setPadding(dp(10),dp(6),dp(10),dp(6));return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(box(Color.WHITE,14));return e;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        // Keep production screens private while allowing screenshot-based debugging in debug APKs.
        if (!BuildConfig.DEBUG) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        }
        prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);
        securePinStore=new SecurePinStore(getApplicationContext());
        if(securePinStore.hasPin())showPinLogin();else showPinSetup();
    }

    @Override protected void onStart(){
        super.onStart();
        if(authenticated&&lastInteractionElapsed>0){
            long remaining=SESSION_IDLE_LIMIT_MS-(SystemClock.elapsedRealtime()-lastInteractionElapsed);
            if(remaining<=0){authenticated=false;showPinLogin();}
            else sessionHandler.postDelayed(lockForInactivity,remaining);
        }
    }

    @Override protected void onStop(){
        sessionHandler.removeCallbacks(lockForInactivity);
        super.onStop();
    }

    @Override public void onUserInteraction(){
        super.onUserInteraction();
        if(authenticated)scheduleSessionLock();
    }

    @Override public void onBackPressed(){
        if(!authenticated){finish();return;}
        super.onBackPressed();
    }

    private void scheduleSessionLock(){
        lastInteractionElapsed=SystemClock.elapsedRealtime();
        sessionHandler.removeCallbacks(lockForInactivity);
        sessionHandler.postDelayed(lockForInactivity,SESSION_IDLE_LIMIT_MS);
    }

    private void showPinSetup(){
        LinearLayout root=authLayout("Crear acceso","Define un PIN de seis dígitos para proteger la cartera.");
        EditText first=input("Nuevo PIN de 6 dígitos"),confirm=input("Repite el PIN");
        first.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(first,new LinearLayout.LayoutParams(-1,dp(56)));
        root.addView(confirm,new LinearLayout.LayoutParams(-1,dp(56)));
        Button save=btn("Guardar PIN",true);root.addView(save,new LinearLayout.LayoutParams(-1,dp(58)));
        save.setOnClickListener(v->{
            String pin=first.getText().toString(),repeat=confirm.getText().toString();
            if(!pin.matches("\\d{6}")){first.setError("El PIN debe tener seis cifras");return;}
            if(!pin.equals(repeat)){confirm.setError("Los PIN no coinciden");return;}
            try{securePinStore.setPin(pin);prefs.edit().putInt("pin_failed_attempts",0).putInt("pin_lock_level",0).putLong("pin_lock_until",0L).apply();unlockSession();}
            catch(Exception e){Toast.makeText(this,"No se pudo proteger el PIN en este dispositivo.",Toast.LENGTH_LONG).show();}
        });
        setContentView(root);
    }

    private void showPinLogin(){
        LinearLayout root=authLayout("Acceso protegido","Introduce tu PIN para abrir la cartera.");
        EditText pin=input("PIN de 6 dígitos");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(pin,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView status=tv("",14,MUTED,false);root.addView(status);
        Button enter=btn("Desbloquear",true);root.addView(enter,new LinearLayout.LayoutParams(-1,dp(58)));
        enter.setOnClickListener(v->{
            long waitUntil=prefs.getLong("pin_lock_until",0L),now=System.currentTimeMillis();
            if(now<waitUntil){status.setText("Acceso temporalmente bloqueado. Inténtalo más tarde.");return;}
            if(securePinStore.verifyPin(pin.getText().toString())){
                prefs.edit().putInt("pin_failed_attempts",0).putInt("pin_lock_level",0).putLong("pin_lock_until",0L).apply();unlockSession();return;
            }
            int attempts=prefs.getInt("pin_failed_attempts",0)+1;
            SharedPreferences.Editor edit=prefs.edit().putInt("pin_failed_attempts",attempts);
            if(attempts>=5){
                int level=prefs.getInt("pin_lock_level",0)+1;long delay=Math.min(15*60_000L,30_000L*(1L<<Math.min(level-1,5)));
                edit.putInt("pin_failed_attempts",0).putInt("pin_lock_level",level).putLong("pin_lock_until",now+delay);
                status.setText("Demasiados intentos. Espera "+(delay<60_000L?delay/1_000L+" segundos.":delay/60_000L+" minutos."));
            }
            else status.setText("PIN incorrecto. Intento "+attempts+" de 5.");
            edit.apply();pin.setText("");
        });
        if(BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)==BiometricManager.BIOMETRIC_SUCCESS){
            Button biometric=btn("🔒 Entrar con huella o biometría",false);
            root.addView(biometric,new LinearLayout.LayoutParams(-1,dp(58)));
            biometric.setOnClickListener(v->biometricLogin());
        }
        setContentView(root);
    }

    private void biometricLogin(){
        int capability=BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if(capability!=BiometricManager.BIOMETRIC_SUCCESS){Toast.makeText(this,"Biometría no disponible. Puedes entrar con tu PIN.",Toast.LENGTH_LONG).show();return;}
        BiometricPrompt prompt=new BiometricPrompt(this,ContextCompat.getMainExecutor(this),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){unlockSession();}
            @Override public void onAuthenticationError(int code,CharSequence message){if(code!=BiometricPrompt.ERROR_NEGATIVE_BUTTON&&code!=BiometricPrompt.ERROR_USER_CANCELED&&code!=BiometricPrompt.ERROR_CANCELED)Toast.makeText(MainActivityV2.this,"No se pudo autenticar. Usa tu PIN.",Toast.LENGTH_LONG).show();}
            @Override public void onAuthenticationFailed(){Toast.makeText(MainActivityV2.this,"Huella o rostro no reconocido. Prueba de nuevo o usa el PIN.",Toast.LENGTH_SHORT).show();}
        });
        BiometricPrompt.PromptInfo info=new BiometricPrompt.PromptInfo.Builder().setTitle("Acceso a RgaPro").setSubtitle("Confirma tu identidad para abrir la cartera").setNegativeButtonText("Usar PIN").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build();
        prompt.authenticate(info);
    }

    private LinearLayout authLayout(String title,String subtitle){
        LinearLayout root=col();root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(24),dp(24),dp(24));root.setBackgroundColor(BG);
        TextView brand=tv("RgaPro",30,NAVY,true);brand.setGravity(Gravity.CENTER);root.addView(brand,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView heading=tv(title,22,TEXT,true);heading.setGravity(Gravity.CENTER);root.addView(heading,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView explain=tv(subtitle,15,MUTED,false);explain.setGravity(Gravity.CENTER);root.addView(explain,new LinearLayout.LayoutParams(-1,dp(60)));
        return root;
    }

    private void unlockSession(){authenticated=true;home();scheduleSessionLock();}

    private void shell(String title,String subtitle){
        LinearLayout root=col();root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(8),dp(12),dp(8));top.setBackgroundColor(NAVY);
        try{ImageView icon=new ImageView(this);icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);top.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));}catch(Exception ignored){}
        LinearLayout tt=col();tt.addView(tv("RgaPro",24,Color.WHITE,true));tt.addView(tv(subtitle,13,Color.LTGRAY,false));top.addView(tt,new LinearLayout.LayoutParams(0,dp(60),1));root.addView(top,new LinearLayout.LayoutParams(-1,dp(76)));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(NAVY);
        Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false),p=btn("Pólizas",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());p.setOnClickListener(v->policies());
        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(p,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);
        ScrollView sc=new ScrollView(this);body=col();body.setPadding(dp(16),dp(16),dp(16),dp(24));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void home(){shell("Inicio","Clientes, pólizas, documentos y vencimientos");body.addView(tv("Inicio",28,TEXT,true));body.addView(tv("BÚSQUEDA 360",14,BLUE,true));EditText q=input("DNI/NIE, nombre, teléfono, póliza o dirección");body.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));LinearLayout results=col();body.addView(results);Runnable search=()->{results.removeAllViews();String s=q.getText().toString().trim();if(s.isEmpty())return;JSONArray a=clientsData();ArrayList<JSONObject> hits=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&matches(x,s))hits.add(x);}Collections.sort(hits,(a1,b1)->clientKey(a1).compareToIgnoreCase(clientKey(b1)));if(hits.isEmpty())results.addView(tv("No hay coincidencias.",15,MUTED,false));for(JSONObject x:hits)addClientCard(results,x);};q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){search.run();}public void afterTextChanged(Editable e){}});
        Button scan=btn("📷  OCR / DOCUMENTOS",true);scan.setOnClickListener(v->ocrPage());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(64)));
        Button cs=btn("👥  CLIENTES A-Z",false);cs.setOnClickListener(v->clients());body.addView(cs,new LinearLayout.LayoutParams(-1,dp(58)));
        Button alarms=btn("🔔  PRÓXIMAS BAJAS / ALERTAS",false);alarms.setOnClickListener(v->expiries());body.addView(alarms,new LinearLayout.LayoutParams(-1,dp(58)));
        Button ps=btn("📄  PÓLIZAS",false);ps.setOnClickListener(v->policies());body.addView(ps,new LinearLayout.LayoutParams(-1,dp(58)));
    }

    private void expiries(){
        shell("Próximos vencimientos","Pólizas que vencen en los próximos 60 días");
        body.addView(tv("PRÓXIMOS VENCIMIENTOS",24,TEXT,true));
        JSONArray clients=clientsData();ArrayList<String> rows=new ArrayList<>();
        for(int i=0;i<clients.length();i++){
            JSONObject client=clients.optJSONObject(i);if(client==null)continue;
            JSONArray policies=client.optJSONArray("policies");if(policies==null)continue;
            for(int j=0;j<policies.length();j++){
                JSONObject policy=policies.optJSONObject(j);if(policy==null||"PROPUESTA".equalsIgnoreCase(policy.optString("documentStatus","")))continue;
                String expiry=policy.optString("expiry",policy.optString("validityDate",""));long days=daysUntil(expiry);
                if(days>=0&&days<=60)rows.add(String.format(Locale.ROOT,"%03d|🔔 %d días · %s%n%s · Vence: %s",days,days,clientKey(client),policy.optString("number","Póliza"),expiry));
            }
        }
        Collections.sort(rows);for(String row:rows){int divider=row.indexOf('|');TextView item=tv(divider>=0?row.substring(divider+1):row,15,TEXT,false);item.setBackground(box(Color.WHITE,14));body.addView(item,new LinearLayout.LayoutParams(-1,dp(70)));}
        if(rows.isEmpty())body.addView(tv("No hay pólizas formalizadas que venzan en los próximos 60 días.",15,MUTED,false));
    }

    private void clients(){shell("Clientes","Listado alfabético + Búsqueda 360");body.addView(tv("Clientes A-Z",26,TEXT,true));EditText q=input("Buscar DNI/NIE, nombre, teléfono, póliza o dirección");body.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));LinearLayout list=col();body.addView(list);Runnable r=()->{list.removeAllViews();ArrayList<JSONObject> xs=new ArrayList<>();JSONArray a=clientsData();String s=q.getText().toString().trim();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&(s.isEmpty()||matches(x,s)))xs.add(x);}Collections.sort(xs,(x,y)->clientKey(x).compareToIgnoreCase(clientKey(y)));if(xs.isEmpty())list.addView(tv("No hay clientes guardados todavía.",15,MUTED,false));for(JSONObject x:xs)addClientCard(list,x);};q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){r.run();}public void afterTextChanged(Editable e){}});r.run();Button add=btn("＋ NUEVO CLIENTE",true);add.setOnClickListener(v->editClient(null));body.addView(add,new LinearLayout.LayoutParams(-1,dp(58)));}
    private void addClientCard(LinearLayout list,JSONObject x){Button b=btn("👤  "+clientKey(x)+System.lineSeparator()+x.optString("identityNumber","")+" · "+x.optString("phone",""),false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setOnClickListener(v->detail(x));list.addView(b,new LinearLayout.LayoutParams(-1,dp(76)));}
    private String clientKey(JSONObject x){String h=x.optString("holder","").trim();if(!h.isEmpty())return h;String n=x.optString("name","").trim(),s=x.optString("surname","").trim();return (n+" "+s).trim().isEmpty()?"Sin nombre":(n+" "+s).trim();}
    private boolean matches(JSONObject x,String q){String s=normalizeSearch(q);for(String k:new String[]{"name","surname","holder","identityNumber","phone","email","address","policyNumber","type","expiry"})if(normalizeSearch(x.optString(k,"")).contains(s))return true;JSONArray ps=x.optJSONArray("policies");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&normalizeSearch(p.toString()).contains(s))return true;}return false;}
    private String normalizeSearch(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}

    private void detail(JSONObject x){
        shell("Ficha de cliente",clientKey(x));
        body.addView(tv(clientKey(x),25,TEXT,true));
        body.addView(tv("DATOS DEL CLIENTE",16,BLUE,true));
        addRead(body,"DNI/NIE/CIF",x.optString("identityNumber",x.optString("holderDni",x.optString("cif",""))));
        addRead(body,"Fecha de nacimiento",x.optString("birthDate",""));
        addRead(body,"Dirección",x.optString("address",""));
        addRead(body,"Teléfono",x.optString("phone",""));
        addRead(body,"Email",x.optString("email",""));
        body.addView(tv("DOCUMENTOS Y PÓLIZAS",16,BLUE,true));
        JSONArray docs=x.optJSONArray("documentPhotos");
        if(docs!=null) for(int i=0;i<docs.length();i++){
            Object item=docs.opt(i); String path=item instanceof JSONObject?((JSONObject)item).optString("path",""):String.valueOf(item);
            if(path==null||path.trim().isEmpty()) continue;
            Button d=btn("📄 "+new File(path).getName(),false);
            d.setOnClickListener(v->openDocument(path));
            body.addView(d,new LinearLayout.LayoutParams(-1,dp(56)));
        }
        JSONArray archived=x.optJSONArray("documents");
        if(archived!=null&&archived.length()>0)addDocumentsToDetail(body,x);
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











    private void openDocument(String path){
        try{
            File f=new File(path); if(!f.exists()){Toast.makeText(this,"No se encuentra el documento",Toast.LENGTH_LONG).show();return;}
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(u,mime(path)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        }catch(Exception e){Toast.makeText(this,"No se pudo abrir el documento",Toast.LENGTH_LONG).show();}
    }
    private String mime(String p){String x=p.toLowerCase(Locale.ROOT);if(x.endsWith(".pdf"))return "application/pdf";if(x.endsWith(".png"))return "image/png";if(x.endsWith(".webp"))return "image/webp";return "image/jpeg";}

    private void addRead(LinearLayout p,String label,String value){TextView t=tv(label+System.lineSeparator()+(value==null||value.isEmpty()?"—":value),15,TEXT,false);t.setBackground(box(Color.WHITE,12));p.addView(t,new LinearLayout.LayoutParams(-1,dp(62)));}

    private void editClient(JSONObject old){
        LinearLayout l=col();
        EditText n=input("Nombre y apellidos"),d=input("DNI/NIE"),b=input("Fecha de nacimiento dd/MM/yyyy"),ad=input("Dirección"),ph=input("Teléfono"),em=input("Email");
        if(old!=null){
            n.setText(clientKey(old));
            d.setText(old.optString("identityNumber",old.optString("holderDni","")));
            b.setText(old.optString("birthDate",""));
            ad.setText(old.optString("address",""));
            ph.setText(old.optString("phone",""));
            em.setText(old.optString("email",""));
        }
        for(EditText e:new EditText[]{n,d,b,ad,ph,em}) l.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));
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
                    x.put("email",em.getText().toString().trim());
                    if(!x.has("policies")) x.put("policies",new JSONArray());
                    if(!x.has("documentPhotos")) x.put("documentPhotos",new JSONArray());
                    if(upsertClient(x))clients();
                }catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
            }).show();
    }














    private void startPolicyPageCamera(){
        try{
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.CAMERA}, POLICY_CAMERA_PERMISSION);return;
            }
            File dir=new File(getFilesDir(),"policy_pages");
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear la carpeta de páginas");
            File f=File.createTempFile("poliza_pagina_",".jpg",dir);
            cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("photo",cameraUri));
            policyCameraFlow=true;
            startActivityForResult(i,POLICY_CAMERA);
        }catch(Exception e){Toast.makeText(this,"No se pudo abrir la cámara: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private Bitmap loadPolicyPageForOcr(Uri u) throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Página no disponible");BitmapFactory.decodeStream(in,null,o);}
        int max=2600,sample=1;while(Math.max(o.outWidth,o.outHeight)/(double)sample>max)sample*=2;
        BitmapFactory.Options real=new BitmapFactory.Options();real.inSampleSize=Math.max(1,sample);real.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("Página no disponible");Bitmap b=BitmapFactory.decodeStream(in,null,real);if(b==null)throw new IOException("No se pudo decodificar la página");return b;}
    }

    private File buildPolicyPdf() throws Exception{
        File dir=new File(getFilesDir(),"policy_pages");if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear el archivo");
        File out=File.createTempFile("poliza_documento_",".pdf",dir);
        android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
        try{
            for(int i=0;i<policyPageBitmaps.size();i++){
                Bitmap b=policyPageBitmaps.get(i);if(b==null)continue;
                int w=Math.max(595,b.getWidth());int h=(int)(w*(b.getHeight()/(double)Math.max(1,b.getWidth())));
                android.graphics.pdf.PdfDocument.PageInfo info=new android.graphics.pdf.PdfDocument.PageInfo.Builder(w,h,i+1).create();
                android.graphics.pdf.PdfDocument.Page page=pdf.startPage(info);Canvas c=page.getCanvas();Rect src=new Rect(0,0,b.getWidth(),b.getHeight());RectF dst=new RectF(0,0,w,h);c.drawBitmap(b,src,dst,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));pdf.finishPage(page);
            }
            try(FileOutputStream os=new FileOutputStream(out)){pdf.writeTo(os);}
        }finally{pdf.close();}
        return out;
    }

    private void askPolicyPageNext(){
        int n=policyPageUris.size();
        LinearLayout box=col();box.setPadding(dp(8),dp(4),dp(8),dp(4));
        box.addView(tv("PÁGINA "+n+" CAPTURADA",18,BLUE,true));
        box.addView(tv("Puedes seguir fotografiando el documento. El orden de las páginas se conserva.",14,MUTED,false));
        if(!policyPageBitmaps.isEmpty()){ImageView iv=new ImageView(this);iv.setImageBitmap(policyPageBitmaps.get(policyPageBitmaps.size()-1));iv.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(iv,new LinearLayout.LayoutParams(-1,dp(260)));}
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Documento de póliza").setView(box).setNegativeButton("TERMINAR Y LEER",null).setPositiveButton("AÑADIR OTRA PÁGINA",null).create();
        dlg.setOnShowListener(v->{Button more=dlg.getButton(AlertDialog.BUTTON_POSITIVE),done=dlg.getButton(AlertDialog.BUTTON_NEGATIVE);more.setOnClickListener(x->{dlg.dismiss();startPolicyPageCamera();});done.setOnClickListener(x->{dlg.dismiss();finishPolicyPhotoDocument();});});
        dlg.show();
    }

    private void finishPolicyPhotoDocument(){
        if(policyPageUris.isEmpty()){Toast.makeText(this,"No se ha fotografiado ninguna página.",Toast.LENGTH_LONG).show();return;}
        try{
            File pdf=buildPolicyPdf();documentUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pdf);documentKind=2;previewBitmap=policyPageBitmaps.isEmpty()?null:policyPageBitmaps.get(0);currentImagePath=documentUri.toString();
            ocrPolicyPagesSequentially(0,new StringBuilder());
        }catch(Exception e){Toast.makeText(this,"No se pudo preparar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void ocrPolicyPagesSequentially(final int index,final StringBuilder all){
        if(index>=policyPageBitmaps.size()){
            String raw=all.toString().trim();
            try{JSONObject p=OcasoPolicyParser.parse(raw);showPolicyReview(p,raw);}catch(Exception e){Toast.makeText(this,"No se pudo interpretar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            return;
        }
        Toast.makeText(this,"Leyendo página "+(index+1)+" de "+policyPageBitmaps.size()+"…",Toast.LENGTH_SHORT).show();
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(InputImage.fromBitmap(policyPageBitmaps.get(index),0)).addOnSuccessListener(result->{
            if(result!=null&&!result.getText().trim().isEmpty()){if(all.length()>0)all.append("\n\n===== PÁGINA ").append(index+1).append(" =====\n");all.append(result.getText());}
            r.close();ocrPolicyPagesSequentially(index+1,all);
        }).addOnFailureListener(e->{r.close();all.append("\n\n===== PÁGINA ").append(index+1).append(" =====\n");all.append("[OCR ERROR: ").append(e.getMessage()==null?"":e.getMessage()).append("]");ocrPolicyPagesSequentially(index+1,all);});
    }

    private void policyDetail(JSONObject p){
        LinearLayout l=col();
        String product=p.optString("policyType",p.optString("type","Póliza"));
        boolean decesos="Decesos".equalsIgnoreCase(product);
        addRead(l,"Estado del documento","PROPUESTA".equalsIgnoreCase(p.optString("documentStatus",""))?"Propuesta pendiente de formalización":"Póliza");
        addRead(l,"Producto",product);
        addRead(l,"PROPUESTA".equalsIgnoreCase(p.optString("documentStatus",""))?"Número de referencia":"Número de póliza",p.optString("number",""));
        addRead(l,"Tomador",p.optString("holder",""));
        addRead(l,"DNI/NIE/CIF",p.optString("identityNumber",p.optString("cif","")));
        addRead(l,"Dirección",p.optString("address",""));
        addRead(l,"Teléfono",p.optString("phone",""));
        addRead(l,"Email",p.optString("email",""));
        addRead(l,"Precio / recibo",p.optString("receipt",""));
        if(!"PROPUESTA".equalsIgnoreCase(p.optString("documentStatus","")))addRead(l,"Vencimiento",p.optString("expiry",p.optString("validityDate","")));
        String localDoc=p.optString("localDocumentPath","");
        if(!localDoc.isEmpty()){Button open=btn("📎 ABRIR DOCUMENTO ORIGINAL",true);open.setOnClickListener(v->openArchivedDocument(localDoc,mime(localDoc)));l.addView(open,new LinearLayout.LayoutParams(-1,dp(58)));}
        if("PROPUESTA".equalsIgnoreCase(p.optString("documentStatus","")))addRead(l,"Válida para contratar hasta",p.optString("proposalValidUntil",""));
        else addRead(l,"Fecha de emisión",p.optString("issueDate",""));
        if(decesos){
            addRead(l,"Capital de decesos",p.optString("capital",""));
            addRead(l,"Total decesos",p.optString("decesos",""));
            addRead(l,"Decesos nivelada",p.optString("decesosLevelada",""));
        }else if(!p.optString("capital","").trim().isEmpty()){
            addRead(l,"Capital",p.optString("capital",""));
        }
        l.addView(tv("ASEGURADOS",16,BLUE,true));
        JSONArray ins=p.optJSONArray("insured");
        if(ins!=null)for(int i=0;i<ins.length();i++){JSONObject a=ins.optJSONObject(i);if(a==null)continue;addRead(l,a.optString("name","Asegurado"),"DNI: "+a.optString("identityNumber","—")+" · Nacimiento: "+a.optString("birthDate","—"));}
        new AlertDialog.Builder(this).setTitle("Póliza Ocaso").setView(l).setPositiveButton("Cerrar",null).show();
    }




























    private void policies(){shell("Pólizas","Pólizas Ocaso guardadas");JSONArray a=clientsData();boolean any=false;for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c==null)continue;JSONArray ps=c.optJSONArray("policies");if(ps==null)continue;for(int j=0;j<ps.length();j++){JSONObject p=ps.optJSONObject(j);if(p==null)continue;any=true;String status="PROPUESTA".equalsIgnoreCase(p.optString("documentStatus",""))?"Propuesta pendiente":"Póliza";Button b=btn("▣ "+status+" · "+p.optString("number","—")+System.lineSeparator()+clientKey(c),false);b.setOnClickListener(v->policyDetail(p));body.addView(b,new LinearLayout.LayoutParams(-1,dp(72)));}}if(!any)body.addView(tv("No hay pólizas guardadas.",15,MUTED,false));Button scan=btn("📄 SUBIR PÓLIZA PDF",true);scan.setOnClickListener(v->choosePdf());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(60)));Button cameraPolicy=btn("📷 FOTOGRAFIAR PÓLIZA · VARIAS PÁGINAS",true);cameraPolicy.setOnClickListener(v->{policyPageUris.clear();policyPageBitmaps.clear();policyCameraFlow=true;startPolicyPageCamera();});body.addView(cameraPolicy,new LinearLayout.LayoutParams(-1,dp(64)));}

    private void ocrPage(){shell("OCR","Primero revisa el documento; después procesa y acepta los datos");body.addView(tv("1 · DOCUMENTO",18,BLUE,true));body.addView(tv("El archivo NO se guarda todavía. Primero comprueba que es el documento correcto.",14,MUTED,false));LinearLayout preview=col();previewContainer=preview;preview.setBackground(box(Color.WHITE,16));body.addView(preview);renderPreview(preview);
        LinearLayout r=new LinearLayout(this);Button front=btn("📷 Tomar anverso",true),back=btn("↩️ Tomar reverso",false);r.addView(front,new LinearLayout.LayoutParams(0,dp(58),1));r.addView(back,new LinearLayout.LayoutParams(0,dp(58),1));body.addView(r);front.setOnClickListener(v->{if(!awaitingDniBack)resetDniIntake();side=1;takePhoto();});back.setOnClickListener(v->{side=2;takePhoto();});
        Button addDocument=btn("📎 AÑADIR DOCUMENTO · PDF O JPEG/JPG",false);body.addView(addDocument,new LinearLayout.LayoutParams(-1,dp(58)));addDocument.setOnClickListener(v->chooseDocument());
        Button process=btn("▶ PROCESAR OCR",true);processOcrButton=process;process.setOnClickListener(v->processCurrentDocument());body.addView(process,new LinearLayout.LayoutParams(-1,dp(62)));
        if(documentKind==0)body.addView(tv("Selecciona o fotografía un documento para continuar.",14,MUTED,false));
    }

    private void renderPreview(LinearLayout container){if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);container.addView(iv,new LinearLayout.LayoutParams(-1,dp(360)));container.addView(tv(documentKind==2?"Vista previa: primera página del PDF":"Vista previa: imagen original",14,GREEN,true));}else container.addView(tv(documentKind==2?"Preparando vista previa del PDF…":"Aún no has seleccionado ningún documento.",15,MUTED,false));}

    private void takePhoto(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA);return;}try{File f=new File(getFilesDir(),"captures/scan_"+System.currentTimeMillis()+".jpg");File parent=f.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("No se pudo crear la carpeta privada");cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAMERA);}catch(Exception e){Toast.makeText(this,"No se pudo abrir la cámara",Toast.LENGTH_LONG).show();}}

    private void chooseDocument(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,DOCUMENT);
    }

    private boolean isPdfDocument(Uri uri){String mime=getContentResolver().getType(uri);if("application/pdf".equalsIgnoreCase(mime))return true;String name=displayName(uri).toLowerCase(Locale.ROOT);return name.endsWith(".pdf");}
    private boolean isImageDocument(Uri uri){String mime=getContentResolver().getType(uri);if(mime!=null&&mime.toLowerCase(Locale.ROOT).startsWith("image/"))return true;String name=displayName(uri).toLowerCase(Locale.ROOT);return name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".png");}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}return uri.getLastPathSegment()==null?"":uri.getLastPathSegment();}



































    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(grantResults.length==0||grantResults[0]!=PackageManager.PERMISSION_GRANTED){
            if(requestCode==POLICY_CAMERA_PERMISSION)Toast.makeText(this,"Permiso de cámara denegado.",Toast.LENGTH_LONG).show();
            return;
        }
        if(requestCode==CAMERA)takePhoto();
        else if(requestCode==POLICY_CAMERA_PERMISSION)startPolicyPageCamera();
    }

    private void chooseImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,IMAGE);}
    private void choosePdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PDF);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        try{
            if(request==POLICY_CAMERA){
                if(cameraUri==null)throw new IOException("No se recibió la foto");
                Bitmap page=loadPolicyPageForOcr(cameraUri);policyPageUris.add(cameraUri);policyPageBitmaps.add(page);policyCameraFlow=true;askPolicyPageNext();return;
            }
            if(request==PDF){Uri u=data==null?null:data.getData();if(u!=null)openPdfForOcr(u);return;}
            if(request==DOCUMENT&&data!=null){
                if(data.getClipData()!=null&&data.getClipData().getItemCount()>=2){
                    Uri first=data.getClipData().getItemAt(0).getUri(),second=data.getClipData().getItemAt(1).getUri();
                    if(isImageDocument(first)&&isImageDocument(second)){resetDniIntake();frontBitmap=loadBitmap(first);backBitmap=loadBitmap(second);frontImagePath=first.toString();backImagePath=second.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentUri=first;documentKind=1;reviewDniPair();return;}
                }
                Uri u=data.getData()!=null?data.getData():data.getClipData()==null?null:data.getClipData().getItemAt(0).getUri();if(u==null)return;
                if(isPdfDocument(u)){openPdfForOcr(u);return;}
                if(!isImageDocument(u)){Toast.makeText(this,"Elige un PDF o una imagen JPEG/JPG.",Toast.LENGTH_LONG).show();return;}
                if(!awaitingDniBack)resetDniIntake();
                documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
                if(awaitingDniBack){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}
                ocrPage();processImage();return;
            }
            if(request==IMAGE && data!=null && data.getClipData()!=null){
                int n=data.getClipData().getItemCount();
                if(n>=2){resetDniIntake();Uri f=data.getClipData().getItemAt(0).getUri();Uri b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(f);backBitmap=loadBitmap(b);frontImagePath=f.toString();backImagePath=b.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentUri=f;documentKind=1;reviewDniPair();return;}
            }
            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            if(request==IMAGE&&!awaitingDniBack)resetDniIntake();
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}
            else if(frontBitmap==null){frontBitmap=currentBitmap;frontImagePath=currentImagePath;}
            else {backBitmap=currentBitmap;backImagePath=currentImagePath;}
            if(awaitingDniBack&&frontBitmap!=null&&backBitmap!=null){ocrPage();processImage();}
            else if(frontBitmap!=null&&backBitmap!=null){reviewDniPair();}
            else {ocrPage();processImage();}
        }catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void openPdfForOcr(Uri uri){
        resetDniIntake();documentUri=uri;documentKind=2;previewBitmap=null;currentImagePath=uri.toString();ocrPage();processCurrentDocument();
        new Thread(()->{
            try{
                Bitmap firstPage=renderPdfFirstPage(uri);
                runOnUiThread(()->{if(isFinishing()||!uri.equals(documentUri))return;previewBitmap=firstPage;if(previewContainer!=null&&previewContainer.isAttachedToWindow()){previewContainer.removeAllViews();renderPreview(previewContainer);}});
            }catch(Exception ignored){runOnUiThread(()->Toast.makeText(this,"No se pudo mostrar la vista previa; el OCR continuará igualmente.",Toast.LENGTH_LONG).show());}
        },"RgaPro-PdfPreview").start();
    }
    private Bitmap loadBitmap(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(b==null)throw new IOException("imagen vacía");return b;}
    private Bitmap renderPdfFirstPage(Uri u)throws Exception{try(ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(u,"r")){if(pfd==null)throw new IOException("PDF no disponible");android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(pfd);try{if(renderer.getPageCount()==0)throw new IOException("PDF vacío");android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(0);int w=Math.min(1600,Math.max(900,page.getWidth()*2));int h=Math.min(2200,Math.max(1200,page.getHeight()*2));Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);page.render(b,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close();return b;}finally{renderer.close();}}}

    private String normalizeOcrIdentity(String s){
        return (s==null?"":s).toUpperCase(Locale.ROOT)
            .replace("NACIMlENTO","NACIMIENTO")
            .replace("NACIMlENT0","NACIMIENTO")
            .replace("N0MBRE","NOMBRE")
            .replace("APELLlDOS","APELLIDOS")
            .replace("VALlDEZ","VALIDEZ");
    }
    private boolean validDniLetter(String v){
        if(v==null||!v.matches("\\d{8}[A-Z]"))return false;
        String t="TRWAGMYFPDXBNJZSQVHLCKE";
        try{return t.charAt(Integer.parseInt(v.substring(0,8))%23)==v.charAt(8);}catch(Exception e){return false;}
    }
    private String extractDniRobust(String text){
        String u=normalizeOcrIdentity(text).replaceAll("[^A-Z0-9<]"," ");
        Matcher m=Pattern.compile("(?<![0-9])([0-9]{8}[A-Z])(?![A-Z0-9])").matcher(u);
        while(m.find())if(validDniLetter(m.group(1)))return m.group(1);
        m=Pattern.compile("(?<![0-9])([0-9]{4})\\s*([0-9]{4})\\s*([A-Z])(?![A-Z0-9])").matcher(u);
        while(m.find()){String v=m.group(1)+m.group(2)+m.group(3);if(validDniLetter(v))return v;}
        m=Pattern.compile("IDESP[A-Z0-9<]*?([0-9]{8}[A-Z])").matcher(u);
        while(m.find())if(validDniLetter(m.group(1)))return m.group(1);
        return "";
    }
    private String extractBirthDateRobust(String text){
        String u=normalizeOcrIdentity(text);
        Matcher m=Pattern.compile("(?:NACIMIENTO|FECHA\\s+DE\\s+NACIMIENTO)[^0-9]{0,30}(\\d{2})\\s*[./ -]\\s*(\\d{2})\\s*[./ -]\\s*(\\d{4})").matcher(u);
        if(m.find())return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
        m=Pattern.compile("(\\d{6})[0-9][MF<](\\d{6})[0-9]").matcher(u.replace(" ",""));
        if(m.find()){String d=m.group(1);int yy=Integer.parseInt(d.substring(0,2));int year=yy>=30?1900+yy:2000+yy;return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(d.substring(4,6)),Integer.parseInt(d.substring(2,4)),year);}
        return "";
    }
    private String extractNameRobust(String text){
        String u=normalizeOcrIdentity(text);
        Matcher m=Pattern.compile("(?m)^APELLIDOS?\\s*[:.-]?\\s*(.+?)(?=\\n(?:NOMBRE|SEXO|NACIONALIDAD|NACIMIENTO|DNI|NIE|DOCUMENTO|IDESP)|\\s+(?:DNI|NIE|DOCUMENTO|IDESP)\\b|$)").matcher(u);
        String sur=m.find()?m.group(1).trim():"";
        m=Pattern.compile("(?m)^NOMBRES?\\s*[:.-]?\\s*(.+?)(?=\\n(?:SEXO|NACIONALIDAD|NACIMIENTO|DNI|NIE|DOCUMENTO|IDESP)|\\s+(?:DNI|NIE|DOCUMENTO|IDESP)\\b|$)").matcher(u);
        String nam=m.find()?m.group(1).trim():"";
        m=Pattern.compile("([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)+)<<([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)+)").matcher(u.replace(" ",""));
        if(m.find()){if(sur.isEmpty())sur=m.group(1).replace('<',' ').trim();if(nam.isEmpty())nam=m.group(2).replace('<',' ').trim();}
        return (nam+" "+sur).trim().replaceAll("\\s+"," ");
    }
    private JSONObject parseEssentialRobust(String text){
        JSONObject x=parseEssential(text);
        try{
            String dni=extractDniRobust(text);if(!dni.isEmpty())x.put("identityNumber",dni);
            String birth=extractBirthDateRobust(text);if(!birth.isEmpty())x.put("birthDate",birth);
            String full=extractNameRobust(text);if(!full.isEmpty())x.put("fullName",full);
            int c=0;if(!dni.isEmpty())c+=45;if(!birth.isEmpty())c+=35;if(!full.isEmpty())c+=20;x.put("confidence",Math.max(x.optInt("confidence",0),c));
        }catch(Exception ignored){}
        return x;
    }

    private void reviewDniPair(){
        LinearLayout wrap=col();wrap.setPadding(dp(8),dp(4),dp(8),dp(4));
        wrap.addView(tv("COMPRUEBA ANVERSO Y REVERSO ANTES DEL OCR",16,BLUE,true));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        ImageView f=new ImageView(this),b=new ImageView(this);f.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        f.setBackground(box(Color.WHITE,12));b.setBackground(box(Color.WHITE,12));if(frontBitmap!=null)f.setImageBitmap(frontBitmap);if(backBitmap!=null)b.setImageBitmap(backBitmap);
        row.addView(f,new LinearLayout.LayoutParams(0,dp(210),1));row.addView(b,new LinearLayout.LayoutParams(0,dp(210),1));wrap.addView(row);
        wrap.addView(tv((frontBitmap!=null?"✓ Anverso":"✗ Falta anverso")+"     "+(backBitmap!=null?"✓ Reverso":"✗ Falta reverso"),15,TEXT,true));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Revisión del DNI/NIE").setView(wrap).setNegativeButton("RECHAZAR / CAMBIAR",null).setPositiveButton("PROCESAR OCR",null).create();
        dlg.setOnShowListener(v->{Button ok=dlg.getButton(AlertDialog.BUTTON_POSITIVE);ok.setOnClickListener(x->{if(frontBitmap==null||backBitmap==null){Toast.makeText(this,"Un DNI/NIE necesita ANVERSO y REVERSO.",Toast.LENGTH_LONG).show();return;}dlg.dismiss();processDniPairOcr();});});dlg.show();
    }

    private void processDniPairOcr(){
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); if(frontBitmap==null||backBitmap==null){r.close();return;}
        r.process(InputImage.fromBitmap(frontBitmap,0)).addOnSuccessListener(f->{
            frontText=f==null?"":f.getText();
            r.process(InputImage.fromBitmap(backBitmap,0)).addOnSuccessListener(b->{
                backText=b==null?"":b.getText();
                DocumentType first=DocumentClassifier.classify(frontText).type,second=DocumentClassifier.classify(backText).type;
                if(first==DocumentType.DNI_BACK&&second==DocumentType.DNI_FRONT){
                    String text=frontText;frontText=backText;backText=text;
                    Bitmap image=frontBitmap;frontBitmap=backBitmap;backBitmap=image;
                    String path=frontImagePath;frontImagePath=backImagePath;backImagePath=path;
                }
                r.close();
                if(DocumentClassifier.classify(frontText).type!=DocumentType.DNI_FRONT||DocumentClassifier.classify(backText).type!=DocumentType.DNI_BACK){Toast.makeText(this,"No se han reconocido las dos caras del DNI/NIE. Comprueba el orden y la nitidez.",Toast.LENGTH_LONG).show();return;}
                dniPairConfirmed=true;awaitingDniBack=false;currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;
                showIdentityReview(parseEssentialRobust(frontText+"\n"+backText));
            }).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR reverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});
        }).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR anverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }





























    private void processCurrentDocument(){
        if(documentUri==null||documentKind==0){Toast.makeText(this,"Primero selecciona un documento.",Toast.LENGTH_LONG).show();return;}
        if(processOcrButton!=null){processOcrButton.setEnabled(false);processOcrButton.setText("Procesando documento…");}
        if(documentKind==2){
            try{
                PdfOcrHelper.process(this,documentUri,new PdfOcrHelper.Callback(){
                    public void onProgress(int completed,int total){runOnUiThread(()->{if(processOcrButton!=null){processOcrButton.setEnabled(false);processOcrButton.setText("Leyendo página "+completed+" de "+total+"…");}});}
                    public void onSuccess(String text){runOnUiThread(()->{restoreOcrButton();routeRecognizedDocument(text);});}
                    public void onError(Exception e){runOnUiThread(()->{restoreOcrButton();Toast.makeText(MainActivityV2.this,"PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();});}
                });
            }catch(Exception e){restoreOcrButton();Toast.makeText(this,"No se pudo iniciar el OCR del PDF: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }else processImage();
    }

    private void restoreOcrButton(){if(processOcrButton!=null){processOcrButton.setEnabled(true);processOcrButton.setText("▶ PROCESAR OCR");}}

    /** Clasifica el texto antes de abrir el formulario correcto; nunca guarda a ciegas. */
    private void routeRecognizedDocument(String text){
        if(!authenticated)return;
        String raw=text==null?"":text.trim();
        DocumentClassifier.Result result=DocumentClassifier.classify(raw);
        if(documentKind==2&&(result.type==DocumentType.DNI_FRONT||result.type==DocumentType.DNI_BACK)){
            if(!pdfContainsBothDniSides(raw)){showMissingDniSide("El PDF debe incluir el anverso y el reverso del DNI/NIE.");return;}
            frontText=raw;backText=raw;dniPairConfirmed=true;showIdentityReview(parseEssentialRobust(raw));return;
        }
        if(awaitingDniBack){
            if(result.type!=DocumentType.DNI_BACK){Toast.makeText(this,"No se ha reconocido el reverso. Carga el reverso del DNI/NIE para continuar.",Toast.LENGTH_LONG).show();return;}
            backText=raw;backBitmap=currentBitmap;backImagePath=currentImagePath;awaitingDniBack=false;dniPairConfirmed=true;
            showIdentityReview(parseEssentialRobust(frontText+"\n"+backText));return;
        }
        if(result.type==DocumentType.POLICY){
            showPolicyReview(OcasoPolicyParser.parse(raw),raw);
        }else if(result.type==DocumentType.DNI_FRONT){
            frontText=raw;frontBitmap=currentBitmap;frontImagePath=currentImagePath;awaitingDniBack=true;side=2;
            ocrPage();promptForDniSide("reverso",2);
        }else if(result.type==DocumentType.DNI_BACK){
            awaitingDniBack=false;side=1;ocrPage();promptForDniSide("anverso",1);
        }else{
            String reason=result.type==DocumentType.RECEIPT?"El documento parece un recibo, no una póliza.":"No se ha podido distinguir con seguridad entre DNI/NIE y póliza.";
            new AlertDialog.Builder(this).setTitle("Documento no identificado")
                .setMessage(reason+"\n\nPrueba con una imagen más nítida o selecciona el anverso y reverso del DNI/NIE.")
                .setPositiveButton("Volver al OCR",(d,w)->ocrPage()).setNegativeButton("Cerrar",null).show();
        }
    }

    private void promptForDniSide(String sideName,int sideToCapture){
        new AlertDialog.Builder(this).setTitle("DNI/NIE: falta el "+sideName)
            .setMessage("He leído la primera cara. Ahora fotografía o adjunta la cara que falta para comprobar las dos antes de crear el cliente.")
            .setPositiveButton("Fotografiar "+sideName,(d,w)->{side=sideToCapture;takePhoto();})
            .setNeutralButton("Elegir imagen",(d,w)->{side=sideToCapture;chooseImage();})
            .setNegativeButton("Más tarde",null).show();
    }

    private boolean pdfContainsBothDniSides(String text){
        String[] pages=text.split("(?m)---\\s*[Pp][áa]gina\\s+\\d+\\s*---");boolean front=false,back=false;
        for(String page:pages){
            String upper=page.toUpperCase(Locale.ROOT);DocumentType type=DocumentClassifier.classify(page).type;
            boolean frontMarkers=upper.contains("APELLIDOS")&&upper.contains("NOMBRE")&&(upper.contains("DOCUMENTO NACIONAL")||Pattern.compile("\\b[0-9]{8}[A-Z]\\b").matcher(upper).find());
            boolean backMarkers=upper.contains("IDESP")||upper.contains("<<<<")||upper.contains("LUGAR DE NACIMIENTO");
            front|=type==DocumentType.DNI_FRONT||frontMarkers;back|=type==DocumentType.DNI_BACK||backMarkers;
        }
        return front&&back;
    }

    private void showMissingDniSide(String message){new AlertDialog.Builder(this).setTitle("DNI/NIE incompleto").setMessage(message+"\n\nNo se guardará el cliente hasta leer las dos caras.").setPositiveButton("Volver al OCR",(d,w)->ocrPage()).setNegativeButton("Cerrar",null).show();}
    private void resetDniIntake(){frontBitmap=null;backBitmap=null;frontText="";backText="";frontImagePath="";backImagePath="";awaitingDniBack=false;dniPairConfirmed=false;}
    private void processImage(){
        if(currentBitmap==null){restoreOcrButton();Toast.makeText(this,"Primero selecciona un JPEG válido.",Toast.LENGTH_LONG).show();return;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);r.process(InputImage.fromBitmap(currentBitmap,0)).addOnSuccessListener(t->{String text=t.getText()==null?"":t.getText();r.close();restoreOcrButton();routeRecognizedDocument(text);}).addOnFailureListener(e->{r.close();restoreOcrButton();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }





























    private void showIdentityReview(JSONObject x){if(!authenticated)return;shell("Revisión DNI/NIE","Comprueba los datos antes de guardar");body.addView(tv("2 · DOCUMENTO LEÍDO",18,BLUE,true));if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);body.addView(iv,new LinearLayout.LayoutParams(-1,dp(300)));}body.addView(tv("3 · DATOS DETECTADOS",18,BLUE,true));fullNameE=input("Nombre y apellidos");dniE=input("DNI/NIE");birthE=input("Fecha de nacimiento");addressE=input("Dirección");phoneE=input("Teléfono (lo puedes añadir tú)");emailE=input("Email (lo puedes añadir tú)");fullNameE.setText(x.optString("fullName",""));dniE.setText(x.optString("identityNumber",""));birthE.setText(x.optString("birthDate",""));addressE.setText(x.optString("address",""));confidenceTv=tv("Confianza de lectura: "+x.optInt("confidence",0)+"% · Comprueba visualmente el documento.",14,MUTED,false);body.addView(confidenceTv);for(EditText e:new EditText[]{fullNameE,dniE,birthE,addressE,phoneE,emailE})body.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));Button accept=btn("✅ ACEPTAR DATOS Y GUARDAR",true),reject=btn("❌ RECHAZAR / VOLVER A DOCUMENTO",false);body.addView(accept,new LinearLayout.LayoutParams(-1,dp(62)));body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));accept.setOnClickListener(v->saveIdentity());reject.setOnClickListener(v->ocrPage());}

    private void saveIdentity(){
        try{
            String full=fullNameE.getText().toString().trim(),id=dniE.getText().toString().trim().toUpperCase(Locale.ROOT),birth=birthE.getText().toString().trim();
            if(!dniPairConfirmed||frontText.trim().isEmpty()||backText.trim().isEmpty()){Toast.makeText(this,"Para guardar el cliente hacen falta el anverso y el reverso del DNI/NIE.",Toast.LENGTH_LONG).show();return;}
            if(!isValidIdentity(id)){Toast.makeText(this,"DNI/NIE no válido. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            JSONObject x=findClientById(id);if(x==null)x=new JSONObject();
            if(full.isEmpty())full=clientKey(x).equals("Sin nombre")?"":clientKey(x);
            if(full.isEmpty()){Toast.makeText(this,"Falta el nombre del cliente. Completa el campo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            if(!validDate(birth)){String existingBirth=x.optString("birthDate","").trim();if(validDate(existingBirth))birth=existingBirth;else{Toast.makeText(this,"Fecha de nacimiento no válida. Revísala antes de guardar.",Toast.LENGTH_LONG).show();return;}}
            x.put("holder",full);x.put("name",full);x.put("surname","");x.put("identityNumber",id);x.put("birthDate",birth);
            String address=addressE.getText().toString().trim(),phone=phoneE.getText().toString().trim();if(!address.isEmpty())x.put("address",address);if(!phone.isEmpty())x.put("phone",phone);String email=emailE.getText().toString().trim();if(!email.isEmpty())x.put("email",email);
            if(!x.has("policies"))x.put("policies",new JSONArray());
            if(frontImagePath!=null&&!frontImagePath.isEmpty()){
                try{String p=copyDocumentToArchive(Uri.parse(frontImagePath),"dni_front","jpg");addArchivedDocument(x,p,frontImagePath,"DNI/NIE · anverso","image","");}catch(Exception ignored){}
            }
            if(backImagePath!=null&&!backImagePath.isEmpty()){
                try{String p=copyDocumentToArchive(Uri.parse(backImagePath),"dni_back","jpg");addArchivedDocument(x,p,backImagePath,"DNI/NIE · reverso","image","");}catch(Exception ignored){}
            }
            if(documentKind==2&&documentUri!=null){
                try{String source=documentUri.toString();String p=copyDocumentToArchive(documentUri,"dni_document","pdf");addArchivedDocument(x,p,source,"DNI/NIE · PDF","pdf","");}catch(Exception e){Toast.makeText(this,"Aviso: no se pudo archivar el PDF de identidad.",Toast.LENGTH_LONG).show();}
            }
            if(!upsertClient(x))return;Toast.makeText(this,"Cliente guardado con sus documentos.",Toast.LENGTH_LONG).show();detail(x);
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }


















    private void showPolicyReview(JSONObject p,String raw){
        if(!authenticated)return;
        boolean proposal="PROPUESTA".equalsIgnoreCase(p.optString("documentStatus",""));
        shell(proposal?"Revisión de propuesta":"Revisión de póliza",proposal?"Comprueba antes de asociarla como póliza formalizada":"Comprueba los datos del tomador antes de guardar");
        body.addView(tv("DATOS DEL CLIENTE",18,BLUE,true));
        int confidence=p.optInt("confidence",p.optInt("ocrConfidence",0));
        body.addView(tv("Confianza de lectura: "+confidence+"%",14,confidence>=85?GREEN:(confidence>=60?TEXT:Color.rgb(180,80,40)),true));
        String validity=p.optString("proposalValidUntil","");
        body.addView(tv(proposal?"El documento indica que es una propuesta todavía no formalizada."+(validity.isEmpty()?"":" Válida para contratar hasta "+validity+".")+" Revísalo antes de archivarlo.":"Solo se muestran los datos esenciales. El documento completo se archivará en el cliente.",13,proposal?Color.rgb(180,80,40):MUTED,false));
        body.addView(tv("Modalidad detectada: "+p.optString("policyType","Otros"),15,BLUE,true));

        policyNumberE=input(proposal?"Número de referencia":"Número de póliza");
        boolean community="Comunidades".equalsIgnoreCase(p.optString("policyType",""));
        holderE=input(community?"Tomador / razón social":"Nombre y apellidos");
        policyDniE=input(community?"DNI / NIE / CIF":"DNI / NIE");
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

        addPolicyField(proposal?"NÚMERO DE REFERENCIA":"NÚMERO DE PÓLIZA",null,policyNumberE);
        addPolicyField(community?"TOMADOR / RAZÓN SOCIAL":"NOMBRE Y APELLIDOS",null,holderE);
        addPolicyField(community?"DNI / NIE / CIF":"DNI / NIE",null,policyDniE);
        policyBirthE=input("Fecha de nacimiento");
        policyBirthE.setText(p.optString("birthDate",""));
        addPolicyField("FECHA DE NACIMIENTO",null,policyBirthE);
        policyExpiryE=input("Vencimiento dd/MM/aaaa (opcional)");
        policyExpiryE.setText(p.optString("expiry",""));
        if(!proposal)addPolicyField("FECHA DE VENCIMIENTO",null,policyExpiryE);
        addPolicyField("DIRECCIÓN",null,policyAddressE);
        addPolicyField("TELÉFONO",null,policyPhoneE);

        Button accept=btn("✅ ACEPTAR DATOS Y ARCHIVAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);
        body.addView(accept,new LinearLayout.LayoutParams(-1,dp(64))); body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));
        accept.setOnClickListener(v->savePolicy(raw,p.optJSONArray("insured")));
        reject.setOnClickListener(v->ocrPage());
    }


    private void addPolicyField(String label,String value,EditText field){
        body.addView(tv(label,13,MUTED,true));
        if(field!=null) body.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));
        else body.addView(tv(value==null?"":value,16,TEXT,false),new LinearLayout.LayoutParams(-1,dp(54)));
    }


























































































    private void savePolicy(String raw,JSONArray insured){
        try{
            String id=policyDniE.getText().toString().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]",""),number=policyNumberE.getText().toString().trim();
            JSONObject parsed=OcasoPolicyParser.parse(raw);
            if(id.isEmpty()) id=parsed.optString("identityNumber","").trim().toUpperCase(Locale.ROOT);
            String holder=holderE.getText().toString().trim(); if(holder.isEmpty()) holder=parsed.optString("holder","").trim();
            String address=policyAddressE.getText().toString().trim(); if(address.isEmpty()) address=parsed.optString("address","").trim();
            String phone=policyPhoneE.getText().toString().trim(); if(phone.isEmpty()) phone=parsed.optString("phone","").trim();
            String email=policyEmailE.getText().toString().trim(); if(email.isEmpty()) email=parsed.optString("email","").trim();
            String birth=policyBirthE.getText().toString().trim(); if(birth.isEmpty()) birth=parsed.optString("birthDate","").trim();
            String expiry="PROPUESTA".equalsIgnoreCase(parsed.optString("documentStatus",""))?"":policyExpiryE.getText().toString().trim();
            if(expiry.isEmpty()&&policyExpiryE!=null&&!"PROPUESTA".equalsIgnoreCase(parsed.optString("documentStatus","")))expiry=parsed.optString("expiry","").trim();
            if(!expiry.isEmpty()&&!validDate(expiry)){Toast.makeText(this,"La fecha de vencimiento debe tener formato dd/MM/aaaa y ser válida.",Toast.LENGTH_LONG).show();return;}
            JSONObject matched=findClientForPolicy(id,holder,phone,email);
            boolean matchedExactIdentity=matched!=null&&normalizeSearch(id).equals(normalizeSearch(matched.optString("identityNumber",matched.optString("holderDni",""))));
            if(!id.isEmpty()&&!matchedExactIdentity&&hasConflictingClientIdentity(id,phone,email)){Toast.makeText(this,"El DNI/NIE no coincide con la ficha asociada a ese teléfono o email. Revísalo para no mezclar clientes.",Toast.LENGTH_LONG).show();return;}
            if(holder.isEmpty()&&matched!=null)holder=clientKey(matched);
            if(number.isEmpty()) number=parsed.optString("number","").trim();
            if(number.isEmpty()){Toast.makeText(this,"El número de póliza es obligatorio.",Toast.LENGTH_LONG).show();return;}
            if(holder.isEmpty()){Toast.makeText(this,"No se ha podido identificar al tomador. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            if(!id.isEmpty()&&!isValidTaxIdentity(id)){Toast.makeText(this,"DNI/NIE/CIF no válido. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}
            String product=parsed.optString("policyType","").trim();if(product.isEmpty()||"Otros".equalsIgnoreCase(product))product=currentPolicyProduct(raw);
            boolean cifIdentity=isValidCif(id);
            JSONObject c=matched;
            if(c==null&&id.isEmpty()&&hasAmbiguousNameMatches(holder)){Toast.makeText(this,"Hay varios clientes con ese nombre. Añade o revisa el DNI/NIE/CIF, teléfono o email para asociar la póliza sin riesgo de duplicar o mezclar fichas.",Toast.LENGTH_LONG).show();return;}
            boolean newClient=c==null;
            if(c==null)c=new JSONObject();
            if(!c.has("documentPhotos")) c.put("documentPhotos",new JSONArray());
            JSONArray clientDocs=c.optJSONArray("documentPhotos");
            String archivedUri=documentUri==null?"":documentUri.toString();
            if(!archivedUri.isEmpty()) {
                boolean already=false;
                for(int di=0;di<clientDocs.length();di++){Object it=clientDocs.opt(di);String path=it instanceof JSONObject?((JSONObject)it).optString("path",""):String.valueOf(it);if(archivedUri.equals(path)){already=true;break;}}
                if(!already){JSONObject d=new JSONObject();d.put("path",archivedUri);d.put("kind","policy");d.put("createdAt",System.currentTimeMillis());clientDocs.put(d);}
            }
            c.put("documentPhotos",clientDocs);
            c.put("holder",holder);c.put("name",holder);c.put("surname","");
            if(!id.isEmpty()){c.put("identityNumber",id);c.put("identityType",cifIdentity?"CIF":id.matches("[XYZ].*")?"NIE":"DNI");if(cifIdentity)c.put("cif",id);} if(!address.isEmpty())c.put("address",address); if(!phone.isEmpty())c.put("phone",phone); if(!email.isEmpty())c.put("email",email);
            if(!birth.isEmpty()&&c.optString("birthDate","").trim().isEmpty())c.put("birthDate",birth);
            JSONArray ps=c.optJSONArray("policies");if(ps==null)ps=new JSONArray();
            JSONObject pol=new JSONObject();
            pol.put("id",UUID.randomUUID().toString());pol.put("type","OCASO");pol.put("policyType",product);pol.put("documentStatus",parsed.optString("documentStatus","POLIZA"));pol.put("number",number);pol.put("issueDate",parsed.optString("issueDate",""));pol.put("expiry",expiry);pol.put("validityDate",expiry);pol.put("proposalValidUntil",parsed.optString("proposalValidUntil",""));pol.put("holder",holder);pol.put("identityNumber",id);pol.put("identityType",cifIdentity?"CIF":id.matches("[XYZ].*")?"NIE":id.isEmpty()?"":"DNI");pol.put("cif",cifIdentity?id:"");pol.put("birthDate",birth);pol.put("address",address);pol.put("phone",phone);pol.put("email",email);pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());
            if("Decesos".equalsIgnoreCase(product)){pol.put("decesos",decesosE.getText().toString().trim());pol.put("decesosLevelada",decesosLeveladaE.getText().toString().trim());}else{pol.put("decesos","");pol.put("decesosLevelada","");}
            pol.put("insured",insured==null?new JSONArray():insured);pol.put("documentUri",documentUri==null?"":documentUri.toString());pol.put("ocrText",raw);pol.put("ocrConfidence",parsed.optInt("confidence",0));pol.put("ocrWarnings",parsed.optJSONArray("warnings"));
            String localPdf="";
            if(documentUri!=null){
                try{localPdf=copyDocumentToArchive(documentUri,"policy_"+(number.isEmpty()?"document":number),documentKind==2?"pdf":"jpg");}catch(Exception e){Toast.makeText(this,"Aviso: no se pudo archivar el documento localmente.",Toast.LENGTH_LONG).show();}
            }
            if(!localPdf.isEmpty())pol.put("localDocumentPath",localPdf);
            boolean replaced=false;for(int i=0;i<ps.length();i++){JSONObject old=ps.optJSONObject(i);if(old!=null&&!normalizeSearch(number).isEmpty()&&normalizeSearch(number).equals(normalizeSearch(old.optString("number","")))){
                if(localPdf.isEmpty()&&!old.optString("localDocumentPath","").isEmpty())pol.put("localDocumentPath",old.optString("localDocumentPath",""));
                ps.put(i,pol);replaced=true;break;
            }}
            if(!replaced)ps.put(pol);c.put("policies",ps);
            if(!localPdf.isEmpty())addArchivedDocument(c,localPdf,documentUri==null?"":documentUri.toString(),"Póliza OCASO · "+number,documentKind==2?"pdf":"image",number);
            if(!upsertClient(c))return;
            if(!expiry.isEmpty()){
                ExpiryAlarmScheduler.schedulePolicy(this,"local",pol);
                if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8106);
            }
            Toast.makeText(this,newClient?"Nuevo cliente creado y póliza archivada.":"Póliza asociada al cliente y archivada.",Toast.LENGTH_LONG).show();
            detail(c);
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }


    private String currentPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("OCASO COMUNIDADES")||u.contains("SEGURO DE COMUNIDADES")||u.contains("COMUNIDADES"))return "Comunidades";
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("AHORRO")||u.contains("PIAS")||u.contains("RENTA VITALICIA"))return "Ahorro";
        if(u.contains("OCASO VIDA")||u.contains("SEGURO DE VIDA")||u.contains("VIDA"))return "Vida";
        if(u.contains("HOGAR")||u.contains("MULTIRRIESGO HOGAR"))return "Hogar";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("SALUD")||u.contains("ASISTENCIA SANITARIA"))return "Salud";
        if(u.contains("AUTOMOVIL")||u.contains("AUTOMÓVIL")||u.contains("VEHICULO")||u.contains("VEHÍCULO"))return "Auto";
        return "Otros";
    }
































































































    private JSONObject parseEssential(String raw){JSONObject x=new JSONObject();try{String u=normalize(raw);String id=findId(u);String birth=findBirth(u);String name=mrzName(raw);if(name.isEmpty())name=labelValue(u,"NOMBRE","APELLIDOS");String address=labelValue(u,"DOMICILIO","DIRECCIÓN","DIRECCION");x.put("fullName",clean(name));x.put("identityNumber",id);x.put("birthDate",birth);x.put("address",address);int c=0;if(!id.isEmpty())c+=40;if(!name.isEmpty())c+=30;if(!birth.isEmpty())c+=20;if(!address.isEmpty())c+=10;x.put("confidence",c);}catch(Exception ignored){}return x;}
    private String normalize(String s){return (s==null?"":s).toUpperCase(Locale.ROOT).replace('Á','A').replace('É','E').replace('Í','I').replace('Ó','O').replace('Ú','U').replace((char)13,'\n').replaceAll("[ \\t]+"," ");}
    private String labelValue(String u,String... labs){String[] lines=u.split("\\n");for(int i=0;i<lines.length;i++){String l=clean(lines[i]);for(String lab:labs){int p=l.indexOf(lab);if(p>=0){String v=clean(l.substring(p+lab.length()).replaceFirst("^[ :.-]+",""));if(!v.isEmpty())return v;if(i+1<lines.length)return clean(lines[i+1]);}}}return "";}
    private String mrzName(String raw){String s=(raw==null?"":raw).toUpperCase(Locale.ROOT).replaceAll("[^A-ZÁÉÍÓÚÑ<]","");Matcher m=Pattern.compile("([A-ZÑ]+(?:<[A-ZÑ]+)*)<<([A-ZÑ]+(?:<[A-ZÑ]+)*)").matcher(s);if(m.find())return clean(m.group(2).replace('<',' ')+' '+m.group(1).replace('<',' '));return "";}
    private String findId(String u){Matcher m=Pattern.compile("(?<![0-9])([0-9]{8}[A-Z])(?![A-Z0-9])").matcher(u.replaceAll("\\s+",""));while(m.find())if(validDni(m.group(1)))return m.group(1);Matcher n=Pattern.compile("(?<![A-Z0-9])([XYZ][0-9]{7}[A-Z])(?![A-Z0-9])").matcher(u.replaceAll("\\s+",""));return n.find()?n.group(1):"";}
    private boolean validDni(String v){return v.matches("[0-9]{8}[A-Z]")&&"TRWAGMYFPDXBNJZSQVHLCKE".charAt(Integer.parseInt(v.substring(0,8))%23)==v.charAt(8);}
    private boolean isValidIdentity(String v){return v.matches("[0-9]{8}[A-Z]")?validDni(v):v.matches("[XYZ][0-9]{7}[A-Z]");}
    private boolean isValidTaxIdentity(String v){return isValidIdentity(v)||isValidCif(v);}
    private boolean isValidCif(String v){if(v==null||!v.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]"))return false;int sum=0;for(int i=1;i<=7;i++){int n=v.charAt(i)-'0';if(i%2==0)sum+=n;else{int d=n*2;sum+=d/10+d%10;}}int c=(10-sum%10)%10;char digit=(char)('0'+c),letter="JABCDEFGHI".charAt(c),kind=v.charAt(0);boolean letterOnly="PQRSNW".indexOf(kind)>=0,digitOnly="ABEH".indexOf(kind)>=0;return v.charAt(8)==(letterOnly?letter:digitOnly?digit:(v.charAt(8)==digit||v.charAt(8)==letter?v.charAt(8):' '));}
    private String findBirth(String u){Matcher m=Pattern.compile("(?<![0-9])([0-3][0-9])[/.-]([0-1][0-9])[/.-]((?:19|20)[0-9]{2})(?![0-9])").matcher(u);int yearNow=Calendar.getInstance().get(Calendar.YEAR);while(m.find()){String d=m.group(1)+"/"+m.group(2)+"/"+m.group(3);if(validDate(d)&&Integer.parseInt(m.group(3))<=yearNow-10)return d;}return "";}
    private boolean validDate(String s){try{Date d=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).parse(s);return new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(d).equals(s);}catch(Exception e){return false;}}
    private String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}

    private String copyDocumentToArchive(Uri source, String prefix, String extension) throws Exception {
        if (source == null) return "";
        File dir = new File(getFilesDir(), "client_documents");
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
        JSONArray a = clientsData();
        boolean identityProvided=id!=null&&!id.trim().isEmpty();
        if (email != null && !email.trim().isEmpty()) {
            String e = email.trim().toLowerCase(Locale.ROOT);
            JSONObject unique=null;boolean duplicate=false;for (int i = 0; i < a.length(); i++) {JSONObject x = a.optJSONObject(i);if (x != null && e.equals(x.optString("email", "").trim().toLowerCase(Locale.ROOT))&&(!identityProvided||x.optString("identityNumber",x.optString("holderDni","")).trim().isEmpty())){if(unique!=null){duplicate=true;break;}unique=x;}}if(unique!=null&&!duplicate)return unique;
        }
        if (phone != null && !phone.trim().isEmpty()) {
            String p = normalizeSearch(phone);
            JSONObject unique=null;boolean duplicate=false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && p.equals(normalizeSearch(x.optString("phone", "")))&&(!identityProvided||x.optString("identityNumber",x.optString("holderDni","")).trim().isEmpty())) {if(unique!=null){duplicate=true;break;}unique=x;}
            }
            if(unique!=null&&!duplicate)return unique;
        }
        String q=normalizeSearch(holder);JSONObject unique=null;
        if(!q.isEmpty())for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&q.equals(normalizeSearch(clientKey(x)))&&(!identityProvided||x.optString("identityNumber",x.optString("holderDni","")).trim().isEmpty())){if(unique!=null)return null;unique=x;}}
        return unique;
    }
    private boolean hasConflictingClientIdentity(String id,String phone,String email){
        String identity=normalizeSearch(id),normalizedPhone=normalizeSearch(phone),normalizedEmail=email==null?"":email.trim().toLowerCase(Locale.ROOT);JSONArray a=clientsData();
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String oldId=normalizeSearch(x.optString("identityNumber",x.optString("holderDni","")));if(oldId.isEmpty()||oldId.equals(identity))continue;boolean sameEmail=!normalizedEmail.isEmpty()&&normalizedEmail.equals(x.optString("email","").trim().toLowerCase(Locale.ROOT));boolean samePhone=!normalizedPhone.isEmpty()&&normalizedPhone.equals(normalizeSearch(x.optString("phone","")));if(sameEmail||samePhone)return true;}
        return false;
    }
    private boolean hasAmbiguousNameMatches(String holder){
        String q=normalizeSearch(holder);if(q.isEmpty())return false;int matches=0;JSONArray a=clientsData();
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&q.equals(normalizeSearch(clientKey(x)))&&++matches>1)return true;}
        return false;
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

    private JSONObject findClientById(String id){if(id==null||id.trim().isEmpty())return null;String key=normalizeSearch(id);JSONArray a=clientsData();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&(key.equals(normalizeSearch(x.optString("identityNumber",x.optString("holderDni",""))))||key.equals(normalizeSearch(x.optString("cif","")))))return x;}return null;}
    private JSONObject findClientByName(String name){String q=normalizeSearch(name);if(q.isEmpty())return null;JSONArray a=clientsData();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&normalizeSearch(clientKey(x)).equals(q))return x;}return null;}
    private JSONArray clientsData(){
        clientStoreReadFailed=false;
        try{String s=prefs.getString("rgapro_clients","");if(!s.isEmpty())return new JSONArray(s);}
        catch(Exception e){clientStoreReadFailed=true;Toast.makeText(this,"No se pudo abrir la cartera cifrada. No se guardarán cambios para evitar perder datos.",Toast.LENGTH_LONG).show();}
        return new JSONArray();
    }
    private boolean upsertClient(JSONObject x){
        try{
            JSONArray a=clientsData();if(clientStoreReadFailed)return false;
            String id=x.optString("identityNumber",x.optString("holderDni","")).trim();String rawName=x.optString("holder","").trim();
            if(rawName.isEmpty())rawName=(x.optString("name","")+" "+x.optString("surname","")).trim();
            String holder=normalizeSearch(rawName);boolean replaced=false;
            for(int i=0;i<a.length();i++){
                JSONObject old=a.optJSONObject(i);if(old==null)continue;
                String oldId=old.optString("identityNumber",old.optString("holderDni","")).trim();String oldRaw=old.optString("holder","").trim();
                if(oldRaw.isEmpty())oldRaw=(old.optString("name","")+" "+old.optString("surname","")).trim();
                String oldName=normalizeSearch(oldRaw);boolean sameIdentity=!id.isEmpty()&&!oldId.isEmpty()&&id.equalsIgnoreCase(oldId);
                boolean safeNameMatch=!holder.isEmpty()&&holder.equals(oldName)&&(id.isEmpty()||oldId.isEmpty());
                if(sameIdentity||safeNameMatch){a.put(i,x);replaced=true;break;}
            }
            if(!replaced)a.put(x);
            prefs.edit().putString("rgapro_clients",a.toString()).apply();return true;
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar: "+e.getMessage(),Toast.LENGTH_LONG).show();return false;}
    }
    private long daysUntil(String date){try{SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT);f.setLenient(false);Date d=f.parse(date);Calendar day=Calendar.getInstance();day.setTime(d);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);return (day.getTimeInMillis()-today.getTimeInMillis())/86400000L;}catch(Exception e){return Long.MIN_VALUE;}}
}

