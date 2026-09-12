from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')
old='''    private Button btn(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(box(primary?BLUE:Color.WHITE,18));b.setPadding(dp(10),dp(6),dp(10),dp(6));return b;}'''
new='''    private Button btn(String text,boolean primary){
        Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(box(primary?BLUE:Color.WHITE,18));b.setPadding(dp(10),dp(6),dp(10),dp(6));
        b.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_UP){
                b.setBackground(box(Color.RED,18));b.setTextColor(Color.WHITE);
            }
            return false;
        });
        return b;
    }'''
if old not in s:
    raise SystemExit('btn method not found')
s=s.replace(old,new,1)
P.write_text(s,encoding='utf-8')
print('button press feedback applied')
