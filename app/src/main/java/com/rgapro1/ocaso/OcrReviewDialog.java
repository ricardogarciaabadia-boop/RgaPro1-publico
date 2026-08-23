package com.rgapro1.ocaso;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Clean OCR confirmation UI. Shows only information useful for client and policy management. */
public final class OcrReviewDialog {
    public interface Callback { void onSave(JSONObject data); }
    private static final int NAVY=Color.rgb(12,35,67), BLUE=Color.rgb(25,133,224), TEXT=Color.rgb(28,39,54), MUTED=Color.rgb(103,115,132), LINE=Color.rgb(225,230,237), WARNING=Color.rgb(160,90,0);
    private OcrReviewDialog() {}

    public static void show(Context context,String raw,int documentCount,Callback callback){
        String text=raw==null?"":raw;
        DniOcrParser.Result dni=DniOcrParser.parse(text);
        JSONObject policy=PolicyOcrParser.parse(text);
        if(isPolicy(text,policy)) showPolicy(context,text,documentCount,policy,dni,callback); else showDni(context,text,documentCount,dni,callback);
    }

    private static boolean isPolicy(String text,JSONObject policy){
        String u=text.toUpperCase(Locale.ROOT);
        return policy.has("type")||policy.has("number")||u.contains("PÓLIZA")||u.contains("POLIZA")||u.contains("TOMADOR")||u.contains("ASEGURADO");
    }

    private static void showDni(Context c,String raw,int count,DniOcrParser.Result d,Callback cb){
        LinearLayout box=box(c);
        addTitle(c,box,"IDENTIDAD","Revisa los 4 datos esenciales antes de guardar.");
        EditText name=field(c,"Nombre",d.name), surname=field(c,"Apellidos",d.surname), id=field(c,"DNI / NIE",d.dni), birth=field(c,"Fecha de nacimiento",d.birthDate);
        box.addView(name); box.addView(surname); box.addView(id); box.addView(birth);
        addTitle(c,box,"CONTACTO","Opcional · puedes completarlo después.");
        EditText phone=field(c,"Teléfono",""),address=field(c,"Dirección",""),email=field(c,"Email","");
        box.addView(phone); box.addView(address); box.addView(email);
        returnDialog(c,"Datos del cliente","Guardar cliente",box,()->{
            try{JSONObject out=new JSONObject();String n=value(name),s=value(surname),i=value(id).toUpperCase(Locale.ROOT);out.put("holder",join(n,s));out.put("name",n);out.put("surname",s);out.put("identityType",identityType(i));out.put("identityNumber",i);out.put("holderDni",i);out.put("birthDate",value(birth));out.put("phone",value(phone));out.put("address",value(address));out.put("email",value(email));out.put("type",identityType(i).equals("NIE")?"Cliente / NIE":"Cliente / DNI");out.put("number","");out.put("ocrText",raw);return out;}catch(Exception e){return null;}
        },cb);
    }

    private static void showPolicy(Context c,String raw,int count,JSONObject policy,DniOcrParser.Result dni,Callback cb){
        LinearLayout box=box(c);
        String type=policy.optString("type","");
        String number=policy.optString("number","");
        String holder=policy.optString("holder","");
        if(holder.isEmpty()) holder=join(dni.name,dni.surname);
        String holderDni=policy.optString("holderDni",dni.dni);
        String birth=dni.birthDate;

        addTitle(c,box,"PÓLIZA","Solo los datos que necesitas para identificarla.");
        EditText product=importantField(c,"TIPO DE PRODUCTO",type.isEmpty()?"No identificado":type);
        EditText policyNo=importantField(c,"Nº DE PÓLIZA",number);
        box.addView(product); box.addView(policyNo);
        if(type.isEmpty()) addWarning(c,box,"Revisa el producto: no se ha identificado con seguridad.");
        if(number.isEmpty()) addWarning(c,box,"Revisa el número: no se ha encontrado una etiqueta clara de Nº de póliza.");

        addTitle(c,box,"TITULAR","Datos personales del tomador/titular. No se mezclan con otros asegurados.");
        EditText holderF=field(c,"Nombre y apellidos",holder), dniF=field(c,"DNI / NIE",holderDni), birthF=field(c,"Fecha de nacimiento",birth);
        box.addView(holderF); box.addView(dniF); box.addView(birthF);

        JSONArray insureds=policy.optJSONArray("insureds");
        if(insureds!=null&&insureds.length()>0){
            addTitle(c,box,"ASEGURADOS ("+insureds.length()+")","Cada persona mantiene sus propios datos.");
            for(int i=0;i<insureds.length();i++){
                JSONObject p=insureds.optJSONObject(i); if(p==null) continue;
                addInsuredCard(c,box,p,i+1);
            }
        }

        addTitle(c,box,"CONTACTO","Opcional · se puede completar después desde el cliente.");
        EditText phone=field(c,"Teléfono",""),address=field(c,"Dirección",""),email=field(c,"Email","");
        box.addView(phone); box.addView(address); box.addView(email);

        returnDialog(c,"Revisar póliza","Guardar póliza",box,()->{
            try{JSONObject out=new JSONObject();String id=value(dniF).toUpperCase(Locale.ROOT);out.put("holder",value(holderF));out.put("name",firstName(value(holderF)));out.put("surname",remainingSurname(value(holderF)));out.put("identityType",identityType(id));out.put("identityNumber","");out.put("holderDni",id);out.put("birthDate",value(birthF));out.put("phone",value(phone));out.put("address",value(address));out.put("email",value(email));out.put("type",value(product).isEmpty()?"Otros":value(product));out.put("number",value(policyNo));out.put("ocrText",raw);if(policy.has("insureds"))out.put("insureds",policy.getJSONArray("insureds"));if(policy.has("insuredCount"))out.put("insuredCount",policy.getInt("insuredCount"));return out;}catch(Exception e){return null;}
        },cb);
    }

    private static void addInsuredCard(Context c,LinearLayout box,JSONObject p,int fallbackIndex){
        LinearLayout card=new LinearLayout(c); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8)); card.setBackground(round(Color.WHITE,14));
        String index=String.valueOf(p.optInt("insuredIndex",fallbackIndex));
        TextView title=label(c,"ASEGURADO "+index+"",16,NAVY,true); card.addView(title);
        card.addView(label(c,p.optString("name","Sin nombre"),17,TEXT,true));
        String id=p.optString("identityNumber",""); if(!id.isEmpty()) card.addView(label(c,"DNI / NIE: "+id,14,TEXT,false));
        String bd=p.optString("birthDate",""); if(!bd.isEmpty()) card.addView(label(c,"Nacimiento: "+bd,14,TEXT,false));
        String sex=p.optString("sex",""); if(!sex.isEmpty()) card.addView(label(c,"Sexo: "+sex,14,TEXT,false));
        String eff=p.optString("effectiveDeathDate",""); if(!eff.isEmpty()) card.addView(label(c,"Efecto decesos: "+eff,14,MUTED,false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT);lp.bottomMargin=dp(c,8);box.addView(card,lp);
    }

    private interface DataFactory{JSONObject build();}
    private static void returnDialog(Context c,String title,String saveText,LinearLayout box,DataFactory factory,Callback cb){
        ScrollView scroll=new ScrollView(c); scroll.setFillViewport(true); scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(title).setView(scroll).setNegativeButton("Descartar",null).setPositiveButton(saveText,null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{JSONObject out=factory.build();if(out==null){Toast.makeText(c,"No se pudieron preparar los datos",Toast.LENGTH_SHORT).show();return;}if("Guardar póliza".equals(saveText)&&out.optString("number","").trim().isEmpty()){addNoNumberDialog(c,()->{cb.onSave(out);dialog.dismiss();});return;}cb.onSave(out);dialog.dismiss();}));
        dialog.show();
    }

    private static void addNoNumberDialog(Context c,Runnable save){new AlertDialog.Builder(c).setTitle("Número de póliza no confirmado").setMessage("No se ha identificado un número junto a una etiqueta clara de póliza. Puedes guardarla sin número y completarlo después.").setNegativeButton("Seguir revisando",null).setPositiveButton("Guardar sin número",(d,w)->save.run()).show();}
    private static void addWarning(Context c,LinearLayout b,String s){TextView t=label(c,s,13,WARNING,true);t.setPadding(dp(c,4),dp(c,8),dp(c,4),dp(c,8));b.addView(t);}
    private static void addTitle(Context c,LinearLayout b,String title,String sub){b.addView(label(c,title,16,NAVY,true));b.addView(label(c,sub,12,MUTED,false));}
    private static LinearLayout box(Context c){LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(c,18),dp(c,6),dp(c,18),dp(c,18));return b;}
    private static TextView label(Context c,String s,float size,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);t.setPadding(dp(c,4),dp(c,5),dp(c,4),dp(c,5));return t;}
    private static EditText field(Context c,String hint,String value){EditText e=new EditText(c);e.setHint(hint);e.setSingleLine(true);e.setText(value==null?"":value);e.setTextSize(16);e.setTextColor(TEXT);e.setPadding(dp(c,14),0,dp(c,14),0);e.setMinHeight(dp(c,52));e.setBackground(round(Color.WHITE,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(c,56));p.bottomMargin=dp(c,7);e.setLayoutParams(p);return e;}
    private static EditText importantField(Context c,String hint,String value){EditText e=field(c,hint,value);e.setTextSize(18);e.setTypeface(Typeface.DEFAULT,Typeface.BOLD);e.setTextColor(NAVY);return e;}
    private static GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);g.setStroke(1,LINE);return g;}
    private static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    private static String value(EditText e){return e.getText().toString().trim();}
    private static String join(String a,String b){return(a+" "+b).trim();}
    private static String firstName(String s){String[] p=s.trim().split("\\s+");return p.length==0?"":p[0];}
    private static String remainingSurname(String s){String[] p=s.trim().split("\\s+");if(p.length<=1)return"";StringBuilder b=new StringBuilder();for(int i=1;i<p.length;i++){if(i>1)b.append(' ');b.append(p[i]);}return b.toString();}
    private static String identityType(String id){return id.toUpperCase(Locale.ROOT).matches("[XYZ][0-9]{7}[A-Z]")?"NIE":(id.isEmpty()?"":"DNI");}
}
