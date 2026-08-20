from pathlib import Path

p = Path('app/src/main/java/com/rgapro1/ocaso/Client360Activity.java')
s = p.read_text(encoding='utf-8')

# Client360Activity uses AlertDialog in the generated edit/add flows.
# Keep the import explicit so the clean build does not depend on MainActivity imports.
if 'import android.app.AlertDialog;' not in s:
    s = s.replace('package com.rgapro1.ocaso;\n', 'package com.rgapro1.ocaso;\n\nimport android.app.AlertDialog;\n', 1)

# ClipData belongs to the Android framework and is already covered by android.content.*.
# androidx.core.content does not provide ClipData.
s = s.replace('import androidx.core.content.ClipData;\n', '')

p.write_text(s, encoding='utf-8')
print('Client360Activity compile imports fixed')
