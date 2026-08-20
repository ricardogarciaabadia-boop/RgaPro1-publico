from pathlib import Path
p=Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s=p.read_text(encoding='utf-8')
s=s.replace("z.append('\\\\n');","z.append(System.lineSeparator());")
s=s.replace("z.append('\\n');","z.append(System.lineSeparator());")
p.write_text(s,encoding='utf-8')
print('Fixed advanced policy relation newline literals')
