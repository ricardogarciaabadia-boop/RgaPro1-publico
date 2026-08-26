from pathlib import Path

MAIN = Path("app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java")
text = MAIN.read_text(encoding="utf-8")

old = 'if((!id.isEmpty()&&id.equalsIgnoreCase(old.optString("identityNumber","")))||(!holder.isEmpty()&&normalizeSearch(holder).equals(normalizeSearch(old.optString("holder","")))){'
new = 'if (((!id.isEmpty()) && id.equalsIgnoreCase(old.optString("identityNumber", ""))) || ((!holder.isEmpty()) && normalizeSearch(holder).equals(normalizeSearch(old.optString("holder", ""))))) {'
if old in text:
    text = text.replace(old, new, 1)


def remove_duplicate_methods(src: str, signature: str) -> str:
    """Keep the first implementation of a method signature and remove later copies."""
    first = src.find(signature)
    if first < 0:
        return src

    def end_of_method(s: str, start: int) -> int:
        brace = s.find('{', start)
        if brace < 0:
            raise SystemExit(f'opening brace not found: {signature}')
        depth = 0
        for i in range(brace, len(s)):
            if s[i] == '{':
                depth += 1
            elif s[i] == '}':
                depth -= 1
                if depth == 0:
                    return i + 1
        raise SystemExit(f'unbalanced method: {signature}')

    first_end = end_of_method(src, first)
    prefix = src[:first_end]
    rest = src[first_end:]
    while True:
        pos = rest.find(signature)
        if pos < 0:
            break
        end = end_of_method(rest, pos)
        rest = rest[:pos] + rest[end:]
    return prefix + rest


# Several historical patch scripts appended the same helper more than once.
# Normalize them deterministically so CI never depends on patch order/count.
for sig in (
    '    private void addPolicyField(String label,String value,EditText field){',
    '    private String currentPolicyProduct(String raw){',
):
    text = remove_duplicate_methods(text, sig)

MAIN.write_text(text, encoding="utf-8")
print("Source normalization complete")
