from pathlib import Path
p = Path('app/src/main/java/com/rgapro1/ocaso/Client360Activity.java')
s = p.read_text(encoding='utf-8')
# ClipData belongs to the Android framework, not androidx.core.content.
s = s.replace('import androidx.core.content.ClipData;\n', '')
p.write_text(s, encoding='utf-8')
print('Client360Activity ClipData import fixed')
