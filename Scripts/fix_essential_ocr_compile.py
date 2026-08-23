from pathlib import Path
MAIN = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')
s = s.replace('            d.mrz=mrzText;\n', '')
MAIN.write_text(s, encoding='utf-8')
print('Removed unsupported MRZ field assignment')
