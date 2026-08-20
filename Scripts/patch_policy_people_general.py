from pathlib import Path

MAIN=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=MAIN.read_text(encoding='utf-8')

if 'extractPolicyPeopleGeneralFinal' not in s:
    marker='    private void security(){'
    helpers=r'''    private String cleanPolicyValueFinal(String x){return (x==null?"":x.replaceAll("[\\t ]+"," ").trim());}

    private String extractLabeledPolicyFinal(String raw,String... labels){
        String[] lines=(raw==null?"":raw.replace('\\r','\\n')).split("\\n");
        for(int i=0;i<lines.length;i++){
            String line=lines[i].trim(), up=line.toUpperCase(Locale.ROOT);
            for(String label:labels){
                String l=label.toUpperCase(Locale.ROOT);int p=up.indexOf(l);
                if(p>=0){String v=line.substring(Math.min(line.length(),p+l.length())).replaceFirst("^[\\s:;.-]+","").trim();if(!v.isEmpty())return cleanPolicyValueFinal(v);if(i+1<lines.length&&!lines[i+1].trim().isEmpty())return cleanPolicyValueFinal(lines[i+1]);}
            }
        }
        return "";
    }

    private JSONObject parsePolicyPersonGeneralFinal(String rawLine,String role){
        JSONObject p=new JSONObject();
        try{
            String x=cleanPolicyValueFinal(rawLine);if(x.isEmpty())return p;
            Matcher em=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",Pattern.CASE_INSENSITIVE).matcher(x);if(em.find()){p.put("email",em.group());x=(x.substring(0,em.start())+" "+x.substring(em.end())).trim();}
            Matcher ph=Pattern.compile("(?<!\\d)(?:\\+34[\\s.-]?)?[6789]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}(?!\\d)").matcher(x);if(ph.find()){p.put("phone",ph.group().replaceAll("[\\s.-]",""));x=(x.substring(0,ph.start())+" "+x.substring(ph.end())).trim();}
            Matcher id=Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])",Pattern.CASE_INSENSITIVE).matcher(x);if(id.find()){p.put("identityNumber",id.group().toUpperCase(Locale.ROOT));x=(x.substring(0,id.start())+" "+x.substring(id.end())).trim();}
            String addr=extractLabeledPolicyFinal(x,"DOMICILIO","DIRECCIÓN","DIRECCION");if(!addr.isEmpty())p.put("address",addr);
            x=x.replaceAll("(?i)\\b(DOMICILIO|DIRECCIÓN|DIRECCION)\\b\\s*[:;.-]?\\s*[^\\n;]+","").replaceAll("\\s+"," ").trim();
            if(x.isEmpty())return p;
            p.put("name",x);p.put("role",role);return p;
        }catch(Exception ignored){return p;}
    }

    private JSONArray extractPolicyPeopleGeneralFinal(String raw,String holder,String holderDni){
        JSONArray out=new JSONArray();String text=raw==null?"":raw.replace('\\r','\\n');
        String[] sectionLabels={"PERSONAS ASEGURADAS","PERSONAS ASEGURADAS DISTINTAS","ASEGURADOS","ASEGURADAS","RELACIÓN DE ASEGURADOS","RELACION DE ASEGURADOS"};
        String block="";for(String label:sectionLabels){Matcher m=Pattern.compile("(?is)\\b"+Pattern.quote(label)+"\\b\\s*[:\\-]?\\s*(.*?)(?=\\n\\s*(?:TOMADOR(?:A)?|CONTRATANTE|BENEFICIARIO(?:S)?|PRIMA|RECIBO|FORMA DE PAGO|CONDICIONES|GARANT[IÍ]AS|COBERTURAS|CAPITALES|OBSERVACIONES)\\b|$)").matcher(text);if(m.find()){block=m.group(1);break;}}
        if(block.trim().isEmpty())return out;
        for(String line:block.split("\\n|;")){
            String x=cleanPolicyValueFinal(line);if(x.length()<3||x.length()>240)continue;String u=x.toUpperCase(Locale.ROOT);
            if(u.matches(".*\\b(?:P[ÓO]LIZA|POLIZA|PRIMA|RECIBO|ASEGURADORA|COBERTURA|COBERTURAS|GARANT[IÍ]AS|BENEFICIARIO|BENEFICIARIOS|TOMADOR|CONTRATANTE)\\b.*")&&!u.matches(".*(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z]).*"))continue;
            JSONObject p=parsePolicyPersonGeneralFinal(x,"ASEGURADO");if(p.optString("name","").isEmpty())continue;
            String key=personKey(p);boolean dup=false;for(int i=0;i<out.length();i++){JSONObject q=out.optJSONObject(i);if(q!=null&&!key.isEmpty()&&key.equals(personKey(q))){dup=true;break;}}if(!dup)out.put(p);
        }
        if(out.length()==0&&!holder.trim().isEmpty()){JSONObject p=new JSONObject();try{p.put("name",holder.trim());p.put("identityNumber",holderDni==null?"":holderDni.trim());p.put("role","ASEGURADO");out.put(p);}catch(Exception ignored){}}
        return out;
    }

    private void enrichGeneralPolicyPeopleFinal(JSONObject policy,String raw){
        try{
            String holder=policy.optString("holder",policy.optString("name",""));String holderDni=policy.optString("holderDni",policy.optString("identityNumber",""));
            if(holder.isEmpty())holder=extractLabeledPolicyFinal(raw,"TOMADOR","TOMADORA","TITULAR","CONTRATANTE","ASEGURADO");
            if(holderDni.isEmpty()){Matcher m=Pattern.compile("(?<![A-Z0-9])(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])(?![A-Z0-9])",Pattern.CASE_INSENSITIVE).matcher(raw==null?"":raw);if(m.find())holderDni=m.group().toUpperCase(Locale.ROOT);}
            if(!holder.isEmpty())policy.put("holder",holder);if(!holderDni.isEmpty()){policy.put("holderDni",holderDni);policy.put("identityNumber",holderDni);}
            String type=policy.optString("type","");if(type.isEmpty()||"Otros".equalsIgnoreCase(type))policy.put("type",classifyPolicyTypeFinal(raw,type));
            JSONArray people=extractPolicyPeopleGeneralFinal(raw,holder,holderDni);if(people.length()>0)policy.put("insureds",people);
            policy.put("documentKind","POLIZA");
        }catch(Exception ignored){}
    }

    private void rebuildAllPersonPolicyLinksFinal(JSONArray policies){
        try{
            for(int i=0;i<policies.length();i++){
                JSONObject p=policies.optJSONObject(i);if(p==null)continue;JSONArray persons=new JSONArray();String pk=personKey(p);if(!pk.isEmpty()){JSONObject h=new JSONObject();h.put("name",p.optString("holder",p.optString("name","")));h.put("identityNumber",p.optString("holderDni",p.optString("identityNumber","")));persons.put(h);}JSONArray ins=p.optJSONArray("insureds");if(ins!=null)for(int j=0;j<ins.length();j++)if(ins.optJSONObject(j)!=null)persons.put(ins.optJSONObject(j));
                for(int j=0;j<persons.length();j++){JSONObject person=persons.optJSONObject(j);if(person==null)continue;String key=personKey(person);if(key.isEmpty())continue;JSONArray ids=new JSONArray();for(int k=0;k<policies.length();k++){JSONObject q=policies.optJSONObject(k);if(q==null)continue;boolean hit=key.equals(personKey(q));JSONArray qi=q.optJSONArray("insureds");if(!hit&&qi!=null)for(int z=0;z<qi.length();z++){JSONObject qp=qi.optJSONObject(z);if(qp!=null&&key.equals(personKey(qp))){hit=true;break;}}if(hit){String id=q.optString("policyId","");if(id.isEmpty())id=q.optString("number","");if(!id.isEmpty()&&!contains(ids,id))ids.put(id);}}person.put("otherPolicyIds",ids);person.put("otherPolicyCount",Math.max(0,ids.length()-1));person.put("matchesOtherPolicies",ids.length()>1);}
            }
        }catch(Exception ignored){}
    }

'''
    if marker not in s: raise SystemExit('security marker missing')
    s=s.replace(marker,helpers+marker,1)

needle='save(a);updatePeopleIndex(p);'
if 'enrichGeneralPolicyPeopleFinal(p,raw);' not in s and needle in s:
    s=s.replace(needle,'if(!dniDocument){enrichGeneralPolicyPeopleFinal(p,raw);rebuildAllPersonPolicyLinksFinal(a);}\n            save(a);updatePeopleIndex(p);',1)

MAIN.write_text(s,encoding='utf-8')
print('Generic policy product, holder, insured people and cross-policy linking applied')
