from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivity.java")
s = MAIN.read_text(encoding="utf-8")

start = s.find("    private boolean looksLikeDniDocumentFinal(String raw){")
if start < 0:
    raise SystemExit("No se encontró looksLikeDniDocumentFinal")
end = s.find("\n    private String labeledFinal", start)
if end < 0:
    raise SystemExit("No se encontró fin del clasificador DNI")

strict = r'''    private boolean looksLikeDniDocumentFinal(String raw){
        String u=(raw==null?"":raw).toUpperCase(Locale.ROOT);
        // Un número DNI por sí solo NO convierte un documento en DNI: las pólizas
        // contienen con frecuencia el DNI del tomador/asegurado.
        boolean id=Pattern.compile("(?<![0-9])[0-9]{8}[A-Z](?![A-Z0-9])").matcher(u).find()
                ||Pattern.compile("(?<![A-Z0-9])[XYZ][0-9]{7}[A-Z](?![A-Z0-9])").matcher(u).find();
        if(!id)return false;

        boolean policy=Pattern.compile("(?s)\\b(PÓLIZA|POLIZA|N[ÚU]MERO\\s+DE\\s+P[ÓO]LIZA|TOMADOR(?:A)?|CONTRATANTE|ASEGURADO(?:S|AS)?|ASEGURADORA|PRIMA|RECIBO|COBERTURA|CAPITAL(?:ES)?\\s+ASEGURADO(?:S)?|CONDICIONES\\s+(?:PARTICULARES|GENERALES)|VENCIMIENTO|EFECTO|FRANQUICIA|BENEFICIARIO(?:S)?|RIESGO|GARANT[ÍI]A(?:S)?)\\b").matcher(u).find();
        if(policy)return false;

        int dniLabels=0;
        String[] labels={"DNI","NOMBRE","APELLIDOS","FECHA DE NACIMIENTO","LUGAR DE NACIMIENTO","NACIONALIDAD","SEXO","SOPORTE","FECHA DE EXPEDICIÓN","VALIDEZ","DOMICILIO","ESPAÑA","ESPANA","IDESP"};
        for(String label:labels)if(u.contains(label))dniLabels++;

        boolean mrz=u.contains("<<") && Pattern.compile("[A-Z0-9<]{20,}").matcher(u).find();
        // Exigimos contexto inequívoco: al menos dos etiquetas propias del DNI
        // o una MRZ válida. Esto evita que una póliza con un DNI aislado se trate como DNI.
        return mrz || dniLabels>=2;
    }
'''
s = s[:start] + strict + s[end:]

# Nunca forzar DNI por dniMode cuando el contenido real no es un DNI.
old='boolean dniDocument=dniMode||looksLikeDniDocumentFinal(raw);'
new='boolean dniDocument=looksLikeDniDocumentFinal(raw);'
if old not in s:
    raise SystemExit("No se encontró la decisión dniDocument")
s=s.replace(old,new,1)

# Para documentos que no sean DNI, limpiar restos de campos específicos de DNI antes de guardar.
needle='else{\n                p.put("type",classifyPolicyTypeFinal(raw,p.optString("type","")));'
repl='''else{
                p.put("documentKind","POLIZA");
                p.remove("birthDate");p.remove("nationality");p.remove("sex");p.remove("birthPlace");p.remove("parents");
                p.remove("supportNumber");p.remove("issueDate");p.remove("validityDate");p.remove("expiry");p.remove("cif");
                p.put("type",classifyPolicyTypeFinal(raw,p.optString("type","")));'''
if needle not in s:
    raise SystemExit("No se encontró bloque de póliza")
s=s.replace(needle,repl,1)

MAIN.write_text(s,encoding="utf-8")
print("Strict document classifier v2 applied: DNI requires strong DNI context; policies with DNI numbers stay policies")
