from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')
if 'extractDecesoInsuredsWithoutId' not in s:
    marker='    private JSONArray extractDecesoCapitalsAdvanced('
    helper=r'''    private JSONArray extractDecesoInsuredsWithoutId(String raw,String holder,String holderDni){
        JSONArray out=new JSONArray();
        String text=raw==null?"":raw.replace('\\r','\\n');
        Matcher sec=Pattern.compile("(?is)(?:RELACI[ÓO]N\\s+DE\\s+ASEGURADOS|RELACI[ÓO]N\\s+DE\\s+ASEGURADOS\\s+QUE\\s+COMPONEN\\s+LA\\s+P[ÓO]LIZA)(.*?)(?=GARANT[IÍ]AS\\s+Y\\s+COBERTURAS|$)").matcher(text);
        String block=sec.find()?sec.group(1):text;
        Pattern row=Pattern.compile("(?m)^\\s*(\\d{1,3})\\s+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ .'-]{3,})\\s+(\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4})\\s+([A-Z])\\s+(\\d{1,2}[ /.-]\\d{1,2}[ /.-]\\d{4})\\s*$",Pattern.CASE_INSENSITIVE);
        Matcher m=row.matcher(block);
        while(m.find()){
            try{
                String name=clean(m.group(2));
                if(name.matches("(?i).*(NOMBRE|ASEGURADO|POLIZA|PÓLIZA|TOMADOR|FECHA|GARANTIA|GARANTÍA).*"))continue;
                JSONObject p=new JSONObject();p.put("row",m.group(1));p.put("name",name);p.put("birthDate",m.group(3));p.put("sex",m.group(4).toUpperCase(Locale.ROOT));p.put("rightsDate",m.group(5));p.put("role","ASEGURADO");p.put("isHolder",samePerson(p,holder,holderDni));out.put(p);
            }catch(Exception ignored){}
        }
        return out;
    }

    private JSONArray mergeInsuredArrays(JSONArray a,JSONArray b){
        JSONArray out=new JSONArray();
        try{for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null)out.put(new JSONObject(p.toString()));}for(int i=0;i<b.length();i++){JSONObject p=b.optJSONObject(i);if(p==null)continue;String row=p.optString("row","");String key=personKey(p);boolean dup=false;for(int j=0;j<out.length();j++){JSONObject q=out.optJSONObject(j);if(q==null)continue;if(!row.isEmpty()&&row.equals(q.optString("row",""))){dup=true;break;}if(!key.isEmpty()&&key.equals(personKey(q))){dup=true;break;}}if(!dup)out.put(p);}}catch(Exception ignored){}return out;
    }

'''
    if marker not in s: raise SystemExit('capital helper marker not found')
    s=s.replace(marker,helper+marker,1)
needle='JSONArray ins=extractDecesoInsuredsAdvanced(raw,holder,hid);'
if needle in s and 'mergeInsuredArrays(ins,extractDecesoInsuredsWithoutId' not in s:
    s=s.replace(needle,needle+'ins=mergeInsuredArrays(ins,extractDecesoInsuredsWithoutId(raw,holder,hid));',1)
p.write_text(s,encoding='utf-8')
print('Added decesos insured rows without DNI, preserving children/minors')
