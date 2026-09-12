from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/MainActivityV2.java')
s=P.read_text(encoding='utf-8')
start=s.find('    private Button btn(String text,boolean primary){')
if start<0: raise SystemExit('btn method not found')
brace=s.find('{',start);depth=0;end=-1
for i in range(brace,len(s)):
    if s[i]=='{': depth+=1
    elif s[i]=='}':
        depth-=1
        if depth==0: end=i+1; break
if end<0: raise SystemExit('btn method unbalanced')
new='''    private Button btn(String text,boolean primary){
        Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);
        boolean ocrButton=text!=null && text.toUpperCase(Locale.ROOT).contains("PROCESAR OCR");
        b.setTextColor(ocrButton?Color.WHITE:TEXT);b.setBackground(box(ocrButton?BLUE:Color.WHITE,18));b.setPadding(dp(10),dp(6),dp(10),dp(6));
        b.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_UP){
                b.setBackground(box(Color.RED,18));b.setTextColor(Color.WHITE);
                b.postDelayed(()->{b.setBackground(box(ocrButton?BLUE:Color.WHITE,18));b.setTextColor(ocrButton?Color.WHITE:TEXT);},350);
            }
            return false;
        });
        return b;
    }'''
s=s[:start]+new+s[end:]
P.write_text(s,encoding='utf-8');print('button feedback transient red applied')