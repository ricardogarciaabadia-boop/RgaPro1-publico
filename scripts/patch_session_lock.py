from pathlib import Path

p = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = p.read_text(encoding='utf-8')

# The clean-build workflow applies several patches to MainActivity in sequence.
# Earlier versions could leave a lifecycle hook behind and then add a second copy,
# producing Javac errors such as "method onResume() is already defined".
# Remove every generated copy of the three lifecycle hooks before installing one
# canonical implementation.
def remove_method(source: str, name: str) -> str:
    marker = '@Override'
    pos = 0
    while True:
        sig = source.find(f'{name}(', pos)
        if sig < 0:
            return source
        # Only remove Java lifecycle declarations, not calls such as super.onResume().
        line_start = source.rfind('\n', 0, sig) + 1
        line = source[line_start:sig]
        if 'void ' + name in line and '@Override' in source[max(0, line_start-80):line_start]:
            start = source.rfind('@Override', 0, line_start)
            brace = source.find('{', sig)
            if brace < 0:
                return source
            depth = 0
            end = brace
            in_string = False
            escape = False
            while end < len(source):
                ch = source[end]
                if in_string:
                    if escape:
                        escape = False
                    elif ch == '\\':
                        escape = True
                    elif ch == '"':
                        in_string = False
                else:
                    if ch == '"':
                        in_string = True
                    elif ch == '{':
                        depth += 1
                    elif ch == '}':
                        depth -= 1
                        if depth == 0:
                            end += 1
                            if end < len(source) and source[end] == '\n':
                                end += 1
                            source = source[:start] + source[end:]
                            pos = start
                            break
                end += 1
            else:
                return source
        else:
            pos = sig + len(name) + 1

for method in ('onUserLeaveHint', 'onResume', 'onStop'):
    s = remove_method(s, method)

marker = '    private final Executor biometricExecutor=Executors.newSingleThreadExecutor();\n'
if marker not in s:
    raise SystemExit('biometric executor marker not found')

block = '''    private boolean rgaProUserLeftApp=false;
    private boolean rgaProScreenLocked=false;

    @Override public void onUserLeaveHint(){
        super.onUserLeaveHint();
        rgaProUserLeftApp=true;
    }

    @Override protected void onResume(){
        super.onResume();
        boolean wasLocked=rgaProScreenLocked;
        rgaProUserLeftApp=false;
        if(wasLocked){
            rgaProScreenLocked=false;
            currentUser=null;
            showLogin();
        }
    }

    @Override protected void onStop(){
        super.onStop();
        if(rgaProUserLeftApp && currentUser!=null){
            rgaProScreenLocked=true;
            currentUser=null;
        }
    }

'''
s = s.replace(marker, marker + block, 1)
p.write_text(s, encoding='utf-8')
