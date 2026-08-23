package com.rgapro1.ocaso;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Focused OCR confirmation UI: identity fields for DNI, product/policy fields for policies. */
public final class OcrReviewDialog {
    public interface Callback { void onSave(JSONObject data); }
    private static final int NAVY=Color.rgb(12,35,67), BLUE=Color.rgb(25,133,224), TEXT=Color.rgb(28,39,54), MUTED=Color.rgb(103,115,132);
    private OcrReviewDialog() {}

    public static void show(Context context, String raw, int documentCount, Callback callback) {
        String text=raw==null?"":raw;
        DniOcrParser.Result dni=DniOcrParser.parse(text);
        JSONObject policy=PolicyOcrParser.parse(text);
        boolean looksPolicy=isPolicy(text,policy);
        if (looksPolicy) showPolicy(context,text,documentCount,policy,dni,callback);
        else showDni(context,text,documentCount,dni,callback);
    }

    private static boolean isPolicy(String text, JSONObject policy) {
        String u=text.toUpperCase(Locale.ROOT);
        return policy.has("type") || policy.has("number") || u.contains("PÓLIZA") || u.contains("POLIZA") || u.contains("TOMADOR DEL SEGURO") || u.contains("TOMADOR");
    }

    private static void showDni(Context c,String raw,int count,DniOcrParser.Result d,Callback cb){
        LinearLayout box=box(c);
        addHeading(c,box,"IDENTIDAD", "Solo los datos personales esenciales del DNI/NIE.");
        EditText name=field(c,"Nombre",d.name), surname=field(c,"Apellidos",d.surname), id=field(c,"DNI / NIE",d.dni), birth=field(c,"Fecha de nacimiento",d.birthDate);
        box.addView(name);box.addView(surname);box.addView(id);box.addView(birth);
        addHeading(c,box,"CONTACTO (OPCIONAL)","Puedes completarlo ahora o después desde la ficha del cliente.");
        EditText phone=field(c,"Teléfono",""); EditText address=field(c,"Dirección",""); EditText email=field(c,"Email","");
        box.addView(phone);box.addView(address);box.addView(email);
        TextView info=label(c,"Documento(s) procesado(s): "+count+" · Confianza OCR: "+d.confidence+"%");box.addView(info);
        ScrollView scroll=new ScrollView(c);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle("Datos detectados por OCR").setView(scroll).setNegativeButton("Descartar",null).setPositiveButton("Guardar cliente",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject out=new JSONObject();out.put("holder",join(name.getText().toString(),surname.getText().toString()));out.put("name",value(name));out.put("surname",value(surname));out.put("identityType",id.getText().toString().trim().toUpperCase(Locale.ROOT).matches("[XYZ].*")?"NIE":"DNI");out.put("identityNumber",value(id).toUpperCase(Locale.ROOT));out.put("holderDni",value(id).toUpperCase(Locale.ROOT));out.put("birthDate",value(birth));out.put("phone",value(phone));out.put("address",value(address));out.put("email",value(email));out.put("type",id.getText().toString().trim().toUpperCase(Locale.ROOT).matches("[XYZ].*")?"Cliente / NIE":"Cliente / DNI");out.put("number","");out.put("ocrText",raw);cb.onSave(out);dialog.dismiss();}catch(Exception ignored){}}));
        dialog.show();
    }

    private static void showPolicy(Context c,String raw,int count,JSONObject policy,DniOcrParser.Result dni,Callback cb){
        LinearLayout box=box(c);
        addHeading(c,box,"PRODUCTO / PÓLIZA","Estos son los datos que identifican el producto. El número de póliza es prioritario.");
        String type=policy.optString("type",""); if(type.isEmpty()) type=inferProduct(raw);
        String number=policy.optString("number",""); if(number.isEmpty()) number=labelNumber(raw);
        EditText product=field(c,"Tipo de producto",type); EditText policyNo=field(c,"Nº de póliza",number);
        box.addView(product);box.addView(policyNo);
        addHeading(c,box,"TITULAR / DATOS PERSONALES","Se conserva la identificación del titular cuando aparece en el documento.");
        String holder=policy.optString("holder",""); if(holder.isEmpty()) holder=join(dni.name,dni.surname);
        EditText holderF=field(c,"Nombre y apellidos del titular",holder); EditText dniF=field(c,"DNI / NIE del titular",policy.optString("holderDni",dni.dni)); EditText birth=field(c,"Fecha de nacimiento",dni.birthDate);
        box.addView(holderF);box.addView(dniF);box.addView(birth);
        addHeading(c,box,"CONTACTO (OPCIONAL)","Teléfono, dirección y email se completan cuando estén disponibles.");
        EditText phone=field(c,"Teléfono","");EditText address=field(c,"Dirección","");EditText email=field(c,"Email","");box.addView(phone);box.addView(address);box.addView(email);
        TextView info=label(c,"Documento(s) procesado(s): "+count+(policy.has("insuredCount")?" · Asegurados detectados: "+policy.optInt("insuredCount"):""));box.addView(info);
        ScrollView scroll=new ScrollView(c);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle("Datos de la póliza").setView(scroll).setNegativeButton("Descartar",null).setPositiveButton("Guardar póliza",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject out=new JSONObject();out.put("holder",value(holderF));out.put("name",firstName(value(holderF)));out.put("surname",remainingSurname(value(holderF)));String id=value(dniF).toUpperCase(Locale.ROOT);out.put("identityType",id.matches("[XYZ].*")?"NIE":"DNI");out.put("identityNumber",id);out.put("holderDni",id);out.put("birthDate",value(birth));out.put("phone",value(phone));out.put("address",value(address));out.put("email",value(email));out.put("type",value(product).isEmpty()?"Otros":value(product));out.put("number",value(policyNo));out.put("ocrText",raw);if(policy.has("insureds"))out.put("insureds",policy.getJSONArray("insureds"));if(policy.has("insuredCount"))out.put("insuredCount",policy.getInt("insuredCount"));cb.onSave(out);dialog.dismiss();}catch(Exception ignored){}}));
        dialog.show();
    }

    private static LinearLayout box(Context c){LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(18,4,18,10);return b;}
    private static void addHeading(Context c,LinearLayout b,String title,String sub){TextView t=label(c,title);t.setTextSize(15);t.setTextColor(NAVY);t.setGravity(Gravity.LEFT);b.addView(t);TextView s=label(c,sub);s.setTextColor(MUTED);s.setTextSize(12);b.addView(s);}
    private static TextView label(Context c,String s){TextView t=new TextView(c);t.setText(s);t.setTextColor(TEXT);t.setPadding(4,10,4,4);return t;}
    private static EditText field(Context c,String hint,String value){EditText e=new EditText(c);e.setHint(hint);e.setSingleLine(true);e.setText(value==null?"":value);e.setTextSize(16);e.setPadding(14,0,14,0);e.setMinHeight(54);return e;}
    private static String value(EditText e){return e.getText().toString().trim();}
    private static String join(String a,String b){return (a+" "+b).trim();}
    private static String firstName(String s){String[] p=s.trim().split("\\s+");return p.length==0?"":p[0];}
    private static String remainingSurname(String s){String[] p=s.trim().split("\\s+");if(p.length<=1)return "";StringBuilder b=new StringBuilder();for(int i=1;i<p.length;i++){if(i>1)b.append(' ');b.append(p[i]);}return b.toString();}
    private static String inferProduct(String raw){String u=raw.toUpperCase(Locale.ROOT);int p=u.indexOf("PÓLIZA DE SEGURO");if(p<0)p=u.indexOf("POLIZA DE SEGURO");if(p>=0){String line=raw.substring(p).split("\\r?\\n")[0].trim();return line.length()>70?line.substring(0,70):line;}return u.contains("TOMADOR")?"Póliza":"Otros";}
    private static String labelNumber(String raw){Pattern p=Pattern.compile("(?:N[º°.]?\\s*(?:DE\\s*)?P[ÓO]LIZA|P[ÓO]LIZA\\s*(?:N[º°.]?|NUM(?:ERO)?))\\s*[:#-]?\\s*([A-Z0-9./_-]{5,})",Pattern.CASE_INSENSITIVE);Matcher m=p.matcher(raw==null?"":raw);return m.find()?m.group(1).trim():"";}
}
