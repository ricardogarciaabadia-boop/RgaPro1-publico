from pathlib import Path

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivity.java")
s = MAIN.read_text(encoding="utf-8")
old = 'String nu=u.getText().toString().trim();String np=p.getText().toString();prefs.edit().putString("user",nu).putString("pin",np).putBoolean("biometric",true).putString("appUsers",new JSONArray().put(new JSONObject().put("name",nu).put("pin",np).put("active",true).put("role","ADMIN")).toString()).apply();currentUser=nu;home();'
new = 'String nu=u.getText().toString().trim();String np=p.getText().toString();try{JSONObject first=new JSONObject();first.put("name",nu);first.put("pin",np);first.put("active",true);first.put("role","ADMIN");JSONArray ua=new JSONArray();ua.put(first);prefs.edit().putString("user",nu).putString("pin",np).putBoolean("biometric",true).putString("appUsers",ua.toString()).apply();}catch(Exception ex){prefs.edit().putString("user",nu).putString("pin",np).putBoolean("biometric",true).apply();}currentUser=nu;home();'
if old in s:
    s=s.replace(old,new,1)
MAIN.write_text(s,encoding="utf-8")
print("User initialization compile safety applied")
