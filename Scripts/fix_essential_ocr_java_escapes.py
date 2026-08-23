from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')
# The essential OCR generator uses raw Python strings. Generated Java must contain a single backslash in char literals.
s = s.replace("raw.replace('\\\\r','\\\\n')", "raw.replace('\\r','\\n')")
s = s.replace(".append('\\\\n')", ".append('\\n')")
MAIN.write_text(s, encoding='utf-8')
print('Corrected Java OCR newline escapes')
