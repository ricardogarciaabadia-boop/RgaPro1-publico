from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')
if 'private String activeMenu="Inicio";' not in s:
    s=s.replace('private int side=0;','private int side=0; private String activeMenu="Inicio";',1)
old='Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false),p=btn("Pólizas",false);h.setOnClickListener(v->home());c.setOnClickListener(v->clients());o.setOnClickListener(v->ocrPage());p.setOnClickListener(v->policies());'
new='Button h=btn("Inicio",false),c=btn("Clientes",false),o=btn("OCR",false),p=btn("Pólizas",false); h.setBackground(box("Inicio".equals(activeMenu)?BLUE:Color.WHITE,18)); c.setBackground(box("Clientes".equals(activeMenu)?BLUE:Color.WHITE,18)); o.setBackground(box("OCR".equals(activeMenu)?BLUE:Color.WHITE,18)); p.setBackground(box("Pólizas".equals(activeMenu)?BLUE:Color.WHITE,18)); h.setTextColor("Inicio".equals(activeMenu)?Color.WHITE:TEXT); c.setTextColor("Clientes".equals(activeMenu)?Color.WHITE:TEXT); o.setTextColor("OCR".equals(activeMenu)?Color.WHITE:TEXT); p.setTextColor("Pólizas".equals(activeMenu)?Color.WHITE:TEXT); h.setOnClickListener(v->{clearTransientDocumentState();activeMenu="Inicio";home();});c.setOnClickListener(v->{clearTransientDocumentState();activeMenu="Clientes";clients();});o.setOnClickListener(v->{clearTransientDocumentState();activeMenu="OCR";ocrPage();});p.setOnClickListener(v->{clearTransientDocumentState();activeMenu="Pólizas";policies();});'
if old in s:s=s.replace(old,new,1)
else: raise SystemExit('navigation anchor not found')
anchor='    private void home(){'
helper='''    private void clearTransientDocumentState(){\n        currentBitmap=null; previewBitmap=null; frontBitmap=null; backBitmap=null; currentImagePath=""; frontImagePath=""; backImagePath=""; frontText=""; backText=""; documentUri=null; documentKind=0; side=0;\n    }\n\n'''
if 'private void clearTransientDocumentState()' not in s:s=s.replace(anchor,helper+anchor,1)
for sig,key in [('    private void home(){','Inicio'),('    private void clients(){','Clientes'),('    private void ocrPage(){','OCR'),('    private void policies(){','Pólizas')]:
    if sig in s:
        s=s.replace(sig,sig+'\n        activeMenu="'+key+'";',1)
P.write_text(s,encoding='utf-8')
print('navigation state and transient document isolation applied')
