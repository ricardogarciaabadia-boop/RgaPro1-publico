from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/OcasoPolicyParser.java')
s = MAIN.read_text(encoding='utf-8')

def replace_method(src, signature, replacement):
    start = src.find(signature)
    if start < 0:
        raise SystemExit('method not found: ' + signature)
    brace = src.find('{', start)
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[:start] + replacement + src[i+1:]
    raise SystemExit('unbalanced method: ' + signature)

holder = r'''    private static String extractHolder(String[] lines){
        int[] b=sectionBounds(lines,new String[]{"DATOS DEL TOMADOR Y DOMICILIO","TOMADOR DEL SEGURO Y DOMICILIO","TOMADOR DEL SEGURO"},new String[]{"RIESGO/S ASEGURADO/S","RIESGOS ASEGURADOS","DURACION DEL CONTRATO","DURACIÓN DEL CONTRATO"});
        if(b[0]<0)return "";
        String[] labels={"NOMBRE Y APELLIDOS","NOMBRE","TOMADOR DEL SEGURO"};
        for(int i=b[0];i<b[1];i++){
            String line=lines[i];
            for(String label:labels){
                int p=line.indexOf(label);if(p<0)continue;
                String v=stripIdentityAndNoise(cutAtNextLabel(line.substring(p+label.length())));
                if(isRealPersonName(v))return v;
                if(v.isEmpty()&&i+1<b[1]){
                    v=stripIdentityAndNoise(cutAtNextLabel(lines[i+1]));
                    if(isRealPersonName(v))return v;
                }
            }
        }
        return "";
    }
'''

def helpers(src):
    old='if(isCompanyOrOffice(u)||u.contains("POR CUENTA")'
    new='if(u.equals("TOMADOR DEL SEGURO")||u.equals("TOMADOR")||u.equals("NOMBRE")||u.equals("NOMBRE Y APELLIDOS"))return false;if(isCompanyOrOffice(u)||u.contains("POR CUENTA")'
    if old not in src: raise SystemExit('person-name guard not found')
    return src.replace(old,new,1)

s=replace_method(s,'    private static String extractHolder(String[] lines){',holder)
s=helpers(s)
MAIN.write_text(s,encoding='utf-8')
print('fix: tomador label is a field label; actual person value is read from the following OCR line')
