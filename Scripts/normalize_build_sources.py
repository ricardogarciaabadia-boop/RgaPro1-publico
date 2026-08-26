from pathlib import Path

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java")
text = MAIN.read_text(encoding="utf-8")
old = 'if((!id.isEmpty()&&id.equalsIgnoreCase(old.optString("identityNumber","")))||(!holder.isEmpty()&&normalizeSearch(holder).equals(normalizeSearch(old.optString("holder","")))){'
new = 'if (((!id.isEmpty()) && id.equalsIgnoreCase(old.optString("identityNumber", ""))) || ((!holder.isEmpty()) && normalizeSearch(holder).equals(normalizeSearch(old.optString("holder", ""))))) {'
if old in text:
    text = text.replace(old, new, 1)
MAIN.write_text(text, encoding="utf-8")
print("Source normalization complete")
