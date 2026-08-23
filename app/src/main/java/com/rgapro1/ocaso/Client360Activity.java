package com.rgapro1.ocaso;

import android.app.AlertDialog;
import android.content.*;
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
    private static final int NAVY=0xff0c2343, BLUE=0xff1985e0, BG=0xfff7f9fc, TEXT=0xff1c2736;
    private JSONObject client;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView t(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(TEXT);v.setTypeface(null,b?1:0);v.setPadding(dp(12),dp(8),dp(12),dp(8));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);return b;}
    private EditText field(String label,String value){EditText e=new EditText(this);e.setHint(label);e.setSingleLine(true);e.setText(value==null?"":value);e.setTextSize(16);e.setPadding(dp(14),0,dp(14),0);return e;}
    @Override public void onCreate(Bundle b){super.onCreate(b);String raw=getIntent().getStringExtra("client_json");try{client=new JSONObject(raw==null?"{}":raw);show();}catch(Exception e){Toast.makeText(this,"No se pudo abrir el cliente",Toast.LENGTH_LONG).show();finish();}}
    private void render(){show();}
    private void show(JSONObject p){client=p==null?new JSONObject():p;show();}
    private TextView headerText(String s,int z){TextView v=t(s,z,true);v.setTextColor(Color.WHITE);return v;}
    private boolean isBadAddress(String value){String x=value==null?"":value.trim().toLowerCase();return x.isEmpty()||x.equals("de cobro")||x.equals("domicilio de cobro")||x.equals("dirección de cobro")||x.equals("direccion de cobro")||x.equals("del seguro y domicilio");}
    private String cleanHolder(){String h=client.optString("holder",client.optString("name","")).trim();if(h.isEmpty()||h.equalsIgnoreCase("del Seguro y Domicilio")||h.equalsIgnoreCase("del seguro y domicilio")||h.equalsIgnoreCase("de Cobro")||h.equalsIgnoreCase("de cobro"))return "Titular no identificado";return h;}
    private String expiry(){String e=client.optString("expiry","").trim();if(e.isEmpty())e=client.optString("validityDate","").trim();return e;}
    private void show(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(8),dp(4),dp(8),dp(5));head.setBackgroundColor(NAVY);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button back=btn("↩️  VOLVER");back.setTextColor(-1);back.setTextSize(16);back.setOnClickListener(v->finish());
        Button edit=btn("✏️  EDITAR");edit.setTextSize(16);edit.setOnClickListener(v->editClient());
        buttons.addView(back,new LinearLayout.LayoutParams(0,dp(50),1));buttons.addView(edit,new LinearLayout.LayoutParams(0,dp(50),1));
        head.addView(buttons);head.addView(headerText("CLIENTE 360º",20));root.addView(head);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(12),dp(12),dp(20));
        body.addView(t("👤 "+cleanHolder(),23,true));
        String id=client.optString("identityNumber",client.optString("holderDni","—"));
        String address=client.optString("address","");
        String addressLine=isBadAddress(address)?"Dirección del asegurado: No indicada":"Dirección del asegurado: "+address;
        body.addView(t("Identificación: "+(id.isEmpty()?"—":id)+"\nTeléfono: "+client.optString("phone","—")+"\nEmail: "+client.optString("email","—")+"\n"+addressLine,16,false));
        addGroup(body,"📦 PRODUCTO / PÓLIZA",client);addInsuredsGroup(body,client);addGroup(body,"📄 DOCUMENTACIÓN",client);addGroup(body,"🔔 VENCIMIENTO / BAJA",client);
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void addGroup(LinearLayout body,String title,JSONObject p){
        body.addView(t(title,18,true));
        String ex=expiry();
        String dateLabel=ex.isEmpty()?"⚠️ VENCIMIENTO: NO DETECTADO":"📅 VENCIMIENTO: "+ex;
        Button product=btn((p.optString("type","Documento")+"  ·  "+p.optString("number","Sin número")+"\n"+dateLabel));
        product.setTextSize(17);product.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);product.setPadding(dp(18),dp(8),dp(18),dp(8));
        if(ex.isEmpty())product.setTextColor(0xff9a3412);
        product.setOnClickListener(v->showProduct(p));body.addView(product,new LinearLayout.LayoutParams(-1,dp(84)));
        JSONArray docs=p.optJSONArray("documentPhotos");if(docs!=null){for(int i=0;i<docs.length();i++){String path=documentPath(docs.opt(i));if(path.isEmpty())continue;Button d=btn("📄 "+new File(path).getName());d.setOnClickListener(v->documentMenu(path));body.addView(d,new LinearLayout.LayoutParams(-1,dp(58)));}}
    }
    private void addInsuredsGroup(LinearLayout body,JSONObject p){
        JSONArray insureds=p.optJSONArray("insureds");if(insureds==null||insureds.length()==0)return;
        body.addView(t("👥 ASEGURADOS DE LA PÓLIZA",18,true));body.addView(t(insureds.length()+" personas · Pulsa una persona para ver sus datos.",14,false));
        for(int i=0;i<insureds.length();i++){
            JSONObject person=insureds.optJSONObject(i);if(person==null)continue;
            String name=person.optString("name",person.optString("holder","Asegurado"));
            String id=person.optString("identityNumber","");
            Button row=btn("👤  "+(i+1)+". "+name+(id.isEmpty()?"":"\n      DNI/NIE · "+maskIdentity(id))+"\n      Ver datos  ›");
            row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(8),dp(12),dp(8));
            body.addView(row,new LinearLayout.LayoutParams(-1,dp(82)));
            row.setOnClickListener(v->showInsured(person));
        }
    }
    private String maskIdentity(String id){String x=id==null?"":id.trim();if(x.length()<=4)return x;return "••••"+x.substring(x.length()-4);}
    private void showInsured(JSONObject p){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),0,dp(8),0);
        box.addView(t("DATOS PERSONALES",15,true));
        box.addView(t("Nombre y apellidos\n"+p.optString("name",p.optString("holder","—")),17,false));
        box.addView(t("DNI / NIE\n"+p.optString("identityNumber","—"),17,false));
        box.addView(t("Fecha de nacimiento\n"+p.optString("birthDate","—"),17,false));
        box.addView(t("Sexo\n"+p.optString("sex","—"),17,false));
        box.addView(t("DATOS DE LA PÓLIZA",15,true));
        box.addView(t("Fecha de efecto decesos\n"+p.optString("effectiveDeathDate","—"),17,false));
        String capital=p.optString("deathCapital",p.optString("capitalDecesos",""));
        if(!capital.isEmpty())box.addView(t("Capital de fallecimiento\n"+capital,17,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        new AlertDialog.Builder(this).setTitle(p.optString("name","Asegurado")).setView(scroll).setPositiveButton("Cerrar",null).show();
    }
    private String documentPath(Object item){if(item==null||item==JSONObject.NULL)return "";if(item instanceof JSONObject)return ((JSONObject)item).optString("path","");return String.valueOf(item);}
    private void showProduct(JSONObject p){String ex=expiry();new AlertDialog.Builder(this).setTitle("Producto / póliza").setMessage("Tipo: "+p.optString("type","—")+"\nNúmero: "+p.optString("number","—")+"\nTitular: "+cleanHolder()+"\nVencimiento: "+(ex.isEmpty()?"NO DETECTADO — REVISAR DOCUMENTO":ex)+"\nAsegurados: "+p.optInt("insuredCount",0)).setPositiveButton("Cerrar",null).show();}
    private void editClient(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(8),0,dp(8),0);
        EditText holder=field("Titular",client.optString("holder","")),name=field("Nombre",client.optString("name","")),surname=field("Apellidos",client.optString("surname","")),identity=field("DNI / NIE",client.optString("identityNumber",client.optString("holderDni",""))),cif=field("CIF",client.optString("cif","")),phone=field("Teléfono",client.optString("phone","")),email=field("Correo electrónico",client.optString("email","")),address=field("Dirección del asegurado",isBadAddress(client.optString("address",""))?"":client.optString("address","")),birth=field("Fecha de nacimiento",client.optString("birthDate","")),type=field("Tipo",client.optString("type","")),number=field("Número de póliza / documento",client.optString("number","")),expiryField=field("Fecha de vencimiento",expiry()),nationality=field("Nacionalidad",client.optString("nationality","")),sex=field("Sexo",client.optString("sex","")),birthPlace=field("Lugar de nacimiento",client.optString("birthPlace",""));
        EditText[] fields=new EditText[]{holder,name,surname,identity,cif,phone,email,address,birth,type,number,expiryField,nationality,sex,birthPlace};for(EditText e:fields)form.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Editar cliente").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(ignored->{Button saveButton=dialog.getButton(AlertDialog.BUTTON_POSITIVE);saveButton.setOnClickListener(v->saveEditedClient(dialog,holder,name,surname,identity,cif,phone,email,address,birth,type,number,expiryField,nationality,sex,birthPlace));});dialog.show();
    }
    private void saveEditedClient(AlertDialog dialog,EditText holder,EditText name,EditText surname,EditText identity,EditText cif,EditText phone,EditText email,EditText address,EditText birth,EditText type,EditText number,EditText expiryField,EditText nationality,EditText sex,EditText birthPlace){try{put(client,"holder",holder.getText().toString());put(client,"name",name.getText().toString());put(client,"surname",surname.getText().toString());put(client,"identityNumber",identity.getText().toString());put(client,"holderDni",identity.getText().toString());put(client,"cif",cif.getText().toString());put(client,"phone",phone.getText().toString());put(client,"email",email.getText().toString());put(client,"address",address.getText().toString());put(client,"birthDate",birth.getText().toString());put(client,"type",type.getText().toString());put(client,"number",number.getText().toString());put(client,"expiry",expiryField.getText().toString());put(client,"validityDate",expiryField.getText().toString());put(client,"nationality",nationality.getText().toString());put(client,"sex",sex.getText().toString());put(client,"birthPlace",birthPlace.getText().toString());client.put("updatedAt",System.currentTimeMillis());if(saveClient(client)){dialog.dismiss();show();Toast.makeText(this,"✅ Datos del cliente guardados",Toast.LENGTH_LONG).show();}else Toast.makeText(this,"No se encontró el cliente original",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"No se pudieron guardar los cambios",Toast.LENGTH_LONG).show();}}
    private void put(JSONObject o,String k,String v)throws Exception{String x=v==null?"":v.trim();if(x.isEmpty())o.remove(k);else o.put(k,x);}
    private boolean saveClient(JSONObject edited){try{android.content.SharedPreferences prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);JSONArray a=new JSONArray(prefs.getString("policies","[]"));int best=-1,bestScore=0;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;int score=0;score+=same(p,"identityNumber",edited.optString("identityNumber"),3);score+=same(p,"holderDni",edited.optString("holderDni"),3);score+=same(p,"number",edited.optString("number"),2);score+=same(p,"email",edited.optString("email"),2);score+=same(p,"phone",edited.optString("phone"),2);score+=same(p,"holder",edited.optString("holder"),1);score+=same(p,"createdAt",edited.optString("createdAt"),4);if(score>bestScore){bestScore=score;best=i;}}if(best<0)return false;a.put(best,edited);prefs.edit().putString("policies",a.toString()).apply();return true;}catch(Exception e){return false;}}
    private int same(JSONObject a,String key,String value,int weight){if(value==null||value.trim().isEmpty())return 0;String av=a.optString(key,"").trim();return av.equalsIgnoreCase(value.trim())?weight:0;}
    private void documentMenu(String path){new AlertDialog.Builder(this).setTitle("Documento").setItems(new String[]{"👁️ Abrir / ver","⬇️ Descargar","📤 Compartir"},(d,w)->{if(w==0)open(path);else if(w==1)download(path);else share(path);}).show();}
    private Uri uri(String path){File f=new File(path);if(!f.exists())return null;try{return FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);}catch(Exception e){return null;}}
    private void open(String path){Uri u=uri(path);if(u==null){toast("No se puede acceder al archivo para compartirlo");return;}Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,mime(path));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(Exception e){toast("No hay una aplicación compatible para abrir este documento");}}
    private void share(String path){Uri u=uri(path);if(u==null){toast("No se puede compartir este archivo desde su ubicación actual");return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime(path));i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{startActivity(Intent.createChooser(i,"Compartir documento"));}catch(Exception e){toast("No hay aplicaciones disponibles para compartir este documento");}}
    private void download(String path){File src=new File(path);if(!src.exists()){toast("No se encuentra el documento");return;}try{if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,src.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime(path));v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("No se pudo crear la descarga");try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,v,null,null);}else{File d=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);if(!d.exists())d.mkdirs();try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(new File(d,src.getName()))){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}}toast("Documento descargado");}catch(Exception e){toast("No se pudo descargar: "+e.getMessage());}}
    private String mime(String p){String x=p.toLowerCase();if(x.endsWith(".pdf"))return "application/pdf";if(x.endsWith(".png"))return "image/png";if(x.endsWith(".webp"))return "image/webp";return "image/jpeg";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
