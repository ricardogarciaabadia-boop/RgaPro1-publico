package com.rgapro1.ocaso;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Repairs OCR data and keeps all documents for the same person under one client. */
public final class ClientAutoLinker {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Pattern DNI = Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CIF = Pattern.compile("\\b[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+34\\s*)?[6789]\\d{8}(?!\\d)");
    private static final Pattern POLICY = Pattern.compile("(?:N[º°.]?\\s*)?(?:DE\\s*)?(?:P[ÓO]LIZA|POLIZA)\\s*(?:N[º°.]?|NUM(?:ERO)?)?\\s*[:#-]?\\s*([A-Z0-9./_-]{5,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\b\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{2,4}\\b");

    private ClientAutoLinker() {}

    public static void start(final Context context) {
        final Context app = context.getApplicationContext();
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> { repair(app); MAIN.postDelayed(tick[0], 2500L); };
        MAIN.post(tick[0]);
    }

    public static void repair(final Context context) {
        if (!RUNNING.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("rgapro_local", Context.MODE_PRIVATE);
                JSONArray source = new JSONArray(prefs.getString("policies", "[]"));
                enhanceSavedImages(context, source);
                for (int i = 0; i < source.length(); i++) enrich(source.optJSONObject(i));
                merge(source);
                prefs.edit().putString("policies", source.toString()).apply();
            } catch (Exception ignored) {
            } finally {
                RUNNING.set(false);
            }
        }, "rgapro-auto-link").start();
    }

    /** Re-runs OCR using the original full-resolution saved image. */
    private static void enhanceSavedImages(Context context, JSONArray source) {
        int processed = 0;
        for (int i = 0; i < source.length() && processed < 8; i++) {
            JSONObject client = source.optJSONObject(i);
            if (client == null) continue;
            JSONArray docs = client.optJSONArray("documentPhotos");
            if (docs == null) continue;
            for (int j = 0; j < docs.length() && processed < 8; j++) {
                JSONObject doc = docs.optJSONObject(j);
                if (doc == null || doc.optBoolean("ocrEnhanced", false)) continue;
                String path = doc.optString("path", "");
                try {
                    if (path.isEmpty() || path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        doc.put("ocrEnhanced", true);
                        continue;
                    }
                    File f = new File(path);
                    if (!f.exists()) {
                        doc.put("ocrEnhanced", true);
                        continue;
                    }
                    InputImage image = InputImage.fromFilePath(context, Uri.fromFile(f));
                    TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    String text;
                    try { text = Tasks.await(recognizer.process(image)).getText(); }
                    finally { recognizer.close(); }
                    if (text != null && !text.trim().isEmpty()) {
                        String old = client.optString("ocrText", "");
                        if (!old.contains(text.trim())) client.put("ocrText", (old.isEmpty() ? "" : old + "\n\n--- OCR SEGUNDA PASADA ---\n") + text.trim());
                    }
                    doc.put("ocrEnhanced", true);
                    processed++;
                } catch (Exception ignored) {
                    // Leave it pending so the next cycle can retry.
                }
            }
        }
    }

    private static void enrich(JSONObject p) {
        if (p == null) return;
        String text = p.optString("ocrText", "");
        if (text.isEmpty()) return;
        String upper = text.toUpperCase(Locale.ROOT);
        String id = first(DNI, upper);
        String cif = first(CIF, upper);
        if (empty(p, "identityNumber") && !id.isEmpty()) { put(p, "identityNumber", id); put(p, "holderDni", id); put(p, "identityType", id.matches("[XYZ].*") ? "NIE" : "DNI"); }
        if (empty(p, "cif") && !cif.isEmpty()) put(p, "cif", cif);
        if (empty(p, "email")) put(p, "email", first(EMAIL, text));
        if (empty(p, "phone")) put(p, "phone", first(PHONE, text));
        if (empty(p, "number")) put(p, "number", policyNumber(upper));
        if (empty(p, "surname")) put(p, "surname", labelValue(upper, "APELLIDOS", "APELLIDOS Y NOMBRE"));
        if (empty(p, "name")) put(p, "name", labelValue(upper, "NOMBRE", "ASEGURADO", "CLIENTE"));
        if (empty(p, "holder")) {
            String holder = labelValue(upper, "TITULAR", "TOMADOR", "ASEGURADO", "CLIENTE");
            String name = p.optString("name", "").trim(), surname = p.optString("surname", "").trim();
            if (!name.isEmpty() || !surname.isEmpty()) holder = (name + " " + surname).trim();
            if (!holder.isEmpty()) put(p, "holder", holder);
        }
        if (empty(p, "address")) put(p, "address", labelValue(upper, "DOMICILIO", "DIRECCION", "DIRECCIÓN"));
        if (empty(p, "birthDate")) put(p, "birthDate", dateAfter(upper, "NACIMIENTO", "NAC"));
        if (empty(p, "validityDate")) { String d=dateAfter(upper,"VALIDEZ","CADUCIDAD","VENCIMIENTO","VENC"); if(!d.isEmpty()){put(p,"validityDate",d);put(p,"expiry",d);} }
        if (empty(p, "issueDate")) put(p, "issueDate", dateAfter(upper, "EMISION", "EMISIÓN", "EFECTO", "ALTA"));
        if (empty(p, "type")) put(p, "type", (upper.contains("PÓLIZA") || upper.contains("POLIZA")) ? "Otros" : "Cliente / DNI");
    }

    private static void merge(JSONArray a) throws Exception {
        for (int i=0;i<a.length();i++) {
            JSONObject base=a.optJSONObject(i); if(base==null)continue;
            for(int j=a.length()-1;j>i;j--){JSONObject other=a.optJSONObject(j);if(other!=null&&sameClient(base,other)){mergeInto(base,other);a.remove(j);}}
        }
    }

    private static boolean sameClient(JSONObject a, JSONObject b) {
        String ai=norm(id(a)), bi=norm(id(b));
        if(!ai.isEmpty()&&!bi.isEmpty()) return ai.equals(bi);
        String ae=norm(a.optString("email","")),be=norm(b.optString("email","")); if(!ae.isEmpty()&&!be.isEmpty()&&ae.equals(be))return true;
        String ap=digits(a.optString("phone","")),bp=digits(b.optString("phone","")); if(!ap.isEmpty()&&!bp.isEmpty()&&ap.equals(bp))return true;
        String an=normName(fullName(a)),bn=normName(fullName(b));
        if(!an.isEmpty()&&!bn.isEmpty()&&(an.equals(bn)||an.contains(bn)||bn.contains(an)))return true;
        if(!an.isEmpty()&&!bn.isEmpty()){String[]at=an.split(" "),bt=bn.split(" ");int common=0;for(String x:at)for(String y:bt)if(x.length()>2&&x.equals(y))common++;if(common>=2)return true;}
        return false;
    }

    private static void mergeInto(JSONObject base,JSONObject other)throws Exception{
        String[] fields={"holder","surname","name","identityType","identityNumber","holderDni","cif","birthDate","nationality","sex","address","birthPlace","parents","supportNumber","issueDate","validityDate","expiry","phone","email"};
        for(String k:fields)copyIfEmpty(base,other,k);
        JSONArray docs=base.optJSONArray("documentPhotos");if(docs==null)docs=new JSONArray();Set<String>seen=new HashSet<>();for(int i=0;i<docs.length();i++){JSONObject d=docs.optJSONObject(i);if(d!=null)seen.add(d.optString("path",""));}
        JSONArray od=other.optJSONArray("documentPhotos");if(od!=null)for(int i=0;i<od.length();i++){JSONObject d=od.optJSONObject(i);if(d!=null&&!seen.contains(d.optString("path",""))){docs.put(d);seen.add(d.optString("path",""));}}base.put("documentPhotos",docs);
        JSONArray policies=base.optJSONArray("linkedPolicies");if(policies==null)policies=new JSONArray();addPolicyIfPresent(policies,base);addPolicyIfPresent(policies,other);base.put("linkedPolicies",uniquePolicies(policies));
        String bo=base.optString("ocrText",""),oo=other.optString("ocrText","");if(!oo.isEmpty()&&!bo.contains(oo))base.put("ocrText",(bo.isEmpty()?"":bo+"\n\n--- DOCUMENTO ASOCIADO ---\n")+oo);
        base.put("updatedAt",System.currentTimeMillis());
    }

    private static void addPolicyIfPresent(JSONArray a,JSONObject p)throws Exception{String n=p.optString("number","").trim();if(n.isEmpty())return;JSONObject x=new JSONObject();x.put("number",n);x.put("type",p.optString("type","Otros"));x.put("expiry",p.optString("expiry",p.optString("validityDate","")));x.put("holder",p.optString("holder",""));a.put(x);}
    private static JSONArray uniquePolicies(JSONArray in)throws Exception{JSONArray out=new JSONArray();Set<String>seen=new HashSet<>();for(int i=0;i<in.length();i++){JSONObject p=in.optJSONObject(i);if(p==null)continue;String k=norm(p.optString("number",""));if(k.isEmpty()||seen.add(k))out.put(p);}return out;}
    private static void copyIfEmpty(JSONObject a,JSONObject b,String k)throws Exception{if(empty(a,k)&&!empty(b,k))a.put(k,b.optString(k));}
    private static boolean empty(JSONObject p,String k){return p==null||p.optString(k,"").trim().isEmpty();}
    private static void put(JSONObject p,String k,String v){try{if(v!=null&&!v.trim().isEmpty())p.put(k,v.trim());}catch(Exception ignored){}}
    private static String id(JSONObject p){String x=p.optString("identityNumber",p.optString("holderDni",""));if(x.trim().isEmpty())x=p.optString("cif","");return x;}
    private static String fullName(JSONObject p){String h=p.optString("holder","");if(!h.isEmpty())return h;return(p.optString("name","")+" "+p.optString("surname","")).trim();}
    private static String norm(String s){if(s==null)return"";return Normalizer.normalize(s.toUpperCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^A-Z0-9]","");}
    private static String normName(String s){if(s==null)return"";return Normalizer.normalize(s.toUpperCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^A-Z0-9 ]","").replaceAll("\\s+"," ").trim();}
    private static String digits(String s){return s==null?"":s.replaceAll("\\D","");}
    private static String first(Pattern p,String s){Matcher m=p.matcher(s==null?"":s);return m.find()?m.group():"";}
    private static String policyNumber(String s){Matcher m=POLICY.matcher(s==null?"":s);return m.find()?m.group(1).trim():"";}
    private static String labelValue(String text,String...labels){for(String label:labels){Pattern p=Pattern.compile("(?:^|\\n)\\s*"+Pattern.quote(label)+"\\s*[:.-]?\\s*([^\\n]{2,100})",Pattern.CASE_INSENSITIVE);Matcher m=p.matcher(text);if(m.find()){String v=m.group(1).trim();if(!v.isEmpty())return v;}}return"";}
    private static String dateAfter(String text,String...labels){for(String label:labels){Pattern p=Pattern.compile(Pattern.quote(label)+"[^\\n]{0,50}?"+DATE.pattern(),Pattern.CASE_INSENSITIVE);Matcher m=p.matcher(text);if(m.find()){Matcher d=DATE.matcher(m.group());if(d.find())return d.group();}}return"";}
}
