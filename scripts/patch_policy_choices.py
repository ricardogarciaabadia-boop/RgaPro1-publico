from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')
old='String[] types={"Cliente / DNI","Cliente / NIE","Auto","Hogar","Vida","Salud","Decesos","Empresa","Otros"};'
new='String[] types={"Deceso","Hogar","Vida","Accidente","Ahorro","Comunidades","Empresa","Responsabilidad civil","Salud","Otros"};'
s=s.replace(old,new)
if 'Modalidad Ahorro: Flexible / PIAS / Aporte extraordinario' not in s:
    s=s.replace('EditText insureds=edit("Asegurados: uno por línea · Nombre | DNI/NIE | Fecha nacimiento"),product=edit("Producto (solo Deceso; editable)");','EditText insureds=edit("Asegurados: uno por línea · Nombre | DNI/NIE | Fecha nacimiento"),product=edit("Producto (solo Deceso; editable)"),ahorroMode=edit("Modalidad Ahorro: Flexible / PIAS / Aporte extraordinario");')
    s=s.replace('box.addView(product,new LinearLayout.LayoutParams(-1,dp(52)));','box.addView(product,new LinearLayout.LayoutParams(-1,dp(52)));ahorroMode.setVisibility(View.GONE);box.addView(ahorroMode,new LinearLayout.LayoutParams(-1,dp(52)));')
    s=s.replace('product.setVisibility((String.valueOf(type.getSelectedItem()).equalsIgnoreCase("Deceso")||String.valueOf(type.getSelectedItem()).equalsIgnoreCase("Decesos"))?View.VISIBLE:View.GONE);','product.setVisibility((String.valueOf(type.getSelectedItem()).equalsIgnoreCase("Deceso")||String.valueOf(type.getSelectedItem()).equalsIgnoreCase("Decesos"))?View.VISIBLE:View.GONE);ahorroMode.setVisibility(String.valueOf(type.getSelectedItem()).equalsIgnoreCase("Ahorro")?View.VISIBLE:View.GONE);')
    s=s.replace('String raw,String insuredText,String productText){','String raw,String insuredText,String productText,String ahorroModeText){')
    s=s.replace('if("Deceso".equalsIgnoreCase(p.optString("type","")))p.put("product",productText==null?"":productText.trim());','if("Deceso".equalsIgnoreCase(p.optString("type","")))p.put("product",productText==null?"":productText.trim());if("Ahorro".equalsIgnoreCase(p.optString("type","")))p.put("ahorroModalidad",ahorroModeText==null?"":ahorroModeText.trim());')
    s=s.replace('raw,insureds.getText().toString(),product.getText().toString());','raw,insureds.getText().toString(),product.getText().toString(),ahorroMode.getText().toString());')
    s=s.replace('final EditText holder=edit("Tomador"),number=edit("Nº póliza"),product=edit("Producto (Deceso)"),insured=','final EditText holder=edit("Tomador"),number=edit("Nº póliza"),product=edit("Producto (Deceso)"),ahorroMode=edit("Modalidad Ahorro: Flexible / PIAS / Aporte extraordinario"),insured=')
    s=s.replace('product.setText(policy.optString("product",""));JSONArray ins=','product.setText(policy.optString("product",""));ahorroMode.setText(policy.optString("ahorroModalidad",""));JSONArray ins=')
    s=s.replace('if("Deceso".equalsIgnoreCase(policy.optString("type","")))box.addView(product,new LinearLayout.LayoutParams(-1,dp(52)));box.addView(insured','if("Deceso".equalsIgnoreCase(policy.optString("type","")))box.addView(product,new LinearLayout.LayoutParams(-1,dp(52)));if("Ahorro".equalsIgnoreCase(policy.optString("type","")))box.addView(ahorroMode,new LinearLayout.LayoutParams(-1,dp(52)));box.addView(insured')
    s=s.replace('if("Deceso".equalsIgnoreCase(policy.optString("type","")))policy.put("product",product.getText().toString().trim());policy.put("insureds"','if("Deceso".equalsIgnoreCase(policy.optString("type","")))policy.put("product",product.getText().toString().trim());if("Ahorro".equalsIgnoreCase(policy.optString("type","")))policy.put("ahorroModalidad",ahorroMode.getText().toString().trim());policy.put("insureds"')
s=s.replace('extra+=" · Asegurados: "+(ins==null?0:ins.length());','if("Ahorro".equalsIgnoreCase(t)&&!p.optString("ahorroModalidad","").isEmpty())extra+=" · Modalidad: "+p.optString("ahorroModalidad");extra+=" · Asegurados: "+(ins==null?0:ins.length());')
p.write_text(s,encoding='utf-8')
print('Exact policy taxonomy and Ahorro modalities applied')
