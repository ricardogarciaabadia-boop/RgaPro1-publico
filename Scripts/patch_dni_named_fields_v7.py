from pathlib import Path

P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')

def replace_method(src, signature, replacement):
    a=src.find(signature)
    if a<0: raise SystemExit('method not found: '+signature)
    b=src.find('{',a); d=0
    for i in range(b,len(src)):
        if src[i]=='{': d+=1
        elif src[i]=='}':
            d-=1
            if d==0:return src[:a]+replacement+src[i+1:]
    raise SystemExit('unbalanced method')

method=r'''    private String extractNameRobust(String text){
        String u=normalizeOcrIdentity(text).replace('\r','\n');
        String surnames="", name="";
        String[] lines=u.split("\\n");
        for(int i=0;i<lines.length;i++){
            String l=lines[i].trim().replaceAll("\\s+"," ");
            if(l.startsWith("APELLIDOS")){
                String v=l.replaceFirst("^APELLIDOS?\\s*[:.-]?\\s*","").trim();
                if(!v.isEmpty()&&!v.equals("APELLIDOS"))surnames=v;
                else if(i+1<lines.length)surnames=lines[++i].trim();
            }
            if(l.startsWith("NOMBRE")){
                String v=l.replaceFirst("^NOMBRES?\\s*[:.-]?\\s*","").trim();
                if(!v.isEmpty()&&!v.equals("NOMBRE"))name=v;
                else if(i+1<lines.length)name=lines[++i].trim();
            }
        }
        if(surnames.isEmpty()){
            Matcher m=Pattern.compile("(?i)APELLIDOS\\s*[:.-]?\\s*([A-ZÁÉÍÓÚÑ]+(?:\\s+[A-ZÁÉÍÓÚÑ]+)+)").matcher(u);
            if(m.find())surnames=m.group(1).trim();
        }
        if(name.isEmpty()){
            Matcher m=Pattern.compile("(?i)NOMBRE\\s*[:.-]?\\s*([A-ZÁÉÍÓÚÑ]+)").matcher(u);
            if(m.find())name=m.group(1).trim();
        }
        if(!name.isEmpty()&&!surnames.isEmpty())return (name+" "+surnames).replaceAll("\\s+"," ").trim();
        return !name.isEmpty()?name:surnames;
    }
'''
s=replace_method(s,'    private String extractNameRobust(String text){',method)
P.write_text(s,encoding='utf-8')
print('DNI named fields v7 applied: explicit NOMBRE/APELLIDOS labels, output NOMBRE + APELLIDOS')
