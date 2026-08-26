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
    // Cliente 360: edición y gestión de documentos.
    private static final int NAVY=0xff0c2343, BLUE=0xff1985e0, BG=0xfff7f9fc, TEXT=0xff1c2736;
    private JSONObject client;

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView t(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(TEXT);v.setTypeface(null,b?1:0);v.setPadding(dp(12),dp(8),dp(12),dp(8));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);return b;}
    private EditText field(String label,String value){EditText e=new EditText(this);e.setHint(label);e.setSingleLine(true);e.setText(value==null?"":value);e.setTextSize(16);e.setPadding(dp(14),0,dp(14),0);return e;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        String raw=getIntent().getStringExtra("client_json");
        try{client=new JSONObject(raw==null?"{}":raw);show();}
        catch(Exception e){Toast.makeText(this,"No se pudo abrir el cliente",Toast.LENGTH_LONG).show();finish();}
    }

    private void render(){show();}
    private void show(JSONObject p){client=p==null?new JSONObject():p;show();}

    private void show(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(10),dp(8),dp(10),dp(8));head.setBackgroundColor(NAVY);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button back=btn("↩️ VOLVER");back.setTextColor(-1);back.setTextSize(17);back.setOnClickListener(v->finish());
        Button edit=btn("✏️ EDITAR");edit.setTextSize(16);edit.setOnClickListener(v->editClient());
        buttons.addView(back,new LinearLayout.LayoutParams(0,dp(58),1));buttons.addView(edit,new LinearLayout.LayoutParams(0,dp(58),1));
        head.addView(buttons);head.addView(t("🔵 CLIENTE 360º",22,true));root.addView(head);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(12),dp(12),dp(20));
        body.addView(t("👤 "+client.optString("holder",client.optString("name","Cliente")),23,true));
        String id=client.optString("identityNumber",client.optString("holderDni","—"));
        body.addView(t("DNI/NIE: "+id+"\nFecha de nacimiento: "+client.optString("birthDate","—")+"\nDirección: "+client.optString("address","—")+"\nTeléfono: "+client.optString("phone","—"),16,false));
        addGroup(body,"📦 PÓLIZAS",client);addGroup(body,"📄 DOCUMENTACIÓN",client);
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }


    private void addGroup(LinearLayout body,String title,JSONObject p){
        body.addView(t(title,18,true));
        if(title.contains("PÓLIZAS")){
            JSONArray ps=p.optJSONArray("policies");
            if(ps!=null) for(int i=0;i<ps.length();i++){
                JSONObject pol=ps.optJSONObject(i);if(pol==null)continue;
                Button product=btn("▣ Póliza · "+pol.optString("number","Sin número"));
                product.setOnClickListener(v->showProduct(pol));body.addView(product,new LinearLayout.LayoutParams(-1,dp(60)));
            }
        }
        if(title.contains("DOCUMENTACIÓN")){
            JSONArray docs=p.optJSONArray("documentPhotos");
            if(docs!=null) for(int i=0;i<docs.length();i++){
                String path=documentPath(docs.opt(i));if(path.isEmpty())continue;
                Button d=btn("📄 "+new File(path).getName());d.setOnClickListener(v->documentMenu(path));body.addView(d,new LinearLayout.LayoutParams(-1,dp(58)));
            }
        }
    }


    private String documentPath(Object item){if(item==null||item==JSONObject.NULL)return "";if(item instanceof JSONObject)return ((JSONObject)item).optString("path","");return String.valueOf(item);}
    private void showProduct(JSONObject p){new AlertDialog.Builder(this).setTitle("Producto / póliza").setMessage("Tipo: "+p.optString("type","—")+"\nNúmero: "+p.optString("number","—")+"\nTitular: "+p.optString("holder","—")+"\nVencimiento: "+p.optString("expiry",p.optString("validityDate","—"))).setPositiveButton("Cerrar",null).show();}

    private void editClient(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(8),0,dp(8),0);
        EditText holder=field("Nombre y apellidos",client.optString("holder",client.optString("name","")));
        EditText identity=field("DNI / NIE",client.optString("identityNumber",client.optString("holderDni","")));
        EditText birth=field("Fecha de nacimiento",client.optString("birthDate",""));
        EditText address=field("Dirección",client.optString("address",""));
        EditText phone=field("Teléfono",client.optString("phone",""));
        EditText[] fields=new EditText[]{holder,identity,birth,address,phone};
        for(EditText e:fields)form.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Editar cliente").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try{
                client.put("holder",holder.getText().toString().trim());
                client.put("name",holder.getText().toString().trim());client.put("surname","");
                client.put("identityNumber",identity.getText().toString().trim().toUpperCase(java.util.Locale.ROOT));
                client.put("holderDni",identity.getText().toString().trim().toUpperCase(java.util.Locale.ROOT));
                client.put("birthDate",birth.getText().toString().trim());client.put("address",address.getText().toString().trim());client.put("phone",phone.getText().toString().trim());
                client.put("updatedAt",System.currentTimeMillis());
                if(saveClient(client)){dialog.dismiss();show();Toast.makeText(this,"✅ Datos del cliente guardados",Toast.LENGTH_LONG).show();}
                else Toast.makeText(this,"No se encontró el cliente original",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"No se pudieron guardar los cambios",Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }


    private void saveEditedClient(AlertDialog dialog, EditText holder, EditText name, EditText surname,
                                  EditText identity, EditText cif, EditText phone, EditText email,
                                  EditText address, EditText birth, EditText type, EditText number,
                                  EditText expiry, EditText nationality, EditText sex, EditText birthPlace){
        try{
            put(client,"holder",holder.getText().toString());
            put(client,"name",name.getText().toString());
            put(client,"surname",surname.getText().toString());
            put(client,"identityNumber",identity.getText().toString());
            put(client,"holderDni",identity.getText().toString());
            put(client,"cif",cif.getText().toString());
            put(client,"phone",phone.getText().toString());
            put(client,"email",email.getText().toString());
            put(client,"address",address.getText().toString());
            put(client,"birthDate",birth.getText().toString());
            put(client,"type",type.getText().toString());
            put(client,"number",number.getText().toString());
            put(client,"expiry",expiry.getText().toString());
            put(client,"validityDate",expiry.getText().toString());
            put(client,"nationality",nationality.getText().toString());
            put(client,"sex",sex.getText().toString());
            put(client,"birthPlace",birthPlace.getText().toString());
            client.put("updatedAt",System.currentTimeMillis());
            if(saveClient(client)){
                dialog.dismiss();
                show();
                Toast.makeText(this,"✅ Datos del cliente guardados",Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this,"No se encontró el cliente original",Toast.LENGTH_LONG).show();
            }
        }catch(Exception e){
            Toast.makeText(this,"No se pudieron guardar los cambios",Toast.LENGTH_LONG).show();
        }
    }

    private void put(JSONObject o,String k,String v)throws Exception{String x=v==null?"":v.trim();if(x.isEmpty())o.remove(k);else o.put(k,x);}
    private boolean saveClient(JSONObject edited){try{android.content.SharedPreferences prefs=getSharedPreferences("rgapro_local",MODE_PRIVATE);JSONArray a=new JSONArray(prefs.getString("policies","[]"));int best=-1,bestScore=0;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;int score=0;score+=same(p,"identityNumber",edited.optString("identityNumber"),3);score+=same(p,"holderDni",edited.optString("holderDni"),3);score+=same(p,"number",edited.optString("number"),2);score+=same(p,"email",edited.optString("email"),2);score+=same(p,"phone",edited.optString("phone"),2);score+=same(p,"holder",edited.optString("holder"),1);score+=same(p,"createdAt",edited.optString("createdAt"),4);if(score>bestScore){bestScore=score;best=i;}}if(best<0)return false;a.put(best,edited);prefs.edit().putString("policies",a.toString()).apply();return true;}
    catch(Exception e){return false;}}
    private int same(JSONObject a,String key,String value,int weight){if(value==null||value.trim().isEmpty())return 0;String av=a.optString(key,"").trim();return av.equalsIgnoreCase(value.trim())?weight:0;}

    private void documentMenu(String path){new AlertDialog.Builder(this).setTitle("Documento").setItems(new String[]{"👁️ Abrir / ver","⬇️ Descargar","📤 Compartir"},(d,w)->{if(w==0)open(path);else if(w==1)download(path);else share(path);}).show();}
    private Uri uri(String path){File f=new File(path);if(!f.exists())return null;try{return FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);}catch(Exception e){return null;}}
    private void open(String path){Uri u=uri(path);if(u==null){toast("No se puede acceder al archivo para compartirlo");return;}Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,mime(path));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(Exception e){toast("No hay una aplicación compatible para abrir este documento");}}
    private void share(String path){Uri u=uri(path);if(u==null){toast("No se puede compartir este archivo desde su ubicación actual");return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime(path));i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{startActivity(Intent.createChooser(i,"Compartir documento"));}catch(Exception e){toast("No hay aplicaciones disponibles para compartir este documento");}}
    private void download(String path){File src=new File(path);if(!src.exists()){toast("No se encuentra el documento");return;}try{if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,src.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime(path));v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("No se pudo crear la descarga");try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,v,null,null);}else{File d=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);if(!d.exists())d.mkdirs();try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(new File(d,src.getName()))){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}}toast("Documento descargado");}catch(Exception e){toast("No se pudo descargar: "+e.getMessage());}}
    private String mime(String p){String x=p.toLowerCase();if(x.endsWith(".pdf"))return "application/pdf";if(x.endsWith(".png"))return "image/png";if(x.endsWith(".webp"))return "image/webp";return "image/jpeg";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
