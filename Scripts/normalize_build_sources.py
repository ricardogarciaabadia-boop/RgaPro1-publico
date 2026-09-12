from pathlib import Path

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java")
text = MAIN.read_text(encoding="utf-8")

old = 'if((!id.isEmpty()&&id.equalsIgnoreCase(old.optString("identityNumber","")))||(!holder.isEmpty()&&normalizeSearch(holder).equals(normalizeSearch(old.optString("holder","")))){'
new = 'if (((!id.isEmpty()) && id.equalsIgnoreCase(old.optString("identityNumber", ""))) || ((!holder.isEmpty()) && normalizeSearch(holder).equals(normalizeSearch(old.optString("holder", ""))))) {'
if old in text:
    text = text.replace(old, new, 1)

text = text.replace('.createPage();', '.create();')

def end_of_method(s: str, start: int) -> int:
    brace = s.find('{', start)
    if brace < 0:
        raise SystemExit('opening brace not found')
    depth = 0
    for i in range(brace, len(s)):
        if s[i] == '{': depth += 1
        elif s[i] == '}':
            depth -= 1
            if depth == 0: return i + 1
    raise SystemExit('unbalanced method')

def remove_all_methods(src: str, signatures) -> str:
    while True:
        hits = [(src.find(sig), sig) for sig in signatures]
        hits = [(p, sig) for p, sig in hits if p >= 0]
        if not hits: return src
        pos, sig = min(hits, key=lambda x: x[0])
        end = end_of_method(src, pos)
        src = src[:pos] + src[end:]

permission_signatures = (
    '    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){',
    '    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){',
)
text = remove_all_methods(text, permission_signatures)

unified = '''    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(grantResults.length==0||grantResults[0]!=PackageManager.PERMISSION_GRANTED){
            if(requestCode==POLICY_CAMERA_PERMISSION)Toast.makeText(this,"Permiso de cámara denegado.",Toast.LENGTH_LONG).show();
            return;
        }
        if(requestCode==CAMERA)takePhoto();
        else if(requestCode==POLICY_CAMERA_PERMISSION)startPolicyPageCamera();
    }

'''
marker = '    private void chooseImage()'
if marker not in text: raise SystemExit('chooseImage marker not found')
text = text.replace(marker, unified + marker, 1)

def remove_duplicate_methods(src: str, signature: str) -> str:
    first = src.find(signature)
    if first < 0: return src
    first_end = end_of_method(src, first)
    prefix = src[:first_end]
    rest = src[first_end:]
    while True:
        pos = rest.find(signature)
        if pos < 0: break
        end = end_of_method(rest, pos)
        rest = rest[:pos] + rest[end:]
    return prefix + rest

for sig in (
    '    private void addPolicyField(String label,String value,EditText field){',
    '    private String currentPolicyProduct(String raw){',
):
    text = remove_duplicate_methods(text, sig)

MAIN.write_text(text, encoding="utf-8")

exec(Path("Scripts/patch_policy_hogar_values.py").read_text(encoding="utf-8"), {"__name__": "__build_patch__"})

fixed = MAIN.read_text(encoding="utf-8")
fixed = fixed.replace('if ("Hogar".equalsIgnoreCase(product)) {', 'if ("Hogar".equalsIgnoreCase(p.optString("policyType", ""))) {', 1)
fixed = fixed.replace('if (!"Hogar".equalsIgnoreCase(product)) addPolicyField("CAPITAL",null,capitalE);', 'if (!"Hogar".equalsIgnoreCase(p.optString("policyType", ""))) addPolicyField("CAPITAL",null,capitalE);', 1)
MAIN.write_text(fixed, encoding="utf-8")

# Final client ficha layout: only client data + one button per policy type/number.
exec(Path("Scripts/patch_client_policy_list_ui.py").read_text(encoding="utf-8"), {"__name__": "__build_patch__"})

# Pólizas is a clean index: all saved policies are individual access buttons.
# Creating another policy is a separate action, so the last processed policy is never shown as an upload target.
exec(Path("Scripts/patch_policy_list_clean.py").read_text(encoding="utf-8"), {"__name__": "__build_patch__"})
print("Source normalization + corrected DNI engine + unified OCR input complete")
