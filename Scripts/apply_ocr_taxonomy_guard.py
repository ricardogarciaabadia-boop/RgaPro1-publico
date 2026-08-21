from pathlib import Path

p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')
old='String[] types={"Deceso","Hogar","Vida","Accidente","Ahorro","Comunidades","Empresa","Responsabilidad civil","Salud","Otros"};'
new='String[] types={"Cliente / DNI","Cliente / NIE","Deceso","Hogar","Vida","Accidente","Ahorro","Comunidades","Empresa","Responsabilidad civil","Salud","Otros"};'
if old in s:s=s.replace(old,new,1)
s=s.replace('if("POLIZA".equals(d.documentKind)){int psel=9;','if("POLIZA".equals(d.documentKind)){int psel=11;',1)
p.write_text(s,encoding='utf-8')
print('OCR taxonomy guard applied')
