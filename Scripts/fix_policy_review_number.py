from pathlib import Path

p = Path("app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java")
s = p.read_text(encoding="utf-8")
old = '        addPolicyField("NOMBRE Y APELLIDOS",null,holderE);\n'
new = '        addPolicyField("NÚMERO DE PÓLIZA",null,policyNumberE);\n        addPolicyField("NOMBRE Y APELLIDOS",null,holderE);\n'
if 'addPolicyField("NÚMERO DE PÓLIZA",null,policyNumberE);' not in s:
    if old not in s:
        raise SystemExit("policy review insertion point not found")
    s = s.replace(old, new, 1)
    p.write_text(s, encoding="utf-8")
    print("policy number field added")
else:
    print("policy number field already present")
