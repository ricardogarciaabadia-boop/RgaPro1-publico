from pathlib import Path

MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')
# The essential OCR generator uses raw Python strings; normalize the Java character literals.
s = s.replace(r"raw.replace('\\\\r','\\\\n')", r"raw.replace('\\r','\\n')")
s = s.replace(r".append('\\\\n')", r".append('\\n')")
MAIN.write_text(s, encoding='utf-8')
print('Corrected Java OCR newline escapes')
