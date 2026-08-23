from pathlib import Path
p=Path("Scripts/patch_ocr_front_reliable.py")
lines=p.read_text(encoding="utf-8").splitlines(True)
for i,line in enumerate(lines):
    if ".replace(" in line and "\\\\r" in line:
        lines[i]='                .replace("\\r","\\n")\n'
p.write_text("".join(lines),encoding="utf-8")
print("Forced valid Java newline escape")
