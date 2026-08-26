package com.rgapro1.ocaso;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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
    private SharedPreferences prefs; private LinearLayout body; private Uri cameraUri, documentUri; private int side=0;
    private String frontText="", backText="", currentImagePath="", frontImagePath="", backImagePath=""; private Bitmap currentBitmap, previewBitmap, frontBitmap, backBitmap;
    private int documentKind=0; // 1 imagen, 2 PDF
    private EditText fullNameE,dniE,birthE,addressE,phoneE;
    private EditText policyNumberE,holderE,policyDniE,policyAddressE,policyPhoneE,policyEmailE,receiptE,capitalE,decesosE,decesosLeveladaE;
    private TextView confidenceTv;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView tv(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setPadding(dp(6),dp(6),dp(6),dp(6));return t;}
    private GradientDrawable box(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.rgb(222,228,236));return g;}
    private Button btn(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(box(primary?BLUE:Color.WHITE,18));b.setPadding(dp(10),dp(6),dp(10),dp(6));return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(box(Color.WHITE,14));return e;}
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);home();}

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

    private void expiries(){shell("Próximas bajas","Alertas 60 · 40 · 30 · 7 · 1 día");body.addView(tv("PRÓXIMAS BAJAS",24,TEXT,true));JSONArray a=clientsData();int[] targets={1,7,30,40,60};boolean any=false;for(int target:targets){for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long d=daysUntil(x.optString("expiry"));if(d==target){any=true;TextView r=tv("🔔 "+target+" días · "+clientKey(x)+System.lineSeparator()+"DNI: "+x.optString("identityNumber","—")+" · Fecha: "+x.optString("expiry","—"),15,TEXT,false);r.setBackground(box(Color.WHITE,14));body.addView(r,new LinearLayout.LayoutParams(-1,dp(70)));}}}if(!any)body.addView(tv("No hay bajas exactamente en 60, 40, 30, 7 o 1 día.",15,MUTED,false));}

    private void clients(){shell("Clientes","Listado alfabético + Búsqueda 360");body.addView(tv("Clientes A-Z",26,TEXT,true));EditText q=input("Buscar DNI/NIE, nombre, teléfono, póliza o dirección");body.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));LinearLayout list=col();body.addView(list);Runnable r=()->{list.removeAllViews();ArrayList<JSONObject> xs=new ArrayList<>();JSONArray a=clientsData();String s=q.getText().toString().trim();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&(s.isEmpty()||matches(x,s)))xs.add(x);}Collections.sort(xs,(x,y)->clientKey(x).compareToIgnoreCase(clientKey(y)));if(xs.isEmpty())list.addView(tv("No hay clientes guardados todavía.",15,MUTED,false));for(JSONObject x:xs)addClientCard(list,x);};q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){r.run();}public void afterTextChanged(Editable e){}});r.run();Button add=btn("＋ NUEVO CLIENTE",true);add.setOnClickListener(v->editClient(null));body.addView(add,new LinearLayout.LayoutParams(-1,dp(58)));}
    private void addClientCard(LinearLayout list,JSONObject x){Button b=btn("👤  "+clientKey(x)+System.lineSeparator()+x.optString("identityNumber","")+" · "+x.optString("phone",""),false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setOnClickListener(v->detail(x));list.addView(b,new LinearLayout.LayoutParams(-1,dp(76)));}
    private String clientKey(JSONObject x){String h=x.optString("holder","").trim();if(!h.isEmpty())return h;String n=x.optString("name","").trim(),s=x.optString("surname","").trim();return (n+" "+s).trim().isEmpty()?"Sin nombre":(n+" "+s).trim();}
    private boolean matches(JSONObject x,String q){String s=normalizeSearch(q);for(String k:new String[]{"name","surname","holder","identityNumber","phone","email","address","policyNumber","type","expiry"})if(normalizeSearch(x.optString(k,"")).contains(s))return true;JSONArray ps=x.optJSONArray("policies");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&normalizeSearch(p.toString()).contains(s))return true;}return false;}
    private String normalizeSearch(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}

    private void detail(JSONObject x){shell("Ficha de cliente",clientKey(x));body.addView(tv(clientKey(x),25,TEXT,true));body.addView(tv("DATOS DEL CLIENTE",16,BLUE,true));addRead(body,"DNI/NIE",x.optString("identityNumber",""));addRead(body,"Fecha de nacimiento",x.optString("birthDate",""));addRead(body,"Dirección",x.optString("address",""));addRead(body,"Teléfono",x.optString("phone",""));addRead(body,"Email",x.optString("email",""));body.addView(tv("PÓLIZAS",16,BLUE,true));JSONArray ps=x.optJSONArray("policies");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p==null)continue;Button b=btn("▣ "+p.optString("type","Póliza")+" · "+p.optString("number","Sin número"),false);b.setOnClickListener(v->policyDetail(p));body.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));}Button edit=btn("✏️ EDITAR",true);edit.setOnClickListener(v->editClient(x));body.addView(edit,new LinearLayout.LayoutParams(-1,dp(58)));}
    private void addRead(LinearLayout p,String label,String value){TextView t=tv(label+System.lineSeparator()+(value==null||value.isEmpty()?"—":value),15,TEXT,false);t.setBackground(box(Color.WHITE,12));p.addView(t,new LinearLayout.LayoutParams(-1,dp(62)));}

    private void editClient(JSONObject old){LinearLayout l=col();EditText n=input("Nombre y apellidos"),d=input("DNI/NIE"),b=input("Fecha nacimiento dd/MM/yyyy"),ph=input("Teléfono"),ad=input("Dirección"),em=input("Email");if(old!=null){n.setText(old.optString("holder",clientKey(old)));d.setText(old.optString("identityNumber",""));b.setText(old.optString("birthDate",""));ph.setText(old.optString("phone",""));ad.setText(old.optString("address",""));em.setText(old.optString("email",""));}for(EditText e:new EditText[]{n,d,b,ph,ad,em})l.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));new AlertDialog.Builder(this).setTitle(old==null?"Nuevo cliente":"Editar cliente").setView(l).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(di,w)->{try{JSONObject x=old==null?new JSONObject():old;String full=n.getText().toString().trim();x.put("holder",full);x.put("name",full);x.put("surname","");x.put("identityNumber",d.getText().toString().trim().toUpperCase(Locale.ROOT));x.put("birthDate",b.getText().toString().trim());x.put("phone",ph.getText().toString().trim());x.put("address",ad.getText().toString().trim());x.put("email",em.getText().toString().trim());if(!x.has("policies"))x.put("policies",new JSONArray());upsertClient(x);clients();}catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}}).show();}

    private void policyDetail(JSONObject p){
        LinearLayout l=col();
        String product=p.optString("policyType",p.optString("type","Póliza"));
        boolean decesos="Decesos".equalsIgnoreCase(product);
        addRead(l,"Producto",product);
        addRead(l,"Número de póliza",p.optString("number",""));
        addRead(l,"Tomador",p.optString("holder",""));
        addRead(l,"DNI/NIE",p.optString("identityNumber",""));
        addRead(l,"Dirección",p.optString("address",""));
        addRead(l,"Teléfono",p.optString("phone",""));
        addRead(l,"Email",p.optString("email",""));
        addRead(l,"Precio / recibo",p.optString("receipt",""));
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





    private void policies(){shell("Pólizas","Pólizas Ocaso guardadas");JSONArray a=clientsData();boolean any=false;for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c==null)continue;JSONArray ps=c.optJSONArray("policies");if(ps==null)continue;for(int j=0;j<ps.length();j++){JSONObject p=ps.optJSONObject(j);if(p==null)continue;any=true;Button b=btn("▣ "+p.optString("type","OCASO")+" · "+p.optString("number","—")+System.lineSeparator()+clientKey(c),false);b.setOnClickListener(v->policyDetail(p));body.addView(b,new LinearLayout.LayoutParams(-1,dp(72)));}}if(!any)body.addView(tv("No hay pólizas guardadas.",15,MUTED,false));Button scan=btn("📄 SUBIR PÓLIZA PDF",true);scan.setOnClickListener(v->choosePdf());body.addView(scan,new LinearLayout.LayoutParams(-1,dp(60)));}

    private void ocrPage(){shell("OCR","Primero revisa el documento; después procesa y acepta los datos");body.addView(tv("1 · DOCUMENTO",18,BLUE,true));body.addView(tv("El archivo NO se guarda todavía. Primero comprueba que es el documento correcto.",14,MUTED,false));LinearLayout preview=col();preview.setBackground(box(Color.WHITE,16));body.addView(preview);renderPreview(preview);
        LinearLayout r=new LinearLayout(this);Button front=btn("📷 Tomar anverso",true),back=btn("↩️ Tomar reverso",false);r.addView(front,new LinearLayout.LayoutParams(0,dp(58),1));r.addView(back,new LinearLayout.LayoutParams(0,dp(58),1));body.addView(r);front.setOnClickListener(v->{side=1;takePhoto();});back.setOnClickListener(v->{side=2;takePhoto();});
        LinearLayout r2=new LinearLayout(this);Button img=btn("🖼️ Elegir JPEG/JPG",false),pdf=btn("📄 Elegir PDF",false);r2.addView(img,new LinearLayout.LayoutParams(0,dp(58),1));r2.addView(pdf,new LinearLayout.LayoutParams(0,dp(58),1));body.addView(r2);img.setOnClickListener(v->chooseImage());pdf.setOnClickListener(v->choosePdf());
        Button process=btn("▶ PROCESAR OCR",true);process.setOnClickListener(v->processCurrentDocument());body.addView(process,new LinearLayout.LayoutParams(-1,dp(62)));
        if(documentKind==0)body.addView(tv("Selecciona o fotografía un documento para continuar.",14,MUTED,false));
    }

    private void renderPreview(LinearLayout container){if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);container.addView(iv,new LinearLayout.LayoutParams(-1,dp(360)));container.addView(tv(documentKind==2?"Vista previa: primera página del PDF":"Vista previa: imagen original",14,GREEN,true));}else container.addView(tv("Aún no has seleccionado ningún documento.",15,MUTED,false));}

    private void takePhoto(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA);return;}try{File f=new File(getExternalFilesDir("captures"),"scan_"+System.currentTimeMillis()+".jpg");f.getParentFile().mkdirs();cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAMERA);}catch(Exception e){Toast.makeText(this,"No se pudo abrir la cámara",Toast.LENGTH_LONG).show();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==CAMERA&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)takePhoto();}
    private void chooseImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,IMAGE);}
    private void choosePdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PDF);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        try{
            if(request==PDF){Uri u=data==null?null:data.getData();if(u==null)return;documentUri=u;documentKind=2;previewBitmap=renderPdfFirstPage(u);currentImagePath=u.toString();ocrPage();return;}
            if(request==IMAGE && data!=null && data.getClipData()!=null){
                int n=data.getClipData().getItemCount();
                if(n>=2){Uri f=data.getClipData().getItemAt(0).getUri();Uri b=data.getClipData().getItemAt(1).getUri();frontBitmap=loadBitmap(f);backBitmap=loadBitmap(b);frontImagePath=f.toString();backImagePath=b.toString();currentBitmap=frontBitmap;previewBitmap=frontBitmap;documentKind=1;reviewDniPair();return;}
            }
            Uri u=request==CAMERA?cameraUri:(data==null?null:data.getData());if(u==null)return;
            documentUri=u;documentKind=1;currentBitmap=loadBitmap(u);previewBitmap=currentBitmap;currentImagePath=u.toString();
            if(request==CAMERA){if(side==2){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}
            else {if(side==2 || (frontBitmap!=null && backBitmap==null)){backBitmap=currentBitmap;backImagePath=currentImagePath;}else{frontBitmap=currentBitmap;frontImagePath=currentImagePath;}}
            reviewDniPair();
        }catch(Exception e){Toast.makeText(this,"No se pudo cargar el documento: "+e.getMessage(),Toast.LENGTH_LONG).show();}
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
        Matcher m=Pattern.compile("(?m)^APELLIDOS?\\s*[:.-]?\\s*(.+?)(?=\\n(?:NOMBRE|SEXO|NACIONALIDAD|NACIMIENTO)|$)").matcher(u);
        String sur=m.find()?m.group(1).trim():"";
        m=Pattern.compile("(?m)^NOMBRES?\\s*[:.-]?\\s*(.+?)(?=\\n(?:SEXO|NACIONALIDAD|NACIMIENTO)|$)").matcher(u);
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
        r.process(InputImage.fromBitmap(frontBitmap,0)).addOnSuccessListener(f->{frontText=f==null?"":f.getText();r.process(InputImage.fromBitmap(backBitmap,0)).addOnSuccessListener(b->{backText=b==null?"":b.getText();currentBitmap=frontBitmap;previewBitmap=frontBitmap;currentImagePath=frontImagePath;r.close();showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR reverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR anverso: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }






    private void processCurrentDocument(){if(documentUri==null||documentKind==0){Toast.makeText(this,"Primero selecciona un documento.",Toast.LENGTH_LONG).show();return;}if(documentKind==2){PdfOcrHelper.process(this,documentUri,new PdfOcrHelper.Callback(){public void onSuccess(String text){runOnUiThread(()->showPolicyReview(OcasoPolicyParser.parse(text),text));}public void onError(Exception e){runOnUiThread(()->Toast.makeText(MainActivityV2.this,"PDF: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}else processImage();}
    private void processImage(){
        if(currentBitmap==null){Toast.makeText(this,"Primero selecciona un JPEG válido.",Toast.LENGTH_LONG).show();return;}
        TextRecognizer r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);r.process(InputImage.fromBitmap(currentBitmap,0)).addOnSuccessListener(t->{String text=t.getText()==null?"":t.getText();if(side==2)backText=text;else frontText=text;r.close();showIdentityReview(parseEssentialRobust(frontText+"\\n"+backText));}).addOnFailureListener(e->{r.close();Toast.makeText(this,"OCR: "+e.getMessage(),Toast.LENGTH_LONG).show();});
    }






    private void showIdentityReview(JSONObject x){shell("Revisión DNI/NIE","Comprueba los datos antes de guardar");body.addView(tv("2 · DOCUMENTO LEÍDO",18,BLUE,true));if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);body.addView(iv,new LinearLayout.LayoutParams(-1,dp(300)));}body.addView(tv("3 · DATOS DETECTADOS",18,BLUE,true));fullNameE=input("Nombre y apellidos");dniE=input("DNI/NIE");birthE=input("Fecha de nacimiento");addressE=input("Dirección");phoneE=input("Teléfono (lo puedes añadir tú)");fullNameE.setText(x.optString("fullName",""));dniE.setText(x.optString("identityNumber",""));birthE.setText(x.optString("birthDate",""));addressE.setText(x.optString("address",""));confidenceTv=tv("Confianza de lectura: "+x.optInt("confidence",0)+"% · Comprueba visualmente el documento.",14,MUTED,false);body.addView(confidenceTv);for(EditText e:new EditText[]{fullNameE,dniE,birthE,addressE,phoneE})body.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));Button accept=btn("✅ ACEPTAR DATOS Y GUARDAR",true),reject=btn("❌ RECHAZAR / VOLVER A DOCUMENTO",false);body.addView(accept,new LinearLayout.LayoutParams(-1,dp(62)));body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));accept.setOnClickListener(v->saveIdentity());reject.setOnClickListener(v->ocrPage());}

    private void saveIdentity(){try{String full=fullNameE.getText().toString().trim(),id=dniE.getText().toString().trim().toUpperCase(Locale.ROOT),birth=birthE.getText().toString().trim();if(!isValidIdentity(id)){Toast.makeText(this,"DNI/NIE no válido. Revísalo antes de guardar.",Toast.LENGTH_LONG).show();return;}if(!validDate(birth)){Toast.makeText(this,"Fecha de nacimiento no válida.",Toast.LENGTH_LONG).show();return;}JSONObject x=findClientById(id);if(x==null)x=new JSONObject();x.put("holder",full);x.put("name",full);x.put("surname","");x.put("identityNumber",id);x.put("birthDate",birth);x.put("address",addressE.getText().toString().trim());x.put("phone",phoneE.getText().toString().trim());x.put("image",currentImagePath);x.put("frontImage",frontImagePath);x.put("backImage",backImagePath);if(!x.has("policies"))x.put("policies",new JSONArray());upsertClient(x);Toast.makeText(this,"Cliente guardado.",Toast.LENGTH_LONG).show();detail(x);}catch(Exception e){Toast.makeText(this,"No se pudo guardar: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    private void showPolicyReview(JSONObject p,String raw){
        shell("Revisión póliza Ocaso","Comprueba los datos y el documento antes de guardar");
        body.addView(tv("2 · DOCUMENTO PDF",18,BLUE,true));
        if(previewBitmap!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(previewBitmap);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);body.addView(iv,new LinearLayout.LayoutParams(-1,dp(300)));}
        body.addView(tv("3 · DATOS ÚTILES DETECTADOS",18,BLUE,true));

        String product=p.optString("policyType",p.optString("type","Otros"));
        boolean decesos="Decesos".equalsIgnoreCase(product);

        policyNumberE=input("Número de póliza");
        holderE=input("Tomador (nombre y apellidos)");
        policyDniE=input("DNI/NIE");
        policyAddressE=input("Dirección");
        policyPhoneE=input("Teléfono");
        policyEmailE=input("Email");
        receiptE=input("Precio / total recibo");
        capitalE=input("Capital");
        decesosE=input("Total decesos");
        decesosLeveladaE=input("Decesos nivelada");

        policyNumberE.setText(p.optString("number",""));
        holderE.setText(p.optString("holder",""));
        policyDniE.setText(p.optString("identityNumber",p.optString("dni","")));
        policyAddressE.setText(p.optString("address",""));
        policyPhoneE.setText(p.optString("phone",""));
        policyEmailE.setText(p.optString("email",""));
        receiptE.setText(p.optString("receipt",""));
        capitalE.setText(p.optString("capital",""));
        decesosE.setText(p.optString("decesos",""));
        decesosLeveladaE.setText(p.optString("decesosLevelada",""));

        addPolicyField("PRODUCTO",product,null);
        addPolicyField("Nº DE PÓLIZA",null,policyNumberE);
        addPolicyField("TOMADOR",null,holderE);
        addPolicyField("DNI / NIE",null,policyDniE);
        addPolicyField("DIRECCIÓN",null,policyAddressE);
        addPolicyField("TELÉFONO",null,policyPhoneE);
        addPolicyField("EMAIL",null,policyEmailE);
        addPolicyField("PRECIO / RECIBO",null,receiptE);

        if(decesos){
            addPolicyField("CAPITAL DE DECESOS",null,capitalE);
            addPolicyField("TOTAL DECESOS",null,decesosE);
            addPolicyField("DECESOS NIVELADA",null,decesosLeveladaE);
        }else if(!capitalE.getText().toString().trim().isEmpty()){
            addPolicyField("CAPITAL",null,capitalE);
        }

        body.addView(tv("ASEGURADOS DETECTADOS",16,BLUE,true));
        JSONArray ins=p.optJSONArray("insured");
        if(ins!=null)for(int i=0;i<ins.length();i++){JSONObject a=ins.optJSONObject(i);if(a!=null)body.addView(tv("• "+a.optString("name","")+" · "+a.optString("identityNumber","—")+" · "+a.optString("birthDate","—"),14,TEXT,false));}
        Button accept=btn("✅ ACEPTAR DATOS Y ASOCIAR PÓLIZA",true),reject=btn("❌ RECHAZAR / VOLVER",false);
        body.addView(accept,new LinearLayout.LayoutParams(-1,dp(64)));body.addView(reject,new LinearLayout.LayoutParams(-1,dp(58)));
        accept.setOnClickListener(v->savePolicy(raw,ins));reject.setOnClickListener(v->ocrPage());
    }

    private void addPolicyField(String label,String value,EditText field){
        body.addView(tv(label,13,MUTED,true));
        if(field!=null) body.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));
        else body.addView(tv(value==null?"":value,16,TEXT,false),new LinearLayout.LayoutParams(-1,dp(54)));
    }


    private void addPolicyField(String label,String value,EditText field){
        body.addView(tv(label,13,MUTED,true));
        if(field!=null) body.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));
        else body.addView(tv(value==null?"":value,16,TEXT,false),new LinearLayout.LayoutParams(-1,dp(54)));
    }


    private void addPolicyField(String label,String value,EditText field){
        body.addView(tv(label,13,MUTED,true));
        if(field!=null) body.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));
        else body.addView(tv(value==null?"":value,16,TEXT,false),new LinearLayout.LayoutParams(-1,dp(54)));
    }


    private void addPolicyField(String label,String value,EditText field){
        body.addView(tv(label,13,MUTED,true));
        if(field!=null) body.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));
        else body.addView(tv(value==null?"":value,16,TEXT,false),new LinearLayout.LayoutParams(-1,dp(54)));
    }


    private void savePolicy(String raw,JSONArray insured){
        try{
            String id=policyDniE.getText().toString().trim().toUpperCase(Locale.ROOT),number=policyNumberE.getText().toString().trim();
            if(number.isEmpty()){Toast.makeText(this,"El número de póliza es obligatorio.",Toast.LENGTH_LONG).show();return;}
            JSONObject c=findClientById(id);if(c==null)c=findClientByName(holderE.getText().toString().trim());if(c==null)c=new JSONObject();
            c.put("holder",holderE.getText().toString().trim());c.put("name",holderE.getText().toString().trim());c.put("surname","");
            if(!id.isEmpty())c.put("identityNumber",id);c.put("address",policyAddressE.getText().toString().trim());c.put("phone",policyPhoneE.getText().toString().trim());c.put("email",policyEmailE.getText().toString().trim());
            JSONArray ps=c.optJSONArray("policies");if(ps==null)ps=new JSONArray();
            JSONObject pol=new JSONObject();
            String product=currentPolicyProduct(raw);
            pol.put("type","OCASO");pol.put("policyType",product);pol.put("number",number);pol.put("holder",holderE.getText().toString().trim());pol.put("identityNumber",id);pol.put("address",policyAddressE.getText().toString().trim());pol.put("phone",policyPhoneE.getText().toString().trim());pol.put("email",policyEmailE.getText().toString().trim());pol.put("receipt",receiptE.getText().toString().trim());pol.put("capital",capitalE.getText().toString().trim());
            if("Decesos".equalsIgnoreCase(product)){
                pol.put("decesos",decesosE.getText().toString().trim());pol.put("decesosLevelada",decesosLeveladaE.getText().toString().trim());
            }else{
                pol.put("decesos","");pol.put("decesosLevelada","");
            }
            pol.put("insured",insured==null?new JSONArray():insured);pol.put("documentUri",documentUri==null?"":documentUri.toString());pol.put("ocrText",raw);
            boolean replaced=false;for(int i=0;i<ps.length();i++){JSONObject old=ps.optJSONObject(i);if(old!=null&&number.equals(old.optString("number",""))){ps.put(i,pol);replaced=true;break;}}
            if(!replaced)ps.put(pol);c.put("policies",ps);upsertClient(c);Toast.makeText(this,"Póliza guardada y asociada al cliente.",Toast.LENGTH_LONG).show();detail(c);
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar la póliza: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private String currentPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("HOGAR"))return "Hogar";
        if(u.contains("SALUD"))return "Salud";
        if(u.contains("AUTOMOVIL")||u.contains("AUTOMÓVIL")||u.contains("VEHICULO")||u.contains("VEHÍCULO"))return "Auto";
        return "Otros";
    }


    private String currentPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("HOGAR"))return "Hogar";
        if(u.contains("SALUD"))return "Salud";
        if(u.contains("AUTOMOVIL")||u.contains("AUTOMÓVIL")||u.contains("VEHICULO")||u.contains("VEHÍCULO"))return "Auto";
        return "Otros";
    }


    private String currentPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("HOGAR"))return "Hogar";
        if(u.contains("SALUD"))return "Salud";
        if(u.contains("AUTOMOVIL")||u.contains("AUTOMÓVIL")||u.contains("VEHICULO")||u.contains("VEHÍCULO"))return "Auto";
        return "Otros";
    }


    private String currentPolicyProduct(String raw){
        String u=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(u.contains("DECESOS")||u.contains("ASISTENCIA FAMILIAR"))return "Decesos";
        if(u.contains("VIDA")||u.contains("FALLECIMIENTO"))return "Vida";
        if(u.contains("ACCIDENTE"))return "Accidentes";
        if(u.contains("HOGAR"))return "Hogar";
        if(u.contains("SALUD"))return "Salud";
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
    private String findBirth(String u){Matcher m=Pattern.compile("(?<![0-9])([0-3][0-9])[/.-]([0-1][0-9])[/.-]((?:19|20)[0-9]{2})(?![0-9])").matcher(u);int yearNow=Calendar.getInstance().get(Calendar.YEAR);while(m.find()){String d=m.group(1)+"/"+m.group(2)+"/"+m.group(3);if(validDate(d)&&Integer.parseInt(m.group(3))<=yearNow-10)return d;}return "";}
    private boolean validDate(String s){try{Date d=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).parse(s);return new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(d).equals(s);}catch(Exception e){return false;}}
    private String clean(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}

    private JSONObject findClientById(String id){if(id==null||id.isEmpty())return null;JSONArray a=clientsData();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&id.equalsIgnoreCase(x.optString("identityNumber","")))return x;}return null;}
    private JSONObject findClientByName(String name){String q=normalizeSearch(name);if(q.isEmpty())return null;JSONArray a=clientsData();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&normalizeSearch(clientKey(x)).equals(q))return x;}return null;}
    private JSONArray clientsData(){try{String s=prefs.getString("rgapro_clients","");if(!s.isEmpty())return new JSONArray(s);}catch(Exception ignored){}return new JSONArray();}
    private void upsertClient(JSONObject x){try{JSONArray a=clientsData();String id=x.optString("identityNumber","");String holder=x.optString("holder","");boolean replaced=false;for(int i=0;i<a.length();i++){JSONObject old=a.optJSONObject(i);if(old==null)continue;if (((!id.isEmpty()) && id.equalsIgnoreCase(old.optString("identityNumber", ""))) || ((!holder.isEmpty()) && normalizeSearch(holder).equals(normalizeSearch(old.optString("holder", ""))))) {a.put(i,x);replaced=true;break;}}if(!replaced)a.put(x);prefs.edit().putString("rgapro_clients",a.toString()).apply();}catch(Exception e){Toast.makeText(this,"No se pudo guardar: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private long daysUntil(String date){try{Date d=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).parse(date);return Math.round((d.getTime()-System.currentTimeMillis())/86400000.0);}catch(Exception e){return Long.MIN_VALUE;}}
}
