from pathlib import Path

p = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Global client search shortcut: a visible magnifying-glass icon in the main header.
old = 'top.addView(tv("Panel principal · "+currentUser,15,Color.WHITE,false));\n        root.addView(top,new LinearLayout.LayoutParams(-1,dp(88)));'
new = 'top.addView(tv("Panel principal · "+currentUser,15,Color.WHITE,false));\n        Button globalSearch=action("🔍  Buscar cliente",true); globalSearch.setTextSize(15); globalSearch.setOnClickListener(v->clients());\n        top.addView(globalSearch,new LinearLayout.LayoutParams(-1,dp(50)));\n        root.addView(top,new LinearLayout.LayoutParams(-1,dp(138)));'
if old in s and 'globalSearch=action("🔍  Buscar cliente"' not in s:
    s = s.replace(old, new, 1)

# Also expose the same action in the navigation menu.
old = 'Button clients=sideButton("👤  Clientes"); clients.setOnClickListener(v->clients()); side.addView(clients,new LinearLayout.LayoutParams(-1,dp(60)));'
new = 'Button clients=sideButton("👤  Clientes"); clients.setOnClickListener(v->clients()); side.addView(clients,new LinearLayout.LayoutParams(-1,dp(60)));\n        Button searchClients=sideButton("🔍  Buscar cliente"); searchClients.setOnClickListener(v->clients()); side.addView(searchClients,new LinearLayout.LayoutParams(-1,dp(60)));'
if old in s and 'searchClients=sideButton("🔍  Buscar cliente"' not in s:
    s = s.replace(old, new, 1)

# Replace exact-field search with normalized, accent-insensitive, token-aware substring search.
old_start = '    private boolean match(JSONObject p,String q){if(q==null||q.trim().isEmpty())return true;String n=q.toLowerCase(Locale.ROOT);for(String k:new String[]{'
start = s.find(old_start)
if start >= 0:
    end = s.find('\n    private void detail(JSONObject p)', start)
    if end >= 0:
        replacement = '''    private String searchNorm(String value){
        if(value==null)return "";
        String n=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD);
        n=n.replaceAll("\\\\p{InCombiningDiacriticalMarks}+","");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9áéíóúüñ@._/ -]"," ").replaceAll("\\\\s+"," ").trim();
    }
    private boolean match(JSONObject p,String q){
        String query=searchNorm(q);
        if(query.isEmpty())return true;
        StringBuilder all=new StringBuilder();
        for(String k:new String[]{"holder","name","surname","birthDate","holderDni","identityNumber","identityType","cif","phone","email","address","birthPlace","nationality","sex","parents","supportNumber","issueDate","expiry","validityDate","type","number","ocrText"}){
            Object v=p.opt(k); if(v!=null)all.append(' ').append(v.toString());
        }
        // Include every scalar nested value as well, so future policy/customer fields are searchable without code changes.
        for(java.util.Iterator<String> it=p.keys();it.hasNext();){String k=it.next();Object v=p.opt(k);if(v instanceof JSONObject)appendSearchJson(all,(JSONObject)v);else if(v instanceof JSONArray)appendSearchArray(all,(JSONArray)v);}
        String hay=searchNorm(all.toString());
        if(hay.contains(query))return true;
        // Multi-word searches may arrive in any order; every token must occur somewhere in the client record.
        for(String token:query.split(" "))if(!token.isEmpty()&&!hay.contains(token))return false;
        return true;
    }
    private void appendSearchJson(StringBuilder out,JSONObject o){for(java.util.Iterator<String> it=o.keys();it.hasNext();){Object v=o.opt(it.next());if(v instanceof JSONObject)appendSearchJson(out,(JSONObject)v);else if(v instanceof JSONArray)appendSearchArray(out,(JSONArray)v);else if(v!=null)out.append(' ').append(v.toString());}}
    private void appendSearchArray(StringBuilder out,JSONArray a){for(int i=0;i<a.length();i++){Object v=a.opt(i);if(v instanceof JSONObject)appendSearchJson(out,(JSONObject)v);else if(v instanceof JSONArray)appendSearchArray(out,(JSONArray)v);else if(v!=null)out.append(' ').append(v.toString());}}
'''
        s = s[:start] + replacement + s[end:]

p.write_text(s,encoding='utf-8')
print('Global client search UI and fuzzy/all-field search applied')
