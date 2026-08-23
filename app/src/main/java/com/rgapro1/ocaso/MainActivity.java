package com.rgapro1.ocaso;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends FragmentActivity {
    private static final int NAVY=Color.rgb(12,35,67), BLUE=Color.rgb(25,133,224), BG=Color.rgb(247,249,252), TEXT=Color.rgb(28,39,54), MUTED=Color.rgb(103,115,132), LINE=Color.rgb(225,230,237);
    private static final int CAMERA_REQ=7101, IMAGE_REQ=7102, PDF_REQ=7103;
    private SharedPreferences prefs; private String currentUser; private LinearLayout content;
    private File scanFile; private Uri scanUri; private boolean dniMode=false; private int dniSide=0; private boolean multiMode=false;
    private final ArrayList<String> sessionPaths=new ArrayList<>(); private final ArrayList<String> sessionTexts=new ArrayList<>();
    private final Executor biometricExecutor=Executors.newSingleThreadExecutor();
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(b?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);v.setPadding(dp(6),dp(6),dp(6),dp(6));return v;}
    private EditText edit(String h){EditText e=new EditText(this);e.setHint(h);e.setSingleLine(true);e.setTextSize(16);e.setPadding(dp(14),0,dp(14),0);return e;}
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));g.setStroke(dp(1),LINE);return g;}
    private Button action(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(bg(primary?BLUE:Color.WHITE,18));return b;}
    private Button sideButton(String s){Button b=action(s,false);b.setTextSize(14);b.setMinHeight(dp(58));b.setPadding(dp(8),dp(4),dp(8),dp(4));return b;}
    private TextView section(String s){TextView v=tv(s,16,TEXT,true);v.setPadding(dp(4),dp(10),dp(4),dp(6));return v;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(NAVY);prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);if(!prefs.contains("user"))createUser();else showLogin();}
    private void createUser(){LinearLayout l=col();l.setBackgroundColor(BG);l.setPadding(dp(24),dp(20),dp(24),dp(24));l.setGravity(Gravity.CENTER_HORIZONTAL);l.addView(tv("RgaPro",32,NAVY,true));l.addView(tv("Bienvenido · acceso seguro",22,TEXT,true));EditText u=edit("Usuario"),p=edit("Clave de 6 dígitos"),p2=edit("Repite la clave");p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);p2.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);l.addView(u,new LinearLayout.LayoutParams(-1,dp(52)));l.addView(p,new LinearLayout.LayoutParams(-1,dp(52)));l.addView(p2,new LinearLayout.LayoutParams(-1,dp(52)));Button go=action("Crear acceso",true);l.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));go.setOnClickListener(v->{if(u.getText().toString().trim().isEmpty()||!p.getText().toString().matches("\\d{6}")||!p.getText().toString().equals(p2.getText().toString())){Toast.makeText(this,"Usa una clave de 6 dígitos y repítela",Toast.LENGTH_LONG).show();return;}prefs.edit().putString("user",u.getText().toString().trim()).putString("pin",p.getText().toString()).putBoolean("biometric",true).apply();currentUser=u.getText().toString().trim();home();});l.addView(tv("🔒 Los datos se mantienen en el dispositivo.",13,MUTED,false));setContentView(l);}
    private void showLogin(){LinearLayout l=col();l.setBackgroundColor(BG);l.setPadding(dp(24),dp(20),dp(24),dp(24));l.setGravity(Gravity.CENTER_HORIZONTAL);l.addView(tv("RgaPro",32,NAVY,true));l.addView(tv("Acceso a tu cartera",22,TEXT,true));l.addView(tv("Clave + biometría",15,MUTED,false));Button key=action("🔐 Entrar con clave",true),bio=action("● Entrar con huella / biometría",false);l.addView(key,new LinearLayout.LayoutParams(-1,dp(56)));l.addView(bio,new LinearLayout.LayoutParams(-1,dp(56)));key.setOnClickListener(v->login());bio.setOnClickListener(v->biometricLogin());setContentView(l);}
    private void login(){EditText e=edit("Clave de 6 dígitos");e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);AlertDialog d=new AlertDialog.Builder(this).setTitle("Entrar").setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Entrar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{if(prefs.getString("pin","").equals(e.getText().toString())){d.dismiss();currentUser=prefs.getString("user","");home();}else e.setError("Clave incorrecta");}));d.show();}
    private void biometricLogin(){if(!prefs.getBoolean("biometric",true)){Toast.makeText(this,"Biometría desactivada",Toast.LENGTH_SHORT).show();return;}int r=BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);if(r!=BiometricManager.BIOMETRIC_SUCCESS){Toast.makeText(this,"Biometría no disponible. Usa la clave.",Toast.LENGTH_LONG).show();return;}BiometricPrompt p=new BiometricPrompt(this,biometricExecutor,new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){runOnUiThread(()->{currentUser=prefs.getString("user","");home();});}});p.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("RgaPro").setSubtitle("Acceso seguro").setNegativeButtonText("Usar clave").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build());}

    private void home(){
        LinearLayout root=col(); root.setBackgroundColor(BG);
        LinearLayout top=col(); top.setBackgroundColor(NAVY); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(18),dp(10),dp(18),dp(10));
        top.addView(tv("RgaPro",26,Color.WHITE,true)); top.addView(tv("Panel principal · "+currentUser,15,Color.WHITE,false));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(88)));

        LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.HORIZONTAL); main.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout side=col(); side.setPadding(0,0,dp(8),0); side.setBackground(bg(Color.WHITE,16));
        side.addView(tv("MENÚ",13,MUTED,true),new LinearLayout.LayoutParams(-1,dp(38)));
        Button undo=sideButton("↩️  DESHACER\nÚltima acción"); undo.setTextSize(16); undo.setTextColor(NAVY); undo.setOnClickListener(v->undoLastAction()); side.addView(undo,new LinearLayout.LayoutParams(-1,dp(76)));
        Button clients=sideButton("👤  Clientes"); clients.setOnClickListener(v->clients()); side.addView(clients,new LinearLayout.LayoutParams(-1,dp(60)));
        Button policies=sideButton("▣  Pólizas"); policies.setOnClickListener(v->policies()); side.addView(policies,new LinearLayout.LayoutParams(-1,dp(60)));
        Button docs=sideButton("▤  Escanear / OCR"); docs.setOnClickListener(v->scanDocument()); side.addView(docs,new LinearLayout.LayoutParams(-1,dp(60)));
        Button expires=sideButton("🔔  Futuras bajas"); expires.setOnClickListener(v->expiries()); side.addView(expires,new LinearLayout.LayoutParams(-1,dp(60)));
        Button security=sideButton("🔒  Seguridad"); security.setOnClickListener(v->security()); side.addView(security,new LinearLayout.LayoutParams(-1,dp(60)));
        Button logout=sideButton("Salir"); logout.setOnClickListener(v->showLogin()); side.addView(logout,new LinearLayout.LayoutParams(-1,dp(56)));
        main.addView(side,new LinearLayout.LayoutParams(dp(150),-1));

        LinearLayout center=col(); center.setPadding(dp(8),0,0,0);
        ScrollView scroll=new ScrollView(this); LinearLayout centerBody=col();
        centerBody.addView(tv("OCR Y PRÓXIMAS BAJAS",22,TEXT,true));
        TextView ocrTitle=section("Último OCR"); centerBody.addView(ocrTitle);
        TextView ocr=tv(lastOcrText(),14,TEXT,false); ocr.setBackground(bg(Color.WHITE,16)); ocr.setPadding(dp(14),dp(12),dp(14),dp(12)); centerBody.addView(ocr,new LinearLayout.LayoutParams(-1,dp(190)));
        centerBody.addView(section("Futuras bajas / vencimientos"));
        LinearLayout future=col(); populateFutureBajas(future); centerBody.addView(future);
        centerBody.addView(section("Acciones rápidas"));
        Button newScan=action("📷  NUEVO ESCANEO OCR",true); newScan.setTextSize(16); centerBody.addView(newScan,new LinearLayout.LayoutParams(-1,dp(64))); newScan.setOnClickListener(v->scanDocument());
        Button all=action("📋  Ver todas las pólizas",false); centerBody.addView(all,new LinearLayout.LayoutParams(-1,dp(56))); all.setOnClickListener(v->policies());
        scroll.addView(centerBody); center.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); main.addView(center,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(main,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }

    private String lastOcrText(){
        JSONArray a=data(); String best=""; long latest=0;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i); if(p==null)continue; String s=p.optString("ocrText",""); if(!s.isEmpty()){long t=p.optLong("updatedAt",p.optLong("createdAt",i)); if(t>=latest){latest=t;best=s;}}}
        if(best.isEmpty()) return "Todavía no hay OCR guardado. Usa «Escanear / OCR» para capturar un DNI, NIE, PDF o documento.\n\nEl resultado completo aparecerá aquí después de guardar el cliente.";
        return best.length()>1800?best.substring(0,1800)+"…":best;
    }
    private void populateFutureBajas(LinearLayout list){
        JSONArray a=data(); SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT); Date now=new Date(); boolean any=false;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i); if(p==null)continue; try{String ex=p.optString("expiry",p.optString("validityDate")); if(ex.isEmpty())continue; Date d=f.parse(ex); long days=(d.getTime()-now.getTime())/86400000L; if(days>=0&&days<=60){any=true; String label=days<=15?"🔴":days<=30?"🟠":"🟡"; TextView row=tv(label+"  "+p.optString("holder","Sin titular")+" · "+p.optString("type","Póliza")+"\nVence: "+ex+"  ·  "+days+" días",15,TEXT,true); row.setBackground(bg(Color.WHITE,14)); row.setPadding(dp(14),dp(10),dp(14),dp(10)); list.addView(row,new LinearLayout.LayoutParams(-1,dp(72))); } }catch(Exception ignored){}}
        if(!any){TextView empty=tv("No hay futuras bajas en los próximos 60 días.",15,MUTED,false);empty.setBackground(bg(Color.WHITE,14));empty.setPadding(dp(14),dp(12),dp(14),dp(12));list.addView(empty,new LinearLayout.LayoutParams(-1,dp(62)));}
    }
    private void undoLastAction(){
        try{
            JSONArray a=data(); if(a.length()==0){Toast.makeText(this,"No hay ninguna acción que deshacer",Toast.LENGTH_SHORT).show();return;}
            int last=a.length()-1; JSONObject removed=a.optJSONObject(last); a.remove(last); save(a);
            Toast.makeText(this,"↩️ Última acción deshecha: "+(removed==null?"registro":"cliente/póliza"),Toast.LENGTH_LONG).show(); home();
        }catch(Exception e){Toast.makeText(this,"No se pudo deshacer: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void menu(LinearLayout b,String icon,String title,String sub,View.OnClickListener c){LinearLayout x=new LinearLayout(this);x.setGravity(Gravity.CENTER_VERTICAL);x.setPadding(dp(12),dp(8),dp(12),dp(8));x.setBackground(bg(Color.WHITE,18));x.addView(tv(icon,28,BLUE,true),new LinearLayout.LayoutParams(dp(48),dp(68)));LinearLayout w=col();w.addView(tv(title,18,TEXT,true));w.addView(tv(sub,13,MUTED,false));x.addView(w,new LinearLayout.LayoutParams(0,dp(68),1));x.addView(tv("›",30,MUTED,false),new LinearLayout.LayoutParams(dp(28),dp(68)));x.setOnClickListener(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(86));p.bottomMargin=dp(10);b.addView(x,p);}
    private void page(String title,String sub){LinearLayout r=col();r.setBackgroundColor(BG);LinearLayout h=col();h.setBackgroundColor(NAVY);h.setPadding(dp(16),dp(10),dp(16),dp(10));Button back=action("‹",false);back.setTextColor(Color.WHITE);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->home());h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));h.addView(tv(title,20,Color.WHITE,true));h.addView(tv(sub,12,Color.WHITE,false));r.addView(h);content=col();content.setPadding(dp(16),dp(16),dp(16),dp(20));ScrollView s=new ScrollView(this);s.addView(content);r.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);}
    private JSONArray data(){try{return new JSONArray(prefs.getString("policies","[]"));}catch(Exception e){return new JSONArray();}}
    private void save(JSONArray a){prefs.edit().putString("policies",a.toString()).apply();}
    private void clients(){page("Clientes","Busca por cualquier dato");EditText q=edit("Nombre, DNI, NIE, CIF, teléfono, email, póliza…");content.addView(q,new LinearLayout.LayoutParams(-1,dp(54)));LinearLayout list=col();content.addView(list);Runnable r=()->{list.removeAllViews();JSONArray a=data();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&match(p,q.getText().toString())){Button x=action("👤 "+p.optString("holder","Sin titular")+"\n"+p.optString("type","Cliente")+" · "+p.optString("number","")+" · "+p.optString("identityNumber",""),false);x.setOnClickListener(v->detail(p));list.addView(x,new LinearLayout.LayoutParams(-1,dp(72)));}}};q.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){r.run();}public void afterTextChanged(android.text.Editable e){}});r.run();}
    private boolean match(JSONObject p,String q){if(q==null||q.trim().isEmpty())return true;String n=q.toLowerCase(Locale.ROOT);for(String k:new String[]{"holder","name","surname","birthDate","holderDni","identityNumber","identityType","cif","phone","email","address","birthPlace","nationality","sex","parents","supportNumber","issueDate","expiry","type","number","ocrText","members","documentPhotos"})if(p.optString(k).toLowerCase(Locale.ROOT).contains(n))return true;return false;}
    private void detail(JSONObject p){StringBuilder s=new StringBuilder();s.append("Titular: ").append(p.optString("holder","—"));s.append("\nApellidos: ").append(p.optString("surname","—"));s.append("\nNombre: ").append(p.optString("name","—"));s.append("\nIdentificación: ").append(p.optString("identityType","DNI/NIE")).append(" · ").append(p.optString("identityNumber",p.optString("holderDni","—")));s.append("\nCIF empresa: ").append(p.optString("cif","—"));s.append("\nNacimiento: ").append(p.optString("birthDate","—"));s.append("\nNacionalidad: ").append(p.optString("nationality","—"));s.append("\nSexo: ").append(p.optString("sex","—"));s.append("\nDomicilio: ").append(p.optString("address","—"));s.append("\nLugar nacimiento: ").append(p.optString("birthPlace","—"));s.append("\nPadres: ").append(p.optString("parents","—"));s.append("\nNº soporte: ").append(p.optString("supportNumber","—"));s.append("\nEmisión: ").append(p.optString("issueDate","—"));s.append("\nValidez: ").append(p.optString("validityDate",p.optString("expiry","—")));s.append("\nPóliza: ").append(p.optString("number","—"));s.append("\nTipo: ").append(p.optString("type","—"));s.append("\nFotos/documentos: ").append(p.optJSONArray("documentPhotos")!=null?p.optJSONArray("documentPhotos").length():0);new AlertDialog.Builder(this).setTitle("Ficha de cliente").setMessage(s.toString()).setPositiveButton("Cerrar",null).show();}
    private void policies(){page("Pólizas","Separadas por tipo");Spinner sp=new Spinner(this);String[] types={"Todas","Auto","Hogar","Vida","Salud","Decesos","Empresa","Otros"};sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));content.addView(sp,new LinearLayout.LayoutParams(-1,dp(52)));EditText cif=edit("CIF de empresa (opcional para póliza de empresa)");content.addView(cif,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout list=col();content.addView(list);Runnable r=()->{list.removeAllViews();String f=String.valueOf(sp.getSelectedItem());JSONArray a=data();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&(f.equals("Todas")||f.equalsIgnoreCase(p.optString("type","Otros")))){Button x=action(p.optString("type","Otros")+" · "+p.optString("holder","Sin titular")+"\n"+p.optString("number","—")+" · "+p.optString("expiry","—")+(p.optString("cif","").isEmpty()?"":" · CIF "+p.optString("cif")),false);x.setOnClickListener(v->detail(p));list.addView(x,new LinearLayout.LayoutParams(-1,dp(72)));}}};sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> a,View v,int p,long id){cif.setVisibility(p==6?View.VISIBLE:View.GONE);r.run();}public void onNothingSelected(android.widget.AdapterView<?> a){}});cif.setVisibility(View.GONE);r.run();}
    private void expiries(){page("Futuras bajas","60 · 45 · 30 · 15 días");JSONArray a=data();SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT);Date now=new Date();boolean any=false;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);try{String ex=p.optString("expiry",p.optString("validityDate"));long d=(f.parse(ex).getTime()-now.getTime())/(86400000L);if(d>=0&&d<=60){any=true;content.addView(tv("⚠ "+p.optString("holder","Sin titular")+" · "+p.optString("type","Otros")+" · "+d+" días · "+ex,17,TEXT,true),new LinearLayout.LayoutParams(-1,dp(64)));}}catch(Exception ignored){}}if(!any)content.addView(tv("No hay pólizas en los próximos 60 días.",16,MUTED,false));}
    private void security(){page("Seguridad","Tú decides");Switch b=new Switch(this);b.setText("Permitir acceso biométrico");b.setChecked(prefs.getBoolean("biometric",true));content.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));b.setOnCheckedChangeListener((x,v)->prefs.edit().putBoolean("biometric",v).apply());Button test=action("Probar biometría",false);content.addView(test,new LinearLayout.LayoutParams(-1,dp(54)));test.setOnClickListener(v->biometricLogin());content.addView(tv("No se comparte nada automáticamente. Las fotos y datos se guardan localmente.",13,MUTED,false));}
    private void scanDocument(){page("Escanear documento","DNI / NIE doble cara · multipágina · JPEG · PDF");content.addView(tv("DNI o NIE: se captura anverso y después reverso. La MRZ del reverso completa automáticamente los datos. Para pólizas de empresa podrás indicar el CIF.",15,MUTED,false));Button dni=action("🪪 DNI / NIE · anverso + reverso",true);content.addView(dni,new LinearLayout.LayoutParams(-1,dp(56)));dni.setOnClickListener(v->{resetSession();dniMode=true;multiMode=false;openCamera();});Button multi=action("📷 Documento de varias páginas",false);content.addView(multi,new LinearLayout.LayoutParams(-1,dp(56)));multi.setOnClickListener(v->{resetSession();dniMode=false;multiMode=true;openCamera();});Button jpg=action("🖼 JPEG/JPG · una o varias fotos",false);content.addView(jpg,new LinearLayout.LayoutParams(-1,dp(56)));jpg.setOnClickListener(v->{resetSession();pickImages();});Button pdf=action("📄 PDF · todas las páginas",false);content.addView(pdf,new LinearLayout.LayoutParams(-1,dp(56)));pdf.setOnClickListener(v->{resetSession();pickPdf();});}
    private void resetSession(){sessionPaths.clear();sessionTexts.clear();dniSide=0;}
    private void openCamera(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQ);return;}try{File d=new File(getCacheDir(),"scans");if(!d.exists())d.mkdirs();scanFile=File.createTempFile("rgapro_",".jpg",d);scanUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",scanFile);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,scanUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAMERA_REQ);}catch(Exception e){Toast.makeText(this,"No se pudo abrir la cámara",Toast.LENGTH_LONG).show();}}
    private void pickImages(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMAGE_REQ);}
    private void pickPdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PDF_REQ);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;try{if(request==CAMERA_REQ){String path=copyFileToSession(scanFile);if(path==null)throw new IOException("No se pudo guardar la foto");sessionPaths.add(path);if(dniMode){if(dniSide==0){dniSide=1;processLastImage(true);}else processLastImage(false);}else processLastImage(false);}else if(request==IMAGE_REQ&&data!=null){if(data.getClipData()!=null){ClipData c=data.getClipData();for(int i=0;i<c.getItemCount();i++){String p=copyUriToSession(c.getItemAt(i).getUri(),"jpg");if(p!=null)sessionPaths.add(p);}}else if(data.getData()!=null){String p=copyUriToSession(data.getData(),"jpg");if(p!=null)sessionPaths.add(p);}processAllImages(0,new StringBuilder());}else if(request==PDF_REQ&&data!=null){Uri u=data.getData();String pdfPath=copyUriToSession(u,"pdf");if(pdfPath!=null)sessionPaths.add(pdfPath);Toast.makeText(this,"Leyendo PDF…",Toast.LENGTH_SHORT).show();PdfOcrHelper.process(this,u,new PdfOcrHelper.Callback(){public void onSuccess(String text){runOnUiThread(()->{sessionTexts.add(text);showOcrResult(text);});}public void onError(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"No se pudo leer el PDF. Comprueba que no esté protegido.",Toast.LENGTH_LONG).show());}});}}catch(Exception e){Toast.makeText(this,"No se pudo procesar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void processLastImage(boolean askBack){processImageFile(sessionPaths.get(sessionPaths.size()-1),new ImageCallback(){public void ok(String text){sessionTexts.add(text);if(askBack){runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("Anverso leído").setMessage("Ahora fotografía el REVERSO del DNI/NIE. Se guardarán ambas caras en el mismo cliente.").setNegativeButton("Cancelar",(d,w)->resetSession()).setPositiveButton("Fotografiar reverso",(d,w)->openCamera()).show());}else if(dniMode){showOcrResult(joinTexts());}else askMorePages();}public void error(Exception e){runOnUiThread(()->Toast.makeText(MainActivity.this,"No se pudo leer la foto: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}
    private void askMorePages(){new AlertDialog.Builder(this).setTitle("Página procesada").setMessage("Puedes añadir otra foto al mismo documento o terminar y revisar todos los datos detectados.").setNegativeButton("Terminar",(d,w)->showOcrResult(joinTexts())).setPositiveButton("Añadir otra página",(d,w)->openCamera()).show();}
    private String joinTexts(){StringBuilder s=new StringBuilder();for(int i=0;i<sessionTexts.size();i++)s.append("\n--- DOCUMENTO ").append(i+1).append(" ---\n").append(sessionTexts.get(i)).append('\n');return s.toString();}
    private void processAllImages(int index,StringBuilder all){if(index>=sessionPaths.size()){showOcrResult(all.toString());return;}processImageFile(sessionPaths.get(index),new ImageCallback(){public void ok(String text){sessionTexts.add(text);all.append("\n--- IMAGEN ").append(index+1).append(" ---\n").append(text).append('\n');processAllImages(index+1,all);}public void error(Exception e){processAllImages(index+1,all);}});}
    private interface ImageCallback{void ok(String text);void error(Exception e);}
    private void processImageFile(String path,ImageCallback cb){
        new Thread(()->{
            try{
                BitmapFactory.Options o=new BitmapFactory.Options();
                o.inSampleSize=1;
                o.inScaled=false;
                Bitmap bm=BitmapFactory.decodeFile(path,o);
                if(bm==null)throw new IOException("Imagen no válida");
                InputImage image=InputImage.fromBitmap(bm,0);
                TextRecognizer rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                rec.process(image).addOnSuccessListener(r->{String text=r.getText();bm.recycle();rec.close();cb.ok(text);})
                  .addOnFailureListener(e->{bm.recycle();rec.close();cb.error(e);});
            }catch(Exception e){cb.error(e);}
        }).start();
    }

    private String copyFileToSession(File source)throws IOException{File dir=new File(getFilesDir(),"documents");if(!dir.exists())dir.mkdirs();File out=new File(dir,"scan_"+System.currentTimeMillis()+".jpg");copy(source,out);return out.getAbsolutePath();}
    private String copyUriToSession(Uri uri,String ext){try{File dir=new File(getFilesDir(),"documents");if(!dir.exists())dir.mkdirs();File out=new File(dir,"doc_"+System.currentTimeMillis()+"."+ext);InputStream in=getContentResolver().openInputStream(uri);if(in==null)return null;FileOutputStream os=new FileOutputStream(out);byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)os.write(buf,0,n);in.close();os.close();return out.getAbsolutePath();}catch(Exception e){return null;}}
    private void copy(File a,File b)throws IOException{FileInputStream in=new FileInputStream(a);FileOutputStream out=new FileOutputStream(b);byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);in.close();out.close();}
    private static class OcrData{String holder="",surname="",name="",dni="",birthDate="",nationality="",sex="",address="",birthPlace="",parents="",supportNumber="",issueDate="",validityDate="",phone="",email="",identityType="",cif="",number="",documentKind="UNKNOWN",policyType="";int confidence=0;}
    private OcrData parseOcr(String raw){
        OcrData d=new OcrData();
        String text=raw==null?"":raw.replace('\r','\n');
        String norm=normalizeIdentityOcr(text);
        String documentKind=classifyDocumentKind(norm);
        if("POLIZA".equals(documentKind)||"DOCUMENTO".equals(documentKind)) return parsePolicyOcr(norm,documentKind);

        d.documentKind="DNI";
        d.dni=extractValidDni(norm);
        d.identityType=d.dni.matches("[XYZ].*")?"NIE":"DNI";

        String[] lines=norm.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            if(line.matches("^APELLIDOS.*") || line.matches("^APELLIDO.*")){
                String v=valueAfterLabel(line,line.startsWith("APELLIDOS")?"APELLIDOS":"APELLIDO");
                if(v.isEmpty()) v=collectIdentityField(lines,i);
                d.surname=v;
            }else if(line.matches("^NOMBRE.*") || line.matches("^NOMBRES.*")){
                String lab=line.startsWith("NOMBRES")?"NOMBRES":"NOMBRE";
                String v=valueAfterLabel(line,lab);
                if(v.isEmpty()) v=collectIdentityField(lines,i);
                d.name=v;
            }else if(line.startsWith("NACIONALIDAD")){
                String v=valueAfterLabel(line,"NACIONALIDAD"); if(v.isEmpty()&&i+1<lines.length)v=clean(lines[i+1]); d.nationality=v;
            }else if(line.startsWith("SEXO")){
                String v=valueAfterLabel(line,"SEXO"); if(v.isEmpty()&&i+1<lines.length)v=clean(lines[i+1]); d.sex=v;
            }else if(line.contains("DOMICILIO")){
                d.address=valueAfterLabel(line,"DOMICILIO");
            }else if(line.contains("LUGAR DE NACIMIENTO")){
                d.birthPlace=valueAfterLabel(line,"LUGAR DE NACIMIENTO");
            }else if(line.contains("HIJO/A DE")||line.contains("HIJO DE")){
                String lab=line.contains("HIJO/A DE")?"HIJO/A DE":"HIJO DE";d.parents=valueAfterLabel(line,lab);
            }else if(line.contains("NUM SOPORTE")||line.contains("Nº SOPORTE")||line.contains("N° SOPORTE")){
                String lab=line.contains("NUM SOPORTE")?"NUM SOPORTE":line.contains("Nº SOPORTE")?"Nº SOPORTE":"N° SOPORTE";d.supportNumber=valueAfterLabel(line,lab);
            }
        }

        d.birthDate=findDateNearIdentityLabel(lines,"FECHA DE NACIMIENTO","NACIMIENTO");
        d.issueDate=findDateNearIdentityLabel(lines,"EMISION","FECHA DE EMISION","FECHA DE EXPEDICION");
        d.validityDate=findDateNearIdentityLabel(lines,"VALIDEZ","CADUCIDAD","FECHA DE CADUCIDAD");

        // MRZ (cuando se captura el reverso) confirma DNI, apellidos, nombre, nacimiento y validez.
        StringBuilder mrz=new StringBuilder();
        for(String line:lines){String compact=line.replace(" ","").replaceAll("[^A-Z0-9<]","");if(compact.contains("IDESP")||compact.contains("<<"))mrz.append(compact).append('\n');}
        String mrzText=mrz.toString();
        if(!mrzText.isEmpty()){
            Matcher id=Pattern.compile("IDESP(?:C)?(?:ID)?([0-9]{8}[A-Z])").matcher(mrzText);if(id.find() && isValidDniLocal(id.group(1))) d.dni=id.group(1);
            Matcher nm=Pattern.compile("([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)<<([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)").matcher(mrzText);if(nm.find()){d.surname=nm.group(1).replace('<',' ').trim().replaceAll("\\s+"," ");d.name=nm.group(2).replace('<',' ').trim().replaceAll("\\s+"," ");}
            Matcher dates=Pattern.compile("(\\d{6})\\d([MF<])(\\d{6})\\d").matcher(mrzText.replace("<",""));if(dates.find()){d.birthDate=mrzDate(dates.group(1));d.sex=dates.group(2);d.validityDate=mrzDate(dates.group(3));}
        }

        if(d.surname.isEmpty() && d.name.isEmpty()){
            // Fallback for layouts where the OCR drops the labels entirely.
            Matcher f=Pattern.compile("\\b([A-ZÁÉÍÓÚÑ]{3,}(?:\\s+[A-ZÁÉÍÓÚÑ]{3,})?)\\s+([A-ZÁÉÍÓÚÑ]{3,})\\b").matcher(norm);
            if(f.find()){d.surname=f.group(1);d.name=f.group(2);}
        }
        d.holder=(d.name+" "+d.surname).trim();
        d.confidence=essentialConfidence(d);
        return d;
    }

    private String classifyDocumentKind(String u){
        boolean policy=Pattern.compile("\\b(P[ÓO]LIZA|N[ÚU]MERO\\s+DE\\s+P[ÓO]LIZA|N[ÚU]MERO\\s+P[ÓO]LIZA|TOMADOR(?:A)?|CONTRATANTE|ASEGURAD(?:O|A|ORA|OS|AS)|PRIMA|RECIBO|COBERTURA|CAPITAL(?:ES)?\\s+ASEGURADO|CONDICIONES\\s+(?:PARTICULARES|GENERALES)|VENCIMIENTO|EFECTO|FRANQUICIA|BENEFICIARIO(?:S)?|RIESGO|GARANT[ÍI]A(?:S)?)\\b").matcher(u).find();
        int dniLabels=0;
        for(String label:new String[]{"DNI","NOMBRE","APELLIDOS","FECHA DE NACIMIENTO","LUGAR DE NACIMIENTO","NACIONALIDAD","SEXO","SOPORTE","FECHA DE EXPEDICIÓN","VALIDEZ","DOMICILIO","IDESP"}) if(u.contains(label)) dniLabels++;
        boolean dniId=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b").matcher(u).find();
        boolean mrz=u.contains("IDESP") && Pattern.compile("[A-Z0-9<]{20,}").matcher(u).find();
        if(!policy && mrz && dniId) return u.matches(".*\\b[XYZ][0-9]{7}[A-Z]\\b.*")?"NIE":"DNI";
        if(!policy && dniId && dniLabels>=2) return u.matches(".*\\b[XYZ][0-9]{7}[A-Z]\\b.*")?"NIE":"DNI";
        if(policy) return "POLIZA";
        return "DOCUMENTO";
    }

    private OcrData parsePolicyOcr(String norm,String kind){
        OcrData d=new OcrData();d.documentKind=kind;
        Matcher cif=Pattern.compile("\\b[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]\\b").matcher(norm);if(cif.find())d.cif=cif.group();
        d.policyType=classifyPolicyTypeFinal(norm,d.policyType);
        d.number=extractLabel(norm,"N[ÚU]MERO\\s+DE\\s+P[ÓO]LIZA","N[ÚU]MERO\\s+P[ÓO]LIZA","P[ÓO]LIZA");
        d.holder=extractLabel(norm,"TOMADOR(?:A)?","CONTRATANTE","ASEGURADO(?:A)?","CLIENTE");
        d.issueDate=extractDateAfter(norm,"EFECTO","INICIO","FECHA DE EFECTO","FECHA DE INICIO");
        d.validityDate=extractDateAfter(norm,"VENCIMIENTO","FIN","FECHA DE VENCIMIENTO","FECHA DE FIN","CADUCIDAD");
        d.address=extractLabel(norm,"DOMICILIO","DIRECCI[ÓO]N");
        d.phone=extractLabel(norm,"TEL[ÉE]FONO","M[ÓO]VIL");
        d.email=extractLabel(norm,"EMAIL","CORREO ELECTR[ÓO]NICO");
        int found=0;if(!d.number.isEmpty())found+=25;if(!d.holder.isEmpty())found+=25;if(!d.policyType.isEmpty())found+=20;if(!d.issueDate.isEmpty())found+=10;if(!d.validityDate.isEmpty())found+=10;if(!d.cif.isEmpty())found+=10;d.confidence=Math.min(100,found);return d;
    }

    private String extractLabel(String text,String... labels){
        String[] lines=text.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            for(String label:labels){Matcher m=Pattern.compile("^.*?"+label+"\\s*[:#.-]?\\s*(.*)$",Pattern.CASE_INSENSITIVE).matcher(line);if(m.find()){String v=clean(m.group(1));if(!v.isEmpty()&&!v.matches("^[\\-–—]+$"))return v;if(i+1<lines.length){String n=clean(lines[i+1]);if(!n.isEmpty())return n;}}}
        }
        return "";
    }

    private String extractDateAfter(String text,String... labels){
        String[] lines=text.split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            for(String label:labels){if(Pattern.compile("\\b"+label+"\\b",Pattern.CASE_INSENSITIVE).matcher(line).find()){Matcher m=Pattern.compile("\\d{2}[ /.-]\\d{2}[ /.-]\\d{4}").matcher(line);if(m.find())return m.group();if(i+1<lines.length){m=Pattern.compile("\\d{2}[ /.-]\\d{2}[ /.-]\\d{4}").matcher(lines[i+1]);if(m.find())return m.group();}}}
        }
        return "";
    }

    private String classifyPolicyTypeFinal(String raw,String fallback){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Deceso";
        if(u.contains("HOGAR")||u.contains("MULTIRRIESGO HOGAR"))return "Hogar";
        if(u.contains("AUTOMÓVIL")||u.contains("AUTOMOVIL")||u.contains("VEHÍCULO")||u.contains("VEHICULO"))return "Auto";
        if(u.contains("SALUD")||u.contains("ASISTENCIA SANITARIA"))return "Salud";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("AHORRO")||u.contains("PIAS")||u.contains("RENTA"))return "Ahorro";
        if(u.contains("ACCIDENTE")||u.contains("ACCIDENTES"))return "Accidente";
        if(u.contains("COMUNIDAD")||u.contains("COMUNIDADES"))return "Comunidades";
        if(u.contains("RESPONSABILIDAD CIVIL"))return "Responsabilidad civil";
        if(u.contains("EMPRESA")||u.contains("PYME")||u.contains("COMERCIO"))return "Empresa";
        return fallback==null||fallback.isEmpty()?"Otros":fallback;
    }

    private String normalizeIdentityOcr(String s){
        return (s==null?"":s).toUpperCase(Locale.ROOT)
            .replace("APELLlDOS","APELLIDOS")
            .replace("APELLlDO","APELLIDO")
            .replace("NACIMlENTO","NACIMIENTO")
            .replace("NACIMlENT0","NACIMIENTO")
            .replace("VALlDEZ","VALIDEZ")
            .replace("EMlSION","EMISION")
            .replace("N0MBRE","NOMBRE")
            .replace("N0MBRES","NOMBRES");
    }

    private boolean isIdentityLabel(String line){
        String x=clean(line);
        return x.matches("^(APELLIDOS|APELLIDO|NOMBRE|SEXO|NACIONALIDAD|FECHA DE NACIMIENTO|NACIMIENTO|EMISION|VALIDEZ|CADUCIDAD|NUM SOPORTE|Nº SOPORTE|N° SOPORTE|DOMICILIO|LUGAR DE NACIMIENTO|HIJO/A DE|HIJO DE|FIRMA|SIGNATURE).*");
    }

    private String collectIdentityField(String[] lines,int index){
        StringBuilder out=new StringBuilder();
        for(int j=index+1;j<Math.min(lines.length,index+4);j++){
            String v=clean(lines[j]);
            if(v.isEmpty() || isIdentityLabel(v)) break;
            // Ignore obvious non-person numeric/footer noise.
            if(v.matches("^[0-9 /.-]{4,}$")){
                if(out.length()>0) break;
                continue;
            }
            if(v.matches("^(NATIONAL IDENTITY CARD|DOCUMENTO NACIONAL DE IDENTIDAD|REINO DE ESPAÑA|ESPAÑA)$")) break;
            if(out.length()>0) out.append(' ');
            out.append(v);
        }
        return out.toString().replaceAll("\\s+"," ").trim();
    }

    private String findDateNearIdentityLabel(String[] lines,String... labels){
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            for(String label:labels){
                if(line.contains(label)){
                    Matcher m=Pattern.compile("\\b(\\d{2})[ /.-](\\d{2})[ /.-](\\d{4})\\b").matcher(line);
                    if(m.find()) return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
                    for(int j=i+1;j<Math.min(lines.length,i+3);j++){
                        m=Pattern.compile("\\b(\\d{2})[ /.-](\\d{2})[ /.-](\\d{4})\\b").matcher(lines[j]);
                        if(m.find()) return m.group(1)+"/"+m.group(2)+"/"+m.group(3);
                    }
                }
            }
        }
        return "";
    }

    private String extractValidDni(String text){
        String u=normalizeIdentityOcr(text);
        Matcher spaced=Pattern.compile("(?<![0-9])([0-9]{4})\\s*([0-9]{4})\\s*([A-Z])(?![A-Z0-9])").matcher(u);
        while(spaced.find()){
            String candidate=spaced.group(1)+spaced.group(2)+spaced.group(3);
            if(isValidDniLocal(candidate)) return candidate;
        }
        Matcher plain=Pattern.compile("(?<![0-9])([0-9]{8}[A-Z])(?![A-Z0-9])").matcher(u);
        while(plain.find()){
            String candidate=plain.group(1);
            if(isValidDniLocal(candidate)) return candidate;
        }
        Matcher nie=Pattern.compile("(?<![A-Z0-9])([XYZ][0-9]{7}[A-Z])(?![A-Z0-9])").matcher(u);
        while(nie.find()){
            String candidate=nie.group(1);
            if(isValidNieLocal(candidate)) return candidate;
        }
        return "";
    }

    private boolean isValidDniLocal(String value){
        if(value==null || !value.matches("\\d{8}[A-Z]")) return false;
        final String letters="TRWAGMYFPDXBNJZSQVHLCKE";
        try{return letters.charAt(Integer.parseInt(value.substring(0,8))%23)==value.charAt(8);}catch(Exception e){return false;}
    }

    private boolean isValidNieLocal(String value){
        if(value==null || !value.matches("[XYZ]\\d{7}[A-Z]")) return false;
        String numeric=(value.charAt(0)=='X'?"0":value.charAt(0)=='Y'?"1":"2")+value.substring(1,8);
        final String letters="TRWAGMYFPDXBNJZSQVHLCKE";
        try{return letters.charAt(Integer.parseInt(numeric)%23)==value.charAt(8);}catch(Exception e){return false;}
    }

    private int essentialConfidence(OcrData d){
        int score=0;
        if(!d.dni.isEmpty())score+=35;
        if(!d.name.isEmpty())score+=20;
        if(!d.surname.isEmpty())score+=20;
        if(!d.birthDate.isEmpty())score+=20;
        if(!d.validityDate.isEmpty())score+=5;
        return Math.min(100,score);
    }

    private String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}
    private String valueAfterLabel(String s,String label){int p=s.indexOf(label);if(p<0)return "";return s.substring(p+label.length()).replaceFirst("^[ :.-]+","").trim();}
    private String nextValue(String[] lines,int i){return i+1<lines.length?clean(lines[i+1]):"";}
    private String firstDate(String s){Matcher m=Pattern.compile("\\d{2}[ /.-]\\d{2}[ /.-]\\d{4}").matcher(s);return m.find()?m.group():"";}
    private String mrzDate(String yyMMdd){try{int yy=Integer.parseInt(yyMMdd.substring(0,2));int year=yy<=30?2000+yy:1900+yy;return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(yyMMdd.substring(4,6)),Integer.parseInt(yyMMdd.substring(2,4)),year);}catch(Exception e){return "";}}
    private void showOcrResult(String raw){
        OcrData d=parseOcr(raw);
        LinearLayout box=col();box.setPadding(dp(10),dp(4),dp(10),dp(4));
        boolean policy="POLIZA".equals(d.documentKind)||"DOCUMENTO".equals(d.documentKind);
        String aviso=policy?(d.confidence<60?"⚠ Póliza con datos incompletos. Revisa antes de guardar.":"✓ Póliza identificada. Revisa antes de guardar."):(d.confidence<80?"⚠ DNI con datos incompletos. Revisa antes de guardar.":"✓ DNI identificado. Revisa antes de guardar.");
        box.addView(tv(aviso+"  Confianza: "+d.confidence+"%",14,d.confidence<80?Color.rgb(170,95,0):Color.rgb(25,110,70),true));
        box.addView(tv("Solo mostramos los datos esenciales. El resto del documento queda conservado para consulta.",13,MUTED,false));

        Spinner idType=new Spinner(this);String[] idTypes={"DNI","NIE","CIF empresa","Sin identificar"};idType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,idTypes));int selected="NIE".equals(d.identityType)?1:"CIF".equals(d.identityType)?2:0;idType.setSelection(selected);idType.setVisibility(policy?View.GONE:View.VISIBLE);
        box.addView(tv("Tipo de identificación",13,MUTED,false));box.addView(idType,new LinearLayout.LayoutParams(-1,dp(52)));

        EditText holder=edit("Tomador / titular"),surname=edit("Apellidos"),name=edit("Nombre"),dni=edit("DNI / NIE"),cif=edit("CIF de empresa"),birth=edit("Fecha de nacimiento"),nationality=edit("Nacionalidad"),sex=edit("Sexo"),address=edit("Dirección"),birthPlace=edit("Lugar de nacimiento"),parents=edit("Padres"),support=edit("Nº soporte"),issue=edit("Fecha de emisión"),validity=edit("Fecha de vencimiento / caducidad"),phone=edit("Teléfono"),email=edit("Email"),number=edit("Nº de póliza");
        holder.setText(d.holder);surname.setText(d.surname);name.setText(d.name);dni.setText(d.dni);cif.setText(d.cif);birth.setText(d.birthDate);nationality.setText(d.nationality);sex.setText(d.sex);address.setText(d.address);birthPlace.setText(d.birthPlace);parents.setText(d.parents);support.setText(d.supportNumber);issue.setText(d.issueDate);validity.setText(d.validityDate);number.setText(d.number);

        if(policy){
            addField(box,"TIPO DE PRODUCTO",d.policyType,edit("Tipo de producto"));
            EditText prod=lastEditable(box); if(prod!=null) prod.setText(d.policyType); // populated by helper
            box.addView(tv("Nº DE PÓLIZA",13,MUTED,true));box.addView(number,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("TOMADOR",13,MUTED,true));box.addView(holder,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("VENCIMIENTO",13,MUTED,true));box.addView(validity,new LinearLayout.LayoutParams(-1,dp(54)));
        }else{
            box.addView(tv("NOMBRE",13,MUTED,true));box.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("APELLIDOS",13,MUTED,true));box.addView(surname,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("DNI / NIE",13,MUTED,true));box.addView(dni,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("FECHA DE NACIMIENTO",13,MUTED,true));box.addView(birth,new LinearLayout.LayoutParams(-1,dp(54)));
            box.addView(tv("FECHA DE CADUCIDAD",13,MUTED,true));box.addView(validity,new LinearLayout.LayoutParams(-1,dp(54)));
        }

        Button editAll=action("✏️ Editar datos",false),saveBtn=action("💾 Guardar",true);editAll.setTextSize(16);saveBtn.setTextSize(16);box.addView(editAll,new LinearLayout.LayoutParams(-1,dp(56)));box.addView(saveBtn,new LinearLayout.LayoutParams(-1,dp(58)));
        EditText[] editable={holder,surname,name,dni,cif,birth,nationality,sex,address,birthPlace,parents,support,issue,validity,phone,email,number};
        for(EditText e:editable)e.setEnabled(false);
        editAll.setOnClickListener(v->{for(EditText e:editable)e.setEnabled(true);editAll.setEnabled(false);});
        saveBtn.setOnClickListener(v->{String chosenId=policy?(d.cif.isEmpty()?"Sin identificar":"CIF empresa"):String.valueOf(idType.getSelectedItem());String chosenType=policy?(d.policyType.isEmpty()?"Otros":d.policyType):("CIF empresa".equals(chosenId)?"Empresa":"Cliente / "+chosenId);saveClient(holder,surname,name,dni,cif,birth,nationality,sex,address,birthPlace,parents,support,issue,validity,phone,email,number,chosenId,chosenType,raw);});

        ScrollView sc=new ScrollView(this);sc.addView(box);new AlertDialog.Builder(this).setTitle("Datos esenciales detectados").setView(sc).setNegativeButton("Descartar",null).show();
    }

    private final ArrayList<EditText> _lastEditableFields=new ArrayList<>();
    private void addField(LinearLayout box,String label,String value,EditText field){box.addView(tv(label,13,MUTED,true));box.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));_lastEditableFields.add(field);}
    private EditText lastEditable(LinearLayout box){return _lastEditableFields.isEmpty()?null:_lastEditableFields.get(_lastEditableFields.size()-1);}

    private void saveClient(EditText holder,EditText surname,EditText name,EditText dni,EditText cif,EditText birth,EditText nationality,EditText sex,EditText address,EditText birthPlace,EditText parents,EditText support,EditText issue,EditText validity,EditText phone,EditText email,EditText number,String idType,String type,String raw){try{JSONArray a=data();String idNumber=idType.equals("CIF empresa")?cif.getText().toString().trim().toUpperCase(Locale.ROOT):dni.getText().toString().trim().toUpperCase(Locale.ROOT);int idx=-1;if(!idNumber.isEmpty()){for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&idNumber.equalsIgnoreCase(p.optString("identityNumber",p.optString("holderDni","")))){idx=i;break;}}}if(idx<0&&!holder.getText().toString().trim().isEmpty()){for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&holder.getText().toString().trim().equalsIgnoreCase(p.optString("holder",""))){idx=i;break;}}}JSONObject p=idx>=0?a.optJSONObject(idx):new JSONObject();p.put("holder",holder.getText().toString().trim());boolean policyDocument=!type.startsWith("Cliente") && !"DNI".equalsIgnoreCase(idType) && !"NIE".equalsIgnoreCase(idType) && !"CIF empresa".equalsIgnoreCase(idType);p.put("surname",surname.getText().toString().trim());p.put("name",name.getText().toString().trim());p.put("holderDni",dni.getText().toString().trim().toUpperCase(Locale.ROOT));if(policyDocument){p.put("documentKind","POLIZA");p.remove("birthDate");p.remove("nationality");p.remove("sex");p.remove("birthPlace");p.remove("parents");p.remove("supportNumber");p.remove("issueDate");p.remove("validityDate");p.remove("expiry");p.remove("holderDni");p.put("identityType","");p.put("identityNumber","");}p.put("identityType",idType);p.put("identityNumber",idNumber);p.put("cif",cif.getText().toString().trim().toUpperCase(Locale.ROOT));p.put("birthDate",birth.getText().toString().trim());p.put("nationality",nationality.getText().toString().trim());p.put("sex",sex.getText().toString().trim());p.put("address",address.getText().toString().trim());p.put("birthPlace",birthPlace.getText().toString().trim());p.put("parents",parents.getText().toString().trim());p.put("supportNumber",support.getText().toString().trim());p.put("issueDate",issue.getText().toString().trim());p.put("validityDate",validity.getText().toString().trim());p.put("expiry",validity.getText().toString().trim());p.put("phone",phone.getText().toString().trim());p.put("email",email.getText().toString().trim());p.put("number",number.getText().toString().trim());p.put("type",type);p.put("ocrText",raw);p.put("updatedAt",System.currentTimeMillis());if(policyDocument){p.put("documentKind","POLIZA");p.put("identityType","");p.put("identityNumber","");p.remove("holderDni");p.remove("birthDate");p.remove("nationality");p.remove("sex");p.remove("birthPlace");p.remove("parents");p.remove("supportNumber");p.remove("issueDate");p.remove("validityDate");p.remove("expiry");}JSONArray docs=p.optJSONArray("documentPhotos");if(docs==null)docs=new JSONArray();for(int i=0;i<sessionPaths.size();i++){JSONObject doc=new JSONObject();doc.put("path",sessionPaths.get(i));doc.put("side",dniMode?(i==0?"ANVERSO":"REVERSO"):"DOCUMENTO "+(i+1));doc.put("addedAt",System.currentTimeMillis());docs.put(doc);}p.put("documentPhotos",docs);if(idx>=0)a.put(idx,p);else a.put(p);save(a);Toast.makeText(this,"Cliente guardado. "+sessionPaths.size()+" documento(s) asociados.",Toast.LENGTH_LONG).show();home();}catch(Exception e){Toast.makeText(this,"No se pudo guardar el cliente: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
}
