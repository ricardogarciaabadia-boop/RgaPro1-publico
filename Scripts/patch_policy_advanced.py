from pathlib import Path
import re

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=MAIN.read_text(encoding='utf-8')


def replace_method(src, signature_start, replacement):
    start=src.find(signature_start)
    if start<0:
        return src, False
    brace=src.find('{',start)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:
                return src[:start]+replacement+src[i+1:], True
    raise SystemExit('Unbalanced method: '+signature_start)

# Robust parser for the Ocaso "RELACIÓN DE ASEGURADOS" table used by decesos policies.
if 'extractDecesoInsuredsAdvanced' not in s:
    marker='    private void security(){'
    helpers=r'''    private JSONArray extractDecesoInsuredsAdvanced(String raw,String holder,String holderDni){
        JSONArray out=new JSONArray();
        String text=raw==null?"":raw.replace('\\r','\\n');
        Pattern row=Pattern.compile("(?m)^\\s*(\\d{1,3})\\s+((?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]))\\s+(.+?)\\s+(\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4})\\s+([A-Z])\\s+(\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4})\\s*$",Pattern.CASE_INSENSITIVE);
        Matcher m=row.matcher(text);
        while(m.find()){
            try{
                JSONObject p=new JSONObject();
                p.put("row",m.group(1));
                p.put("identityNumber",m.group(2).toUpperCase(Locale.ROOT));
                p.put("name",clean(m.group(3)));
                p.put("birthDate",m.group(4));
                p.put("sex",m.group(5).toUpperCase(Locale.ROOT));
                p.put("rightsDate",m.group(6));
                p.put("role","ASEGURADO");
                p.put("isHolder",samePerson(p,holder,holderDni));
                out.put(p);
            }catch(Exception ignored){}
        }
        if(out.length()==0){
            Pattern loose=Pattern.compile("(?m)^\\s*(\\d{1,3})\\s+((?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]))\\s+(.+?)\\s+(\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4})(?:\\s+([A-Z]))?(?:\\s+(\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4}))?\\s*$",Pattern.CASE_INSENSITIVE);
            Matcher lm=loose.matcher(text);
            while(lm.find()){
                try{
                    String name=clean(lm.group(3));
                    String up=name.toUpperCase(Locale.ROOT);
                    if(up.matches(".*\\b(?:TOMADOR|POLIZA|PÓLIZA|PRIMA|ASEGURADORA|GARANTIAS|COBERTURAS)\\b.*"))continue;
                    JSONObject p=new JSONObject();p.put("row",lm.group(1));p.put("identityNumber",lm.group(2).toUpperCase(Locale.ROOT));p.put("name",name);p.put("birthDate",lm.group(4));if(lm.group(5)!=null)p.put("sex",lm.group(5).toUpperCase(Locale.ROOT));if(lm.group(6)!=null)p.put("rightsDate",lm.group(6));p.put("role","ASEGURADO");p.put("isHolder",samePerson(p,holder,holderDni));out.put(p);
                }catch(Exception ignored){}
            }
        }
        return dedupeInsureds(out);
    }

    private boolean samePerson(JSONObject p,String holder,String holderDni){
        String id=p.optString("identityNumber","").trim();
        String hid=holderDni==null?"":holderDni.trim();
        if(!id.isEmpty()&&!hid.isEmpty()&&id.equalsIgnoreCase(hid))return true;
        String a=normalizeKey(p.optString("name",""));
        String b=normalizeKey(holder);
        return !a.isEmpty()&&!b.isEmpty()&&a.equals(b);
    }

    private JSONArray dedupeInsureds(JSONArray in){
        JSONArray out=new JSONArray();
        for(int i=0;i<in.length();i++){
            JSONObject p=in.optJSONObject(i);if(p==null)continue;
            String key=personKey(p);boolean dup=false;
            for(int j=0;j<out.length();j++){JSONObject q=out.optJSONObject(j);if(q!=null&&!key.isEmpty()&&key.equals(personKey(q))){dup=true;break;}}
            if(!dup)out.put(p);
        }
        return out;
    }

    private JSONArray extractDecesoCapitalsAdvanced(String raw,int insuredCount){
        JSONArray out=new JSONArray();
        String text=raw==null?"":raw.replace('\\r','\\n');
        Matcher sec=Pattern.compile("(?is)(?:GARANT[IÍ]AS\\s+Y\\s+COBERTURAS\\s+POR\\s+ASEGURADO|GARANT[IÍ]AS\\s+Y\\s+COBERTURAS\\s+POR\\s+ASEGURADOS)(.*?)(?=BENEFICIARIOS|OBSERVACIONES|$)").matcher(text);
        String block=sec.find()?sec.group(1):"";
        if(block.isEmpty())return out;
        for(String line:block.split("\\n")){
            String x=clean(line);if(x.isEmpty())continue;
            String u=x.toUpperCase(Locale.ROOT);
            if(u.startsWith("GARANTIAS")||u.startsWith("GARANTÍAS")||u.matches("^[1-9]\\s+UNI.*"))continue;
            Matcher token=Pattern.compile("(?<!\\d)(?:\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+(?:[.,]\\d{2})?)(?!\\d)").matcher(x);
            ArrayList<String> vals=new ArrayList<>();
            while(token.find()){
                String v=token.group();
                if(v.matches("\\d{1,3}"))continue;
                vals.add(v);
            }
            Matcher included=Pattern.compile("(?i)INCLUIDO|NO\\s+INCLUIDO|\\-{2,}").matcher(x);
            ArrayList<String> status=new ArrayList<>();while(included.find())status.add(included.group().toUpperCase(Locale.ROOT));
            if(vals.isEmpty()&&status.isEmpty())continue;
            int cut=Integer.MAX_VALUE;
            if(!vals.isEmpty())cut=x.indexOf(vals.get(0));else if(!status.isEmpty())cut=x.toUpperCase(Locale.ROOT).indexOf(status.get(0));
            if(cut<=0)continue;
            String label=x.substring(0,cut).replaceAll("[.:]+$","").trim();
            if(label.length()<3)continue;
            try{
                JSONObject row=new JSONObject();row.put("coverage",label);JSONArray per=new JSONArray();
                for(int i=0;i<Math.max(insuredCount,vals.size());i++){
                    String v=i<vals.size()?vals.get(i):(i<status.size()?status.get(i):"");per.put(v);
                }
                row.put("values",per);out.put(row);
            }catch(Exception ignored){}
        }
        return out;
    }

    private void enrichDecesoPolicyAdvanced(JSONObject p,String raw){
        try{
            String holder=p.optString("holder","");String hid=p.optString("identityNumber",p.optString("holderDni",""));
            JSONArray ins=extractDecesoInsuredsAdvanced(raw,holder,hid);
            if(ins.length()>0)p.put("insureds",ins);
            p.put("insuredCount",ins.length());
            p.put("capitales",extractDecesoCapitalsAdvanced(raw,ins.length()));
            p.put("capitalesDetectados",true);
        }catch(Exception ignored){}
    }

    private void rebuildCrossLinks(JSONArray policies){
        try{
            for(int i=0;i<policies.length();i++){
                JSONObject p=policies.optJSONObject(i);if(p==null)continue;
                JSONArray ins=p.optJSONArray("insureds");if(ins==null)continue;
                for(int j=0;j<ins.length();j++){
                    JSONObject person=ins.optJSONObject(j);if(person==null)continue;
                    JSONArray matches=new JSONArray();String key=personKey(person);
                    for(int k=0;k<policies.length();k++){
                        JSONObject q=policies.optJSONObject(k);if(q==null)continue;
                        boolean hit=key.equals(personKey(q));
                        JSONArray qi=q.optJSONArray("insureds");
                        if(!hit&&qi!=null)for(int z=0;z<qi.length();z++){JSONObject qp=qi.optJSONObject(z);if(qp!=null&&key.equals(personKey(qp))){hit=true;break;}}
                        if(hit){String id=q.optString("policyId","");if(!id.isEmpty()&&!contains(matches,id))matches.put(id);}
                    }
                    person.put("otherPolicyIds",matches);person.put("matchesOtherPolicies",matches.length()>1);
                    person.put("otherPolicyCount",Math.max(0,matches.length()-1));
                }
            }
        }catch(Exception ignored){}
    }

    private boolean contains(JSONArray a,String value){for(int i=0;i<a.length();i++)if(value.equals(a.optString(i)))return true;return false;}

    private String formatPolicyRelations(JSONObject policy){
        StringBuilder z=new StringBuilder();JSONArray ins=policy.optJSONArray("insureds");int count=ins==null?0:ins.length();
        z.append("👥 PERSONAS EN LA PÓLIZA: ").append(count).append("\\n\\n");
        if(ins!=null)for(int i=0;i<ins.length();i++){
            JSONObject p=ins.optJSONObject(i);if(p==null)continue;
            z.append(i+1).append(". ").append(p.optString("name","Sin nombre"));
            if(!p.optString("identityNumber","").isEmpty())z.append(" · ").append(p.optString("identityNumber"));
            if(!p.optString("birthDate","").isEmpty())z.append(" · Nac. ").append(p.optString("birthDate"));
            if(!p.optString("rightsDate","").isEmpty())z.append(" · Derechos ").append(p.optString("rightsDate"));
            if(p.optBoolean("isHolder",false))z.append(" · TOMADOR");
            int others=p.optInt("otherPolicyCount",0);if(others>0)z.append(" · ⚠ ").append(others).append(" otra(s) póliza(s)");
            z.append('\\n');
        }
        JSONArray caps=policy.optJSONArray("capitales");
        if(caps!=null&&caps.length()>0){z.append("\\n💶 CAPITALES / COBERTURAS DETECTADOS\\n");for(int i=0;i<caps.length();i++){JSONObject r=caps.optJSONObject(i);if(r==null)continue;z.append("• ").append(r.optString("coverage","")).append(": ");JSONArray v=r.optJSONArray("values");if(v!=null)for(int j=0;j<v.length();j++){if(j>0)z.append(" | ");z.append(v.optString(j,""));}z.append('\\n');}}
        return z.toString();
    }

'''
    if marker not in s: raise SystemExit('security marker not found')
    s=s.replace(marker,helpers+marker,1)

# Upgrade the existing insured-line editor so it can preserve sex and rights date.
if 'p.put("sex",q[3].trim())' not in s:
    start=s.find('    private JSONArray parseInsuredLines(')
    if start>=0:
        brace=s.find('{',start);depth=0
        for i in range(brace,len(s)):
            if s[i]=='{':depth+=1
            elif s[i]=='}':
                depth-=1
                if depth==0:
                    end=i+1;break
        new=r'''    private JSONArray parseInsuredLines(String raw,String fallbackHolder){JSONArray out=new JSONArray();String text=raw==null?"":raw.trim();if(text.isEmpty()&&!fallbackHolder.trim().isEmpty())text=fallbackHolder;for(String line:text.split("\\r?\\n")){String x=line.trim();if(x.isEmpty())continue;String[] q=x.split("\\s*\\|\\s*");try{JSONObject p=new JSONObject();p.put("name",q[0].trim());if(q.length>1)p.put("identityNumber",q[1].trim().toUpperCase(Locale.ROOT));if(q.length>2)p.put("birthDate",q[2].trim());if(q.length>3)p.put("sex",q[3].trim().toUpperCase(Locale.ROOT));if(q.length>4)p.put("rightsDate",q[4].trim());if(q.length>5)p.put("phone",q[5].trim());if(q.length>6)p.put("email",q[6].trim());if(!p.optString("name","").isEmpty())out.put(p);}catch(Exception ignored){}}return dedupeInsureds(out);}
'''
        s=s[:start]+new+s[end:]

# Ensure advanced enrichment and cross-links run immediately before the existing save call.
needle='save(a);updatePeopleIndex(p);'
if 'enrichDecesoPolicyAdvanced(p,raw);' not in s and needle in s:
    repl='if("Deceso".equalsIgnoreCase(p.optString("type","")))enrichDecesoPolicyAdvanced(p,raw);rebuildCrossLinks(a);save(a);updatePeopleIndex(p);'
    s=s.replace(needle,repl,1)

# Add a visible relation/capital report to the policy editor.
if 'formatPolicyRelations(policy)' not in s:
    needle='box.addView(insured,new LinearLayout.LayoutParams(-1,dp(150)));'
    if needle in s:
        repl='box.addView(insured,new LinearLayout.LayoutParams(-1,dp(150)));TextView relation=tv(formatPolicyRelations(policy),13,MUTED,false);relation.setBackground(bg(Color.WHITE,12));box.addView(relation,new LinearLayout.LayoutParams(-1,dp(260)));'
        s=s.replace(needle,repl,1)

# When a policy is edited, recompute cross-links after its insured list changes.
needle='save(a);updatePeopleIndex(policy);'
if needle in s and 'rebuildCrossLinks(a);save(a);updatePeopleIndex(policy);' not in s:
    s=s.replace(needle,'rebuildCrossLinks(a);save(a);updatePeopleIndex(policy);',1)

MAIN.write_text(s,encoding='utf-8')
print('Advanced decesos insured table, rights dates, capitales and cross-policy links applied')
