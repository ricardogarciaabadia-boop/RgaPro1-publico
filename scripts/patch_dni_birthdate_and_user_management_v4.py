from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/rgapro1/ocaso/MainActivity.java"

s = JAVA.read_text(encoding="utf-8")

# This patch is intentionally conservative: it fixes only compile-time damage
# introduced by older DNI/user-management patch variants.
# Never reference local OCR EditText variables from a method where they do not exist.
s = re.sub(
    r'if\(dniMode\)\{[^\n{}]*?setVisibility\(View\.GONE\);[^\n{}]*?\}',
    'if(dniMode){}',
    s,
    count=1,
)

# The OCR saveClient call must match the current 22-argument signature.
# The generated OCR dialog has 17 EditTexts; the current method additionally
# expects four String values before the final raw OCR text. Keep the existing
# selected id/type and supply the remaining empty metadata fields.
needle = 'saveClient(holder,surname,name,dni,cif,birth,nationality,sex,address,birthPlace,parents,support,issue,validity,phone,email,number,chosenId,chosenType,raw)'
replacement = 'saveClient(holder,surname,name,dni,cif,birth,nationality,sex,address,birthPlace,parents,support,issue,validity,phone,email,number,chosenId,chosenType,"","",raw)'
if needle in s:
    s = s.replace(needle, replacement, 1)

# Ensure Java regex uses a valid escaped digit class if older generated text remains.
s = s.replace('matches("\\d{6}")', 'matches("\\\\d{6}")')
s = s.replace("matches('\\d{6}')", 'matches("\\\\d{6}")')

JAVA.write_text(s, encoding="utf-8")
print("Fixed DNI/user-management compile compatibility")
