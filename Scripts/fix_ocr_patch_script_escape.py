from pathlib import Path
p=Path("Scripts/patch_ocr_front_reliable.py")
s=p.read_text(encoding="utf-8")
s=s.replace(".replace('\\\\\\\\r','\\\\\\\\n')", ".replace('\\\\r','\\\\n')")
p.write_text(s,encoding="utf-8")
print("Normalized OCR patch script escapes")
