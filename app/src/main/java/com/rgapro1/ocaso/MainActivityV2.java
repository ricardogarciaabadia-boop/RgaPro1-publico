package com.rgapro1.ocaso;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
    private static final int CAMERA=8101, IMAGE=8102, PDF=8103;
    private SharedPreferences prefs; private LinearLayout body; private Uri cameraUri; private int side=0;
    private String frontText="", backText="", currentImagePath=""; private Bitmap currentBitmap;
    private EditText nameE,surnameE,dniE,birthE,expiryE; private TextView confidenceTv, imageHint;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setPadding(dp(6),dp(6),dp(6),dp(6));return t;}
    private GradientDrawable box(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.rgb(222,228,236));return g;}
    private Button btn(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(box(primary?BLUE:Color.WHITE,18));b.setPadding(dp(10),dp(6),dp(10),dp(6));return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(box(Color.WHITE,14));return e;}
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);home();}

    private void shell(String title,String subtitle){
        LinearLayout root=col(); root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(8),dp(12),dp(8));top.setBackgroundColor(NAVY);
        try{ImageView icon=new ImageView(this);icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);top.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));}catch(Exception ignored){}
        LinearLayout tt=col();tt.addView(tv("RgaPro",24,Color.WHITE,true));tt.addView(tv(subtitle,13,Color.LTGRAY,false));top.addView(tt,new LinearLayout.LayoutParams(0,dp(60),1));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(76)));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(NAVY);
        Button h=btn("Inicio",false), c=btn("Clientes",false), o=btn("OCR",false), p=btn("Pólizas",false);
        h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());p.setOnClickListener(v->policies());
        nav.addView(h,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(c,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(o,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(p,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(nav); ScrollView sc=new ScrollView(this);body=col();body.setPadding(dp(16),dp(16),dp(16),dp(24));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void home(){
        shell("Inicio","Clientes, pólizas, documentos y vencimientos");
        body.addView(tv("Inicio",28,TEXT,true));
        body.addView(tv("BÚSQUEDA GLOBAL",14,BLUE,true));
        EditText q=input("Nombre, apellidos, DNI/NIE, teléfono, dirección, email o póliza");
        body.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout results=col();body.addView(results);
        Runnable search=()->{results.removeAllViews();String s=q.getText().toString().trim();if(s.isEmpty())return;JSONArray a=clientsData();ArrayList<JSONObject> hits=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&matches(x,s))hits.add(x);}Collections.sort(hits,(a1,b1)->clientKey(a1).compareToIgnoreCase(clientKey(b1)));if(hits.isEmpty())results.addView(tv("No hay coincidencias.",15,MUTED,false));for(JSONObject x:hits)addClientCard(results,x);};
        q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){search.run();}public void afterTextChanged(Editable e){}});
        Button clients=btn("👤  LISTADO DE CLIENTES (A-Z)",true);clients.setOnClickListener(v->clients());body.addView(clients,new LinearLayout.LayoutParams(-1,dp(60)));
        Button scan=btn("📷  NUEVO ESCANEO / OCR",true);scan.setOnClickListener(v->ocrPage());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(60)));
        Button pdf=btn("📄  SUBIR PDF / DOCUMENTO",false);pdf.setOnClickListener(v->choosePdf());body.addView(pdf,new LinearLayout.LayoutParams(-1,dp(56)));
        Button alarms=btn("🔔  VENCIMIENTOS Y ALARMAS  ·  60 / 40 / 30 / 7 / 1",false);alarms.setOnClickListener(v->expiries());body.addView(alarms,new LinearLayout.LayoutParams(-1,dp(56)));
        body.addView(tv("PRÓXIMOS VENCIMIENTOS · PRIORIDADES",16,TEXT,true));body.addView(priorityPanel());
    }

    private LinearLayout priorityPanel(){
        LinearLayout l=col();JSONArray a=clientsData();int[] days={1,7,30,40,60};boolean any=false;
        for(int target:days){for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long d=daysUntil(x.optString("expiry"));if(d==target){any=true;TextView t=tv("🔔 "+target+" DÍA"+(target==1?"":"S")+" · "+clientKey(x)+" · vence "+x.optString("expiry"),15,TEXT,true);t.setBackground(box(Color.WHITE,14));l.addView(t,new LinearLayout.LayoutParams(-1,dp(58)));}}}
        if(!any)l.addView(tv("No hay vencimientos exactamente en 60, 40, 30, 7 o 1 día.",14,MUTED,false));
        return l;
    }

    private void expiries(){
        shell("Vencimientos y alarmas","Prioridades: 60 · 40 · 30 · 7 · 1 día");
        body.addView(tv("ALARMAS PRIORITARIAS",22,TEXT,true));
        JSONArray a=clientsData();int[] targets={1,7,30,40,60};boolean any=false;
        for(int target:targets){LinearLayout group=col();TextView head=tv("🔔 "+target+" DÍA"+(target==1?"":"S"),16,BLUE,true);group.addView(head);for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long d=daysUntil(x.optString("expiry"));if(d==target){any=true;TextView row=tv(clientKey(x)+System.lineSeparator()+"DNI: "+x.optString("identityNumber","—")+" · Vence: "+x.optString("expiry","—"),15,TEXT,false);row.setBackground(box(Color.WHITE,14));group.addView(row,new LinearLayout.LayoutParams(-1,dp(68)));}}body.addView(group);}
        if(!any)body.addView(tv("No hay vencimientos exactamente en las prioridades indicadas. Se conservan los datos para generar el aviso cuando llegue cada fecha.",15,MUTED,false));
    }

    private void clients(){
        shell("Clientes","Listado alfabético + búsqueda global");
        body.addView(tv("Clientes",26,TEXT,true));
        EditText q=input("Buscar por nombre, apellidos, DNI, teléfono, dirección, email o póliza");
        body.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout list=col();body.addView(list);
        Runnable r=()->{list.removeAllViews();ArrayList<JSONObject> xs=new ArrayList<>();JSONArray a=clientsData();String s=q.getText().toString().trim();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&(s.isEmpty()||matches(x,s)))xs.add(x);}Collections.sort(xs,(x,y)->clientKey(x).compareToIgnoreCase(clientKey(y)));if(xs.isEmpty())list.addView(tv("No hay clientes guardados todavía.",15,MUTED,false));for(JSONObject x:xs)addClientCard(list,x);};
        q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){r.run();}public void afterTextChanged(Editable e){}});r.run();
        Button add=btn("＋  NUEVO CLIENTE",true);add.setOnClickListener(v->editClient(null));body.addView(add,new LinearLayout.LayoutParams(-1,dp(58)));
    }

    private void addClientCard(LinearLayout list,JSONObject x){
        String title=clientKey(x);String id=x.optString("identityNumber","");String sub=(id.isEmpty()?"":id+" · ")+x.optString("phone","");
        Button b=btn("👤  "+title+(sub.trim().isEmpty()?"":System.lineSeparator()+sub),false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setOnClickListener(v->detail(x));list.addView(b,new LinearLayout.LayoutParams(-1,dp(76)));
    }
    private String clientKey(JSONObject x){String s=x.optString("surname","").trim();String n=x.optString("name","").trim();if(s.isEmpty())s=x.optString("holder","Sin nombre");return (s+" "+n).trim();}
    private boolean matches(JSONObject x,String q){String s=q.toLowerCase(Locale.ROOT);for(String k:new String[]{"name","surname","holder","identityNumber","phone","email","address","policyNumber","type","expiry"})if(x.optString(k,"").toLowerCase(Locale.ROOT).contains(s))return true;return false;}

    private void detail(JSONObject x){
        shell("Ficha de cliente",clientKey(x));
        body.addView(tv(clientKey(x),25,TEXT,true));
        LinearLayout personal=col();body.addView(tv("DATOS PERSONALES Y CONTACTO",16,BLUE,true));body.addView(personal);
        addRead(personal,"DNI/NIE",x.optString("identityNumber",""));addRead(personal,"Fecha de nacimiento",x.optString("birthDate",""));addRead(personal,"Teléfono",x.optString("phone",""));addRead(personal,"Dirección",x.optString("address",""));addRead(personal,"Email",x.optString("email",""));
        body.addView(tv("PÓLIZAS",16,BLUE,true));
        JSONArray ps=x.optJSONArray("policies");if(ps==null)ps=new JSONArray();
        for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p==null)continue;Button b=btn("▣  "+p.optString("type","Póliza")+" · "+p.optString("number","Sin número"),false);body.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));final JSONObject fp=p;b.setOnClickListener(v->policyDetail(fp));}
        Button edit=btn("✏️  EDITAR DATOS",true);edit.setOnClickListener(v->editClient(x));body.addView(edit,new LinearLayout.LayoutParams(-1,dp(58)));
    }
    private void addRead(LinearLayout p,String label,String value){TextView t=tv(label+System.lineSeparator()+(value==null||value.isEmpty()?"—":value),15,TEXT,false);t.setBackground(box(Color.WHITE,12));p.addView(t,new LinearLayout.LayoutParams(-1,dp(62)));}

    private void editClient(JSONObject old){
        LinearLayout l=col();EditText n=input("Nombre"),s=input("Apellidos"),d=input("DNI/NIE"),b=input("Fecha nacimiento dd/MM/yyyy"),ph=input("Teléfono"),ad=input("Dirección"),em=input("Email");
        if(old!=null){n.setText(old.optString("name",""));s.setText(old.optString("surname",""));d.setText(old.optString("identityNumber",""));b.setText(old.optString("birthDate",""));ph.setText(old.optString("phone",""));ad.setText(old.optString("address",""));em.setText(old.optString("email",""));}
        for(EditText e:new EditText[]{n,s,d,b,ph,ad,em})l.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));
        new AlertDialog.Builder(this).setTitle(old==null?"Nuevo cliente":"Editar cliente").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(di,w)->{
            try{JSONObject x=old==null?new JSONObject():old;x.put("name",n.getText().toString().trim());x.put("surname",s.getText().toString().trim());x.put("holder",(n.getText().toString()+" "+s.getText().toString()).trim());x.put("identityNumber",d.getText().toString().trim());x.put("birthDate",b.getText().toString().trim());x.put("phone",ph.getText().toString().trim());x.put("address",ad.getText().toString().trim());x.put("email",em.getText().toString().trim());if(!x.has("policies"))x.put("policies",new JSONArray());upsertClient(x);clients();}catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
        }).show();
    }

    private void policyDetail(JSONObject p){
        LinearLayout l=col();l.addView(tv("Número: "+p.optString("number","—"),16,TEXT,false));l.addView(tv("Vencimiento: "+p.optString("expiry","—"),16,TEXT,false));l.addView(tv("Contenido: "+p.optString("content","—"),15,TEXT,false));
        String insured=p.optString("insured","");l.addView(tv("ASEGURADOS",16,BLUE,true));if(insured.isEmpty())l.addView(tv("No hay asegurados asociados.",14,MUTED,false));else for(String n:insured.split("[,;]+")){if(!n.trim().isEmpty()){Button b=btn("👤 "+n.trim(),false);l.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(n.trim()).setMessage("Datos del asegurado disponibles en la ficha del cliente/póliza.").setPositiveButton("Cerrar",null).show());}}
        new AlertDialog.Builder(this).setTitle(p.optString("type","Póliza")).setView(l).setPositiveButton("Cerrar",null).show();
    }

    private void policies(){
        shell("Pólizas","Todas las pólizas guardadas");
        JSONArray a=clientsData();ArrayList<JSONObject> all=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c==null)continue;JSONArray ps=c.optJSONArray("policies");if(ps==null)continue;for(int j=0;j<ps.length();j++){JSONObject p=ps.optJSONObject(j);if(p!=null){try{p.put("_holder",clientKey(c));}catch(Exception ignored){}all.add(p);}}}
        if(all.isEmpty())body.addView(tv("No hay pólizas guardadas. Puedes subir una póliza PDF desde OCR.",15,MUTED,false));
        for(JSONObject p:all){Button b=btn("▣ "+p.optString("type","Póliza")+" · "+p.optString("number","—")+System.lineSeparator()+p.optString("_holder",""),false);b.setOnClickListener(v->policyDetail(p));body.addView(b,new LinearLayout.LayoutParams(-1,dp(72)));}
        Button scan=btn("📄  Subir póliza / PDF",true);scan.setOnClickListener(v->choosePdf());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(58)));
    }

    private void ocrPage(){
        shell("OCR fiable","DNI/NIE, pólizas, documentos y PDF");
        body.addView(tv("DOCUMENTO",16,BLUE,true));imageHint=tv(side==0?"Paso 1: toma el ANVERSO del DNI/NIE.":"Paso 2: toma el REVERSO para confirmar la MRZ.",15,TEXT,true);imageHint.setBackground(box(Color.WHITE,14));body.addView(imageHint);
        ImageView image=new ImageView(this);image.setBackground(box(Color.WHITE,16));image.setScaleType(ImageView.ScaleType.FIT_CENTER);body.addView(image,new LinearLayout.LayoutParams(-1,dp(300)));if(currentBitmap!=null)image.setImageBitmap(currentBitmap);
        LinearLayout row=new LinearLayout(this);Button front=btn("📷 Tomar anverso",true),back=btn("↩️ Tomar reverso",false);row.addView(front,new LinearLayout.LayoutParams(0,dp(58),1));row.addView(back,new LinearLayout.LayoutParams(0,dp(58),1));body.addView(row);front.setOnClickListener(v->{side=1;takePhoto();});back.setOnClickListener(v->{side=2;takePhoto();});
        LinearLayout row2=new LinearLayout(this);Button choose=btn("🖼️ Elegir foto",false),pdf=btn("📄 Elegir PDF",false);row2.addView(choose,new LinearLayout.LayoutParams(0,dp(58),1));row2.addView(pdf,new LinearLayout.LayoutParams(0,dp(58),1));body.addView(row2);choose.setOnClickListener(v->chooseImage());pdf.setOnClickListener(v->choosePdf());
        body.addView(tv("DATOS ESENCIALES DETECTADOS",16,BLUE,true));confidenceTv=tv("Confianza: pendiente",14,MUTED,false);body.addView(confidenceTv);
        nameE=input("Nombre");surnameE=input("Apellidos");dniE=input("DNI / NIE");birthE=input("Fecha de nacimiento");expiryE=input("Fecha de caducidad / validez");for(EditText e:new EditText[]{nameE,surnameE,dniE,birthE,expiryE})body.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));
        Button edit=btn("✏️ Editar datos",false),save=btn("💾 GUARDAR",true);body.addView(edit,new LinearLayout.LayoutParams(-1,dp(56)));body.addView(save,new LinearLayout.LayoutParams(-1,dp(60)));edit.setOnClickListener(v->{for(EditText e:new EditText[]{nameE,surnameE,dniE,birthE,expiryE})e.setEnabled(true);Toast.makeText(this,"Puedes modificar cualquier campo y después pulsar Guardar.",Toast.LENGTH_SHORT).show();});save.setOnClickListener(v->saveOcrAsClient());
    }

    private void takePhoto(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA);return;}
        try{File f=new File(getExternalFilesDir("captures"),"scan_"+System.currentTimeMillis()+".jpg");f.getParentFile().mkdirs();cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAMERA);}catch(Exception e){Toast.makeText(this,"No se pudo abrir la cámara",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==CAMERA&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)takePhoto();}
    private void chooseImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMAGE);}
    private void choosePdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PDF);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;if(request==PDF){PdfOcrHelper.process(this,u,new PdfOcrHelper.Callback(){public void onSuccess(String text){runOnUiThread(()->showPdfResult(text));}public void onError(Exception e){runOnUiThread(()->Toast.makeText(MainActivityV2.this,"PDF: "+e.getMessage(),Toast.LENGTH_LONG).show());}});return;}try{currentBitmap=loadBitmap(u);currentImagePath=u.toString();ocrImage();}catch(Exception e){Toast.makeText(this,"No se pudo leer la imagen",Toast.LENGTH_LONG).show();}}
    private Bitmap loadBitmap(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(b==null)throw new IOException("imagen vacía");return b;}
    private void ocrImage(){TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);r.process(InputImage.fromBitmap(currentBitmap,0)).addOnSuccessListener(t->{String text=t.getText()==null?"":t.getText();if(side==2)backText=text;else frontText=text;fillFields(parseEssential(frontText+" "+backText));r.close();}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});}
    private void showPdfResult(String text){new AlertDialog.Builder(this).setTitle("Texto extraído del PDF").setMessage(text.length()>9000?text.substring(0,9000)+"…":text).setPositiveButton("Guardar documento",(d,w)->{try{JSONObject x=new JSONObject();x.put("name","Documento PDF");x.put("surname","");x.put("holder","Documento PDF");x.put("identityNumber","");x.put("birthDate","");x.put("expiry","");x.put("ocrText",text);x.put("policies",new JSONArray());upsertClient(x);Toast.makeText(this,"PDF guardado para consulta.",Toast.LENGTH_LONG).show();}catch(Exception ignored){}}).setNegativeButton("Cerrar",null).show();}
    private void fillFields(JSONObject x){nameE.setText(x.optString("name",""));surnameE.setText(x.optString("surname",""));dniE.setText(x.optString("identityNumber",""));birthE.setText(x.optString("birthDate",""));expiryE.setText(x.optString("expiry",""));confidenceTv.setText("Confianza estimada: "+x.optInt("confidence",0)+"% · Revisa siempre antes de guardar.");}
    private void saveOcrAsClient(){try{JSONObject x=new JSONObject();x.put("name",nameE.getText().toString().trim());x.put("surname",surnameE.getText().toString().trim());x.put("holder",(nameE.getText().toString()+" "+surnameE.getText().toString()).trim());x.put("identityNumber",dniE.getText().toString().trim());x.put("birthDate",birthE.getText().toString().trim());x.put("expiry",expiryE.getText().toString().trim());x.put("ocrText",frontText+" "+backText);x.put("image",currentImagePath);x.put("policies",new JSONArray());upsertClient(x);Toast.makeText(this,"Cliente guardado correctamente.",Toast.LENGTH_LONG).show();clients();}catch(Exception e){Toast.makeText(this,"No se pudo guardar: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    private JSONObject parseEssential(String raw){
        JSONObject x=new JSONObject();try{
            String u=normalize(raw);String[] lines=u.split(String.valueOf((char)10));String surname=multiLabel(lines);String name=singleLabel(lines,"NOMBRE");if(name.isEmpty())name=mrzName(raw,true);if(surname.isEmpty())surname=mrzName(raw,false);String dni=findId(u);String birth=findBirth(u);String exp=findAfter(u,"VALIDEZ","CADUCIDAD","VÁLIDEZ");if(exp.isEmpty())exp=mrzDates(raw)[1];if(birth.isEmpty())birth=mrzDates(raw)[0];if(!mrzName(raw,false).isEmpty())surname=mrzName(raw,false);if(!mrzName(raw,true).isEmpty())name=mrzName(raw,true);if(dni.isEmpty())dni=findId(raw.toUpperCase(Locale.ROOT).replaceAll("\\s+",""));
            x.put("name",clean(name));x.put("surname",clean(surname));x.put("identityNumber",dni);x.put("birthDate",birth);x.put("expiry",exp);int c=0;if(!dni.isEmpty())c+=25;if(!name.isEmpty())c+=20;if(!surname.isEmpty())c+=25;if(!birth.isEmpty())c+=15;if(!exp.isEmpty())c+=15;x.put("confidence",c);
        }catch(Exception ignored){}return x;
    }
    private String normalize(String s){return s.toUpperCase(Locale.ROOT).replace((char)13,(char)10).replace("APELLlDOS","APELLIDOS").replace("NACIMlENTO","NACIMIENTO").replace("VALlDEZ","VALIDEZ").replaceAll("[ \\t]+"," ");}
    private String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}
    private boolean label(String line,String... labs){for(String l:labs)if(line.startsWith(l)||line.contains(l))return true;return false;}
    private String singleLabel(String[] lines,String lab){for(int i=0;i<lines.length;i++){String l=clean(lines[i]);int p=l.indexOf(lab);if(p>=0){String v=clean(l.substring(p+lab.length()).replaceFirst("^[ :.-]+",""));if(!v.isEmpty()&&!label(v,"APELLIDOS","SEXO","NACIONALIDAD"))return v;if(i+1<lines.length){v=clean(lines[i+1]);if(!label(v,"APELLIDOS","SEXO","NACIONALIDAD","EMISION","VALIDEZ","NACIMIENTO","NUM SOPORTE"))return v;}}}return "";}
    private String multiLabel(String[] lines){for(int i=0;i<lines.length;i++){String l=clean(lines[i]);int p=l.indexOf("APELLIDOS");if(p<0)continue;StringBuilder out=new StringBuilder();String first=clean(l.substring(p+9).replaceFirst("^[ :.-]+",""));if(!first.isEmpty())out.append(first);for(int j=i+1;j<Math.min(lines.length,i+4);j++){String n=clean(lines[j]);if(n.isEmpty())continue;if(label(n,"NOMBRE","SEXO","NACIONALIDAD","EMISION","VALIDEZ","NACIMIENTO","NUM SOPORTE","DOMICILIO","LUGAR DE NACIMIENTO","HIJO/A DE"))break;if(out.length()>0)out.append(' ');out.append(n);}return clean(out.toString());}return "";}
    private String findId(String u){Matcher m=Pattern.compile("(?<!\\d)(\\d{8}[A-Z])(?![A-Z0-9])").matcher(u);while(m.find()){String v=m.group(1);if(validDni(v))return v;}Matcher n=Pattern.compile("(?<![A-Z0-9])([XYZ]\\d{7}[A-Z])(?![A-Z0-9])").matcher(u);while(n.find())return n.group(1);return "";}
    private boolean validDni(String v){if(!v.matches("\\d{8}[A-Z]"))return false;return "TRWAGMYFPDXBNJZSQVHLCKE".charAt(Integer.parseInt(v.substring(0,8))%23)==v.charAt(8);}
    private String findBirth(String u){List<String> ds=dates(u);String ex=findAfter(u,"EMISION","EMISIÓN"),va=findAfter(u,"VALIDEZ","CADUCIDAD");for(String d:ds)if(!d.equals(ex)&&!d.equals(va)){try{Date dt=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).parse(d);Calendar c=Calendar.getInstance();c.setTime(dt);int y=c.get(Calendar.YEAR);if(y>=1900&&y<=Calendar.getInstance().get(Calendar.YEAR)-10)return d;}catch(Exception ignored){}}return ds.isEmpty()?"":ds.get(0);}
    private String findAfter(String u,String... labs){for(String lab:labs){int p=u.indexOf(lab);if(p>=0){String tail=u.substring(p,Math.min(u.length(),p+220));Matcher m=Pattern.compile("(\\d{2})\\s*[./-]\\s*(\\d{2})\\s*[./-]\\s*(\\d{4})").matcher(tail);if(m.find())return m.group(1)+"/"+m.group(2)+"/"+m.group(3);}}return "";}
    private List<String> dates(String u){ArrayList<String> r=new ArrayList<>();Matcher m=Pattern.compile("(\\d{2})\\s*[./-]\\s*(\\d{2})\\s*[./-]\\s*(\\d{4})").matcher(u);while(m.find())r.add(m.group(1)+"/"+m.group(2)+"/"+m.group(3));return r;}
    private String mrzName(String raw,boolean first){String s=raw.toUpperCase(Locale.ROOT).replace((char)13,(char)32).replace((char)10,(char)32).replace(" ","");Matcher m=Pattern.compile("([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)<<([A-ZÁÉÍÓÚÑ]+(?:<[A-ZÁÉÍÓÚÑ]+)*)").matcher(s);if(m.find())return clean((first?m.group(2):m.group(1)).replace('<',' '));return "";}
    private String[] mrzDates(String raw){String s=raw.toUpperCase(Locale.ROOT).replaceAll("[^0-9MF<]","");Matcher m=Pattern.compile("(\\d{6})\\d([MF<])(\\d{6})\\d").matcher(s);if(m.find())return new String[]{mrzDate(m.group(1)),mrzDate(m.group(3))};return new String[]{"",""};}
    private String mrzDate(String v){try{int yy=Integer.parseInt(v.substring(0,2));int y=yy<=30?2000+yy:1900+yy;return String.format(Locale.ROOT,"%02d/%02d/%04d",Integer.parseInt(v.substring(4,6)),Integer.parseInt(v.substring(2,4)),y);}catch(Exception e){return "";}}

    private JSONArray clientsData(){try{String s=prefs.getString("rgapro_clients","");if(!s.isEmpty())return new JSONArray(s);}catch(Exception ignored){}JSONArray old=new JSONArray();try{old=new JSONArray(prefs.getString("policies","[]"));}catch(Exception ignored){}JSONArray out=new JSONArray();for(int i=0;i<old.length();i++){JSONObject p=old.optJSONObject(i);if(p==null)continue;try{JSONObject c=new JSONObject();c.put("name",p.optString("name",""));c.put("surname",p.optString("surname",""));c.put("holder",p.optString("holder",""));c.put("identityNumber",p.optString("identityNumber",""));c.put("birthDate",p.optString("birthDate",""));c.put("expiry",p.optString("expiry",p.optString("validityDate","")));c.put("phone",p.optString("phone",""));c.put("address",p.optString("address",""));c.put("email",p.optString("email",""));c.put("ocrText",p.optString("ocrText",""));JSONArray ps=new JSONArray();JSONObject pol=new JSONObject();pol.put("number",p.optString("number",""));pol.put("type",p.optString("type",""));pol.put("expiry",p.optString("expiry",""));pol.put("content",p.optString("content",""));pol.put("insured",p.optString("insured",""));ps.put(pol);c.put("policies",ps);out.put(c);}catch(Exception ignored){}}if(out.length()>0)prefs.edit().putString("rgapro_clients",out.toString()).apply();return out;}
    private void upsertClient(JSONObject x){try{JSONArray a=clientsData();String id=x.optString("identityNumber","");boolean replaced=false;for(int i=0;i<a.length();i++){JSONObject old=a.optJSONObject(i);if(old!=null&&(!id.isEmpty()&&id.equalsIgnoreCase(old.optString("identityNumber","")))){a.put(i,x);replaced=true;break;}}if(!replaced)a.put(x);prefs.edit().putString("rgapro_clients",a.toString()).apply();}catch(Exception e){Toast.makeText(this,"No se pudo guardar el cliente: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private long daysUntil(String date){try{Date d=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).parse(date);return Math.round((d.getTime()-System.currentTimeMillis())/86400000.0);}catch(Exception e){return Long.MIN_VALUE;}}
}
