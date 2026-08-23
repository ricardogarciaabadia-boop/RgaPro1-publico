package com.rgapro1.ocaso;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import org.json.*;
import java.io.*;

public class Client360Activity extends FragmentActivity {
    private static final int NAVY=0xff06244a, BLUE=0xff1685df, BG=0xfff7f9fc, TEXT=0xff17243a, MUTED=0xff607084, GREEN=0xff16a34a, RED=0xffdc2626;
    private JSONObject client;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView t(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(TEXT);v.setTypeface(null,b?1:0);v.setPadding(dp(12),dp(7),dp(12),dp(7));return v;}
    private TextView white(String s,int z,boolean b){TextView v=t(s,z,b);v.setTextColor(Color.WHITE);return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);return b;}
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));g.setStroke(dp(1),0x18000000);return g;}
    private EditText field(String label,String value){EditText e=new EditText(this);e.setHint(label);e.setSingleLine(true);e.setText(value==null?"":value);e.setTextSize(16);e.setPadding(dp(14),0,dp(14),0);return e;}
    @Override public void onCreate(Bundle b){super.onCreate(b);String raw=getIntent().getStringExtra("client_json");try{client=new JSONObject(raw==null?"{}":raw);show();}catch(Exception e){Toast.makeText(this,"No se pudo abrir el cliente",Toast.LENGTH_LONG).show();finish();}}
    private boolean badAddress(String x){String s=x==null?"":x.trim().toLowerCase();return s.isEmpty()||s.equals("de cobro")||s.equals("domicilio de cobro")||s.equals("dirección de cobro")||s.equals("direccion de cobro")||s.equals("del seguro y domicilio");}
    private String holder(){String h=client.optString("holder",client.optString("name","")).trim();return h.isEmpty()||h.equalsIgnoreCase("del seguro y domicilio")||h.equalsIgnoreCase("de cobro")?"Titular no identificado":h;}
    private String expiry(){String e=client.optString("expiry","").trim();if(e.isEmpty())e=client.optString("validityDate","").trim();return e;}
    private void show(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(8),dp(5),dp(8),dp(7));head.setBackgroundColor(NAVY);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=btn("←  VOLVER");back.setTextColor(Color.WHITE);back.setTextSize(15);back.setBackground(bg(0x22000000,12));back.setOnClickListener(v->finish());
        Button edit=btn("✎  EDITAR");edit.setTextColor(Color.WHITE);edit.setTextSize(15);edit.setBackground(bg(0x22000000,12));edit.setOnClickListener(v->editClient());
        top.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(edit,new LinearLayout.LayoutParams(0,dp(48),1));head.addView(top);
        TextView title=white("CLIENTE 360º",20,true);title.setGravity(Gravity.CENTER);head.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));root.addView(head);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(14),dp(16),dp(24));
        body.addView(t("👤  "+holder(),24,true));
        String id=client.optString("identityNumber",client.optString("holderDni","—"));
        String address=client.optString("address","");
        String info="DNI: "+(id.isEmpty()?"—":id)+"\n☎ "+client.optString("phone","—")+"\n✉ "+client.optString("email","—");
        body.addView(t(info,16,false));
        if(badAddress(address)){
            TextView notice=t("ℹ  No se ha identificado la dirección del asegurado.\nLa dirección de cobro puede ser distinta del domicilio del asegurado.",14,false);notice.setBackground(bg(0xffeaf3ff,14));notice.setPadding(dp(14),dp(12),dp(14),dp(12));body.addView(notice,new LinearLayout.LayoutParams(-1,dp(76)));
        } else body.addView(t("Dirección del asegurado\n"+address,15,false));
        addPolicyCard(body,client);addInsureds(body,client);addDocuments(body,client);
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void addPolicyCard(LinearLayout body,JSONObject p){
        body.addView(t("🛡  PRODUCTO / PÓLIZA",19,true));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(12),dp(18),dp(12));card.setBackground(bg(Color.WHITE,14));
        TextView type=t(p.optString("type","Producto no identificado"),21,true);type.setTextColor(NAVY);card.addView(type);
        TextView num=t("N.º DE PÓLIZA\n"+p.optString("number","No identificado"),18,true);num.setTextColor(BLUE);card.addView(num);
        String ex=expiry();TextView date=t(ex.isEmpty()?"⚠  VENCIMIENTO\nNO DETECTADO — REVISAR DOCUMENTO":"📅  VENCIMIENTO\n"+ex,18,true);date.setTextColor(ex.isEmpty()?0xffb45309:BLUE);date.setPadding(dp(12),dp(10),dp(12),dp(10));date.setBackground(bg(ex.isEmpty()?0xfffff7ed:0xffeef7ff,10));card.addView(date);
        card.setOnClickListener(v->showProduct(p));body.addView(card,new LinearLayout.LayoutParams(-1,dp(174)));
    }
    private void addInsureds(LinearLayout body,JSONObject p){
        JSONArray a=p.optJSONArray("insureds");if(a==null||a.length()==0)return;
        body.addView(t("👥  ASEGURADOS DE LA PÓLIZA   "+a.length(),19,true));body.addView(t("Pulsa una persona para ver sus datos",14,false));
        for(int i=0;i<a.length();i++){
            JSONObject person=a.optJSONObject(i);if(person==null)continue;String name=person.optString("name","").trim();if(name.isEmpty())continue;String id=person.optString("identityNumber","");
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(5),dp(8),dp(5));row.setBackground(bg(Color.WHITE,12));
            TextView n=t(String.valueOf(i+1),16,true);n.setGravity(Gravity.CENTER);n.setTextColor(BLUE);row.addView(n,new LinearLayout.LayoutParams(dp(34),dp(58)));
            TextView personText=t("👤  "+name+"\n     "+(id.isEmpty()?"Documento no identificado":"DNI/NIE · "+mask(id)),15,true);row.addView(personText,new LinearLayout.LayoutParams(0,dp(62),1));
            TextView arrow=t("›",28,false);arrow.setTextColor(BLUE);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(62)));
            row.setOnClickListener(v->showInsured(person));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(70));lp.setMargins(0,0,0,dp(7));body.addView(row,lp);
        }
    }
    private String mask(String id){String x=id==null?"":id.trim();return x.length()<=4?x:"••••"+x.substring(x.length()-4);}
    private void showInsured(JSONObject p){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(4),0,dp(4),0);
        box.addView(t("👤  DATOS PERSONALES",16,true));box.addView(t("Nombre y apellidos\n"+p.optString("name","—"),17,false));box.addView(t("DNI / NIE\n"+p.optString("identityNumber","—"),17,false));box.addView(t("Fecha de nacimiento\n"+p.optString("birthDate","—"),17,false));box.addView(t("Sexo\n"+p.optString("sex","—"),17,false));
        box.addView(t("🛡  DATOS DE LA PÓLIZA",16,true));box.addView(t("Fecha de efecto decesos\n"+p.optString("effectiveDeathDate","—"),17,false));String cap=p.optString("deathCapital",p.optString("capitalDecesos",""));if(!cap.isEmpty())box.addView(t("Capital de fallecimiento\n"+cap,17,false));
        new AlertDialog.Builder(this).setTitle(p.optString("name","Asegurado")).setView(box).setPositiveButton("Cerrar",null).show();
    }
    private void addDocuments(LinearLayout body,JSONObject p){JSONArray docs=p.optJSONArray("documentPhotos");if(docs==null||docs.length()==0)return;body.addView(t("📄  DOCUMENTACIÓN",19,true));for(int i=0;i<docs.length();i++){String path=documentPath(docs.opt(i));if(path.isEmpty())continue;Button d=btn("📄  "+new File(path).getName()+"   ›");d.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);d.setBackground(bg(Color.WHITE,12));d.setOnClickListener(v->documentMenu(path));body.addView(d,new LinearLayout.LayoutParams(-1,dp(58)));}}
    private String documentPath(Object item){if(item==null||item==JSONObject.NULL)return "";if(item instanceof JSONObject)return ((JSONObject)item).optString("path","");return String.valueOf(item);}
    private void showProduct(JSONObject p){String ex=expiry();new AlertDialog.Builder(this).setTitle("Producto / póliza").setMessage("Tipo: "+p.optString("type","—")+"\nN.º de póliza: "+p.optString("number","—")+"\nTitular: "+holder()+"\nVencimiento: "+(ex.isEmpty()?"NO DETECTADO — REVISAR DOCUMENTO":ex)+"\nAsegurados: "+p.optInt("insuredCount",0)).setPositiveButton("Cerrar",null).show();}
    private void editClient(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(8),0,dp(8),0);
        EditText holder=field("Titular",client.optString("holder","")),name=field("Nombre",client.optString("name","")),surname=field("Apellidos",client.optString("surname","")),identity=field("DNI / NIE",client.optString("identityNumber",client.optString("holderDni",""))),cif=field("CIF",client.optString("cif","")),phone=field("Teléfono",client.optString("phone","")),email=field("Correo electrónico",client.optString("email","")),address=field("Dirección del asegurado",badAddress(client.optString("address",""))?"":client.optString("address","")),birth=field("Fecha de nacimiento",client.optString("birthDate","")),type=field("Tipo",client.optString("type","")),number=field("Número de póliza / documento",client.optString("number","")),expiryField=field("Fecha de vencimiento",expiry()),nationality=field("Nacionalidad",client.optString("nationality","")),sex=field("Sexo",client.optString("sex","")),birthPlace=field("Lugar de nacimiento",client.optString("birthPlace",""));
        EditText[] f={holder,name,surname,identity,cif,phone,email,address,birth,type,number,expiryField,nationality,sex,birthPlace};for(EditText e:f)form.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));ScrollView s=new ScrollView(this);s.addView(form);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar cliente").setView(s).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->saveEdited(d,holder,name,surname,identity,cif,phone,email,address,birth,type,number,expiryField,nationality,sex,birthPlace)));d.show();
    }
    private void saveEdited(AlertDialog d,EditText holder,EditText name,EditText surname,EditText identity,EditText cif,EditText phone,EditText email,EditText address,EditText birth,EditText type,EditText number,EditText expiry,EditText nationality,EditText sex,EditText birthPlace){try{put(client,"holder",holder.getText().toString());put(client,"name",name.getText().toString());put(client,"surname",surname.getText().toString());put(client,"identityNumber",identity.getText().toString());put(client,"holderDni",identity.getText().toString());put(client,"cif",cif.getText().toString());put(client,"phone",phone.getText().toString());put(client,"email",email.getText().toString());put(client,"address",address.getText().toString());put(client,"birthDate",birth.getText().toString());put(client,"type",type.getText().toString());put(client,"number",number.getText().toString());put(client,"expiry",expiry.getText().toString());put(client,"validityDate",expiry.getText().toString());put(client,"nationality",nationality.getText().toString());put(client,"sex",sex.getText().toString());put(client,"birthPlace",birthPlace.getText().toString());client.put("updatedAt",System.currentTimeMillis());if(saveClient(client)){d.dismiss();show();Toast.makeText(this,"Datos guardados",Toast.LENGTH_LONG).show();}else Toast.makeText(this,"No se encontró el cliente original",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"No se pudieron guardar los cambios",Toast.LENGTH_LONG).show();}}
    private void put(JSONObject o,String k,String v)throws Exception{String x=v==null?"":v.trim();if(x.isEmpty())o.remove(k);else o.put(k,x);}
    private boolean saveClient(JSONObject edited){try{SharedPreferences prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);JSONArray a=new JSONArray(prefs.getString("policies","[]"));int best=-1,scoreBest=0;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;int s=0;s+=same(p,"identityNumber",edited.optString("identityNumber"),3);s+=same(p,"holderDni",edited.optString("holderDni"),3);s+=same(p,"number",edited.optString("number"),2);s+=same(p,"email",edited.optString("email"),2);s+=same(p,"phone",edited.optString("phone"),2);s+=same(p,"holder",edited.optString("holder"),1);s+=same(p,"createdAt",edited.optString("createdAt"),4);if(s>scoreBest){scoreBest=s;best=i;}}if(best<0)return false;a.put(best,edited);prefs.edit().putString("policies",a.toString()).apply();return true;}catch(Exception e){return false;}}
    private int same(JSONObject a,String k,String v,int w){if(v==null||v.trim().isEmpty())return 0;return a.optString(k,"").trim().equalsIgnoreCase(v.trim())?w:0;}
    private void documentMenu(String path){new AlertDialog.Builder(this).setTitle("Documento").setItems(new String[]{"👁️ Abrir / ver","⬇️ Descargar","📤 Compartir"},(d,w)->{if(w==0)open(path);else if(w==1)download(path);else share(path);}).show();}
    private Uri uri(String path){File f=new File(path);if(!f.exists())return null;try{return FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);}catch(Exception e){return null;}}
    private void open(String path){Uri u=uri(path);if(u==null){toast("No se puede acceder al archivo");return;}Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,mime(path));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(Exception e){toast("No hay una aplicación compatible para abrirlo");}}
    private void share(String path){Uri u=uri(path);if(u==null){toast("No se puede compartir este archivo");return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime(path));i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(i,"Compartir documento"));}catch(Exception e){toast("No hay aplicaciones disponibles");}}
    private void download(String path){File src=new File(path);if(!src.exists()){toast("No se encuentra el documento");return;}try{if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,src.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime(path));v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("No se pudo crear la descarga");try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,v,null,null);}else{File d=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);if(!d.exists())d.mkdirs();try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(new File(d,src.getName()))){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}}toast("Documento descargado");}catch(Exception e){toast("No se pudo descargar");}}
    private String mime(String p){String x=p.toLowerCase();if(x.endsWith(".pdf"))return "application/pdf";if(x.endsWith(".png"))return "image/png";if(x.endsWith(".webp"))return "image/webp";return "image/jpeg";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
