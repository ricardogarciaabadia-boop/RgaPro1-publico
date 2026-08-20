from pathlib import Path
import re

p = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Open the Client 360 activity from the client list.
pattern = r'    private void detail\(JSONObject p\)\{.*?\n    private void policies\(\)'
replacement = '''    private void detail(JSONObject p){
        Intent i=new Intent(this,Client360Activity.class);
        i.putExtra("client_json",p.toString());
        startActivity(i);
    }
    private void policies()'''
s2, n = re.subn(pattern, replacement, s, flags=re.S)
if n != 1:
    raise SystemExit(f'detail replacement count={n}')
s = s2

# Replace the complete home side-menu area without depending on the exact old labels.
start = s.find('        side.addView(tv("MENÚ",13,MUTED,true)')
end = s.find('        main.addView(side,new LinearLayout.LayoutParams(dp(150),-1));', start)
if start < 0 or end < 0:
    raise SystemExit('home menu block not found: expected MENÚ and main.addView(side) markers')

menu = '''        side.addView(tv("MENÚ",13,MUTED,true),new LinearLayout.LayoutParams(-1,dp(38)));
        Button ocrMain=sideButton("📷  OCR PRINCIPAL");
        ocrMain.setTextSize(17); ocrMain.setTextColor(Color.WHITE); ocrMain.setBackground(bg(BLUE,18));
        ocrMain.setOnClickListener(v->scanDocument());
        side.addView(ocrMain,new LinearLayout.LayoutParams(-1,dp(76)));
        Button undo=sideButton("↩️  DESHACER\\nÚltima acción");
        undo.setTextSize(16); undo.setTextColor(NAVY); undo.setOnClickListener(v->undoLastAction());
        side.addView(undo,new LinearLayout.LayoutParams(-1,dp(70)));
        expandableMenu(side,"👤  CLIENTES",new String[]{"Buscar cliente","Nuevo / ficha"},new View.OnClickListener[]{v->clients(),v->clients()});
        expandableMenu(side,"▣  PÓLIZAS",new String[]{"Todas","Por tipo","Próximas bajas"},new View.OnClickListener[]{v->policies(),v->policies(),v->expiries()});
        expandableMenu(side,"📄  DOCUMENTOS",new String[]{"Escanear OCR","JPEG / fotos","PDF"},new View.OnClickListener[]{v->scanDocument(),v->scanDocument(),v->scanDocument()});
        expandableMenu(side,"🔒  SEGURIDAD",new String[]{"Bloqueo y biometría"},new View.OnClickListener[]{v->security()});
        Button logout=sideButton("Salir / bloquear"); logout.setOnClickListener(v->showLogin());
        side.addView(logout,new LinearLayout.LayoutParams(-1,dp(56)));
'''
s = s[:start] + menu + s[end:]

# Add expandable-menu helper once.
if 'private void expandableMenu(LinearLayout parent,String title,String[] options,View.OnClickListener[] listeners)' not in s:
    marker = '    private void page(String title,String sub){'
    helper = '''    private void expandableMenu(LinearLayout parent,String title,String[] options,View.OnClickListener[] listeners){
        LinearLayout box=col();
        Button head=sideButton(title+"  ▾"); head.setTextSize(14); box.addView(head,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout opts=col(); opts.setVisibility(View.GONE); box.addView(opts,new LinearLayout.LayoutParams(-1,-2));
        head.setOnClickListener(v->{boolean open=opts.getVisibility()==View.VISIBLE;opts.setVisibility(open?View.GONE:View.VISIBLE);head.setText(title+(open?"  ▾":"  ▴"));});
        for(int i=0;i<options.length;i++){Button b=sideButton("   • "+options[i]);b.setTextSize(13);b.setOnClickListener(listeners[i]);opts.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(4);parent.addView(box,lp);
    }
'''
    if marker not in s:
        raise SystemExit('page marker not found')
    s = s.replace(marker, helper + marker, 1)

# Make the back control large and explicit.
oldback = 'Button back=action("‹",false);back.setTextColor(Color.WHITE);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->home());h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));'
newback = 'Button back=action("↩️  VOLVER",true);back.setTextSize(18);back.setOnClickListener(v->home());h.addView(back,new LinearLayout.LayoutParams(-1,dp(62)));'
if oldback in s:
    s = s.replace(oldback,newback,1)

# Session/background locking is intentionally implemented only by patch_session_lock.py.
# Keeping a single onResume/onUserLeaveHint implementation prevents duplicate-method
# compilation failures when the clean-build workflow applies its patches in sequence.

p.write_text(s,encoding='utf-8')

# Register Client360Activity in the manifest if it is not already present.
m=Path('app/src/main/AndroidManifest.xml')
ms=m.read_text(encoding='utf-8')
if 'Client360Activity' not in ms:
    if '</application>' not in ms:
        raise SystemExit('application closing tag not found')
    ms=ms.replace('</application>','    <activity android:name=".Client360Activity" android:exported="false" />\n    </application>',1)
    m.write_text(ms,encoding='utf-8')
