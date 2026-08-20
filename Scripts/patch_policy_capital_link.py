from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')
if 'attachCapitalesToInsureds' not in s:
    marker='    private void rebuildCrossLinks(JSONArray policies){'
    helper=r'''    private void attachCapitalesToInsureds(JSONObject policy){
        try{
            JSONArray ins=policy.optJSONArray("insureds");JSONArray caps=policy.optJSONArray("capitales");
            if(ins==null||caps==null)return;
            for(int i=0;i<ins.length();i++){
                JSONObject person=ins.optJSONObject(i);if(person==null)continue;
                JSONObject own=new JSONObject();
                for(int c=0;c<caps.length();c++){
                    JSONObject row=caps.optJSONObject(c);if(row==null)continue;
                    JSONArray values=row.optJSONArray("values");if(values!=null&&i<values.length())own.put(row.optString("coverage","Cobertura"),values.optString(i,""));
                }
                person.put("capitales",own);
                String base=own.optString("TOTAL DECESOS",own.optString("DECESOS NIVELADA",""));
                if(!base.isEmpty())person.put("capitalDecesos",base);
            }
        }catch(Exception ignored){}
    }

'''
    if marker not in s: raise SystemExit('rebuildCrossLinks marker not found')
    s=s.replace(marker,helper+marker,1)

needle='p.put("capitales",extractDecesoCapitalsAdvanced(raw,ins.length()));'
if needle in s and 'attachCapitalesToInsureds(p);' not in s:
    s=s.replace(needle,needle+'attachCapitalesToInsureds(p);',1)
p.write_text(s,encoding='utf-8')
print('Linked detected capitales to each insured for pricing/reference')
