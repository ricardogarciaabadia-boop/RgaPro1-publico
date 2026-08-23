package com.rgapro1.ocaso;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
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
        addHeading(c,box,"IDENTIDAD","Solo los datos personales esenciales del DNI/NIE.");
        EditText name=field(c,"Nombre",d.name), surname=field(c,"Apellidos",d.surname), id=field(c,"DNI / NIE",d.dni), birth=field(c,"Fecha de nacimiento",d.birthDate);
        box.addView(name);box.addView(surname);box.addView(id);box.addView(birth);
        addHeading(c,box,"CONTACTO (OPCIONAL)","Puedes completarlo ahora o después desde la ficha del cliente.");
        EditText phone=field(c,"Teléfono",""); EditText address=field(c,"Dirección",""); EditText email=field(c,"Email","");
        box.addView(phone);box.addView(address);box.addView(email);
        box.addView(label(c,"Documento(s) procesado(s): "+count+" · Confianza OCR: "+d.confidence+"%"));
        ScrollView scroll=new ScrollView(c);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle("Datos detectados por OCR").setView(scroll).setNegativeButton("Descartar",null).setPositiveButton("Guardar cliente",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject out=new JSONObject();out.put("holder",join(value(name),value(surname)));out.put("name",value(name));out.put("surname",value(surname));out.put("identityType",identityType(value(id)));out.put("identityNumber",value(id).toUpperCase(Locale.ROOT));out.put("holderDni",value(id).toUpperCase(Locale.ROOT));out.put("birthDate",value(birth));out.put("phone",value(phone));out.put("address",value(address));out.put("email",value(email));out.put("type",identityType(value(id)).equals("NIE")?"Cliente / NIE":"Cliente / DNI");out.put("number","");out.put("ocrText",raw);cb.onSave(out);dialog.dismiss();}catch(Exception ignored){}}));
        dialog.show();
    }

    private static void showPolicy(Context c,String raw,int count,JSONObject policy,DniOcrParser.Result dni,Callback cb){
        LinearLayout box=box(c);
        addHeading(c,box,"PRODUCTO / PÓLIZA","El producto y el número de póliza son los datos prioritarios.");
        String type=policy.optString("type",""); if(type.isEmpty()) type=inferProduct(raw);
        String number=policy.optString("number",""); if(number.isEmpty()) number=labelNumber(raw);
        EditText product=field(c,"Tipo de producto",type), policyNo=field(c,"Nº de póliza",number);
        box.addView(product);box.addView(policyNo);
        if(number.isEmpty()) addWarning(c,box,"⚠ No se ha identificado con seguridad el número de póliza. Revísalo antes de guardar.");
        addHeading(c,box,"TITULAR / DATOS PERSONALES","No se mezclan los datos de otros asegurados con los del tomador.");
        String holder=policy.optString("holder",""); if(holder.isEmpty()) holder=join(dni.name,dni.surname);
        EditText holderF=field(c,"Nombre y apellidos del titular",holder), dniF=field(c,"DNI / NIE del titular",policy.optString("holderDni",dni.dni)), birth=field(c,"Fecha de nacimiento",dni.birthDate);
        box.addView(holderF);box.addView(dniF);box.addView(birth);
        JSONArray insureds=policy.optJSONArray("insureds");
        if(insureds!=null && insureds.length()>0){
            addHeading(c,box,"ASEGURADOS","Cada asegurado conserva sus propios datos. No se copian al titular.");
            for(int i=0;i<insureds.length();i++){
                JSONObject p=insureds.optJSONObject(i); if(p==null) continue;
                String label="Asegurado "+p.optInt("insuredIndex",i+1)+" · "+p.optString("name","Sin nombre");
                TextView row=label(c,label+"\n"+p.optString("identityNumber","")+" · nacimiento "+p.optString("birthDate","—")+" · sexo "+p.optString("sex","—"));
                row.setTextColor(TEXT); row.setPadding(8,12,8,12); box.addView(row);
            }
        }
        addHeading(c,box,"CONTACTO (OPCIONAL)","Teléfono, dirección y email se completan cuando estén disponibles.");
        EditText phone=field(c,"Teléfono","");EditText address=field(c,"Dirección","");EditText email=field(c,"Email","");box.addView(phone);box.addView(address);box.addView(email);
        box.addView(label(c,"Documento(s) procesado(s): "+count+(insureds!=null?" · Asegurados detectados: "+insureds.length():"")));
        ScrollView scroll=new ScrollView(c);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle("Datos de la póliza").setView(scroll).setNegativeButton("Descartar",null).setPositiveButton("Guardar póliza",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String n=value(policyNo);if(n.isEmpty()){addNoNumberDialog(c,()->savePolicy(dialog,raw,product,policyNo,holderF,dniF,birth,phone,address,email,policy,cb));return;}savePolicy(dialog,raw,product,policyNo,holderF,dniF,birth,phone,address,email,policy,cb);}catch(Exception ignored){}}));
        dialog.show();
    }

    private static void savePolicy(AlertDialog dialog,String raw,EditText product,EditText policyNo,EditText holderF,EditText dniF,EditText birth,EditText phone,EditText address,EditText email,JSONObject policy,Callback cb)throws Exception{
        JSONObject out=new JSONObject();out.put("holder",value(holderF));out.put("name",firstName(value(holderF)));out.put("surname",remainingSurname(value(holderF)));String id=value(dniF).toUpperCase(Locale.ROOT);out.put("identityType",identityType(id));out.put("identityNumber",id);out.put("holderDni",id);out.put("birthDate",value(birth));out.put("phone",value(phone));out.put("address",value(address));out.put("email",value(email));out.put("type",value(product).isEmpty()?"Otros":value(product));out.put("number",value(policyNo));out.put("ocrText",raw);if(policy.has("insureds"))out.put("insureds",policy.getJSONArray("insureds"));if(policy.has("insuredCount"))out.put("insuredCount",policy.getInt("insuredCount"));cb.onSave(out);dialog.dismiss();
    }

    private static void addNoNumberDialog(Context c,Runnable save){new AlertDialog.Builder(c).setTitle("Número de póliza no confirmado").setMessage("No se ha podido identificar con seguridad el número de póliza. Puedes guardar el documento y completarlo después, pero se recomienda revisarlo ahora.").setNegativeButton("Seguir revisando",null).setPositiveButton("Guardar sin número",(d,w)->save.run()).show();}
    private static void addWarning(Context c,LinearLayout b,String s){TextView t=label(c,s);t.setTextColor(Color.rgb(160,90,0));b.addView(t);}
    private static String identityType(String id){return id.toUpperCase(Locale.ROOT).matches("[XYZ][0-9]{7}[A-Z]")?"NIE":(id.isEmpty()?"":"DNI");}
    private static LinearLayout box(Context c){LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(18,4,18,10);return b;}
    private static void addHeading(Context c,LinearLayout b,String title,String sub){TextView t=label(c,title);t.setTextSize(15);t.setTextColor(NAVY);t.setGravity(Gravity.LEFT);b.addView(t);TextView s=label(c,sub);s.setTextColor(MUTED);s.setTextSize(12);b.addView(s);}
    private static TextView label(Context c,String s){TextView t=new TextView(c);t.setText(s);t.setTextColor(TEXT);t.setPadding(4,10,4,4);return t;}
    private static EditText field(Context c,String hint,String value){EditText e=new EditText(c);e.setHint(hint);e.setSingleLine(true);e.setText(value==null?"":value);e.setTextSize(16);e.setPadding(14,0,14,0);e.setMinHeight(54);return e;}
    private static String value(EditText e){return e.getText().toString().trim();}
    private static String join(String a,String b){return (a+" "+b).trim();}
    private static String firstName(String s){String[] p=s.trim().split("\\s+");return p.length==0?"":p[0];}
    private static String remainingSurname(String s){String[] p=s.trim().split("\\s+");if(p.length<=1)return "";StringBuilder b=new StringBuilder();for(int i=1;i<p.length;i++){if(i>1)b.append(' ');b.append(p[i]);}return b.toString();}
    private static String inferProduct(String raw){String u=raw.toUpperCase(Locale.ROOT);String[] markers={"DECESOS INTEGRAL","ASISTENCIA FAMILIAR XXI","ACCIDENTES DE LA MUJER","AHORRO GARANTIZADO FLEXIBLE","OCASO COMUNIDADES","OCASO HOGAR SENIOR","OCASO HOGAR PROTECCION","OCASO HOGAR"};for(String m:markers)if(u.contains(m))return m.equals("DECESOS INTEGRAL")?"Decesos Integral":m.equals("ASISTENCIA FAMILIAR XXI")?"Asistencia Familiar XXI":m.equals("ACCIDENTES DE LA MUJER")?"Ocaso Accidentes de la Mujer":m.equals("AHORRO GARANTIZADO FLEXIBLE")?"Ocaso Ahorro Garantizado Flexible":m.equals("OCASO COMUNIDADES")?"Ocaso Comunidades":m.equals("OCASO HOGAR SENIOR")?"Ocaso Hogar Senior":m.equals("OCASO HOGAR PROTECCION")?"Ocaso Hogar Protección":"Ocaso Hogar";return u.contains("TOMADOR")?"Póliza":"Otros";}
    private static String labelNumber(String raw){Pattern p=Pattern.compile("(?i)(?:N[º°.]?\\s*(?:DE\\s*)?P[ÓO]LIZA|P[ÓO]LIZA\\s*(?:N[º°.]?|NUM(?:ERO)?))\\s*[:#-]?\\s*([A-Z0-9./_-]{5,})");Matcher m=p.matcher(raw==null?"":raw);while(m.find()){String v=m.group(1).trim();if(!v.matches("(?i)(DE|SEGURO|OCASO|POLIZA|PÓLIZA)")&&!v.contains(" "))return v;}return "";}
}
