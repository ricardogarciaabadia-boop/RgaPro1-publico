from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/DniImagePreprocessor.java')
s=P.read_text(encoding='utf-8')

def end_of_method(src,start):
    brace=src.find('{',start)
    if brace<0: raise SystemExit('opening brace not found')
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return i+1
    raise SystemExit('unbalanced method')

method=r'''    /** Endereza inclinaciones pequeñas mediante concentración de bordes en filas/columnas. */
    public static Bitmap deskew(Bitmap source){
        if(source==null)return null;int sw=source.getWidth(),sh=source.getHeight();if(sw<160||sh<160)return source;
        float scale=Math.min(1f,700f/Math.max(sw,sh));int w=Math.max(180,Math.round(sw*scale)),h=Math.max(180,Math.round(sh*scale));
        Bitmap thumb=Bitmap.createScaledBitmap(source,w,h,true);int[] px=new int[w*h];thumb.getPixels(px,0,w,0,0,w,h);int[] g=new int[px.length];
        for(int i=0;i<px.length;i++)g[i]=(299*Color.red(px[i])+587*Color.green(px[i])+114*Color.blue(px[i]))/1000;
        double best=-1,bestAngle=0;int[] row=new int[h],col=new int[w];
        for(int ai=-12;ai<=12;ai++){
            java.util.Arrays.fill(row,0);java.util.Arrays.fill(col,0);double a=ai*Math.PI/180.0,ca=Math.cos(a),sa=Math.sin(a);int count=0;
            for(int y=2;y<h-2;y+=2)for(int x=2;x<w-2;x+=2){int i=y*w+x;int e=Math.abs(g[i-1]-g[i+1])+Math.abs(g[i-w]-g[i+w]);if(e<90)continue;
                int rx=(int)Math.round((x-w/2.0)*ca-(y-h/2.0)*sa+w/2.0),ry=(int)Math.round((x-w/2.0)*sa+(y-h/2.0)*ca+h/2.0);
                if(rx>=0&&rx<w&&ry>=0&&ry<h){row[ry]++;col[rx]++;count++;}
            }
            java.util.Arrays.sort(row);java.util.Arrays.sort(col);double score=0;int top=Math.min(18,row.length);for(int k=0;k<top;k++)score+=row[row.length-1-k];top=Math.min(18,col.length);for(int k=0;k<top;k++)score+=col[col.length-1-k];
            if(count>0)score/=Math.sqrt(count);if(score>best){best=score;bestAngle=ai;}
        }
        thumb.recycle();if(Math.abs(bestAngle)<1.0)return source;android.graphics.Matrix m=new android.graphics.Matrix();m.postRotate((float)-bestAngle);return Bitmap.createBitmap(source,0,0,sw,sh,m,true);
    }
'''
start=s.find('    public static Bitmap deskew(Bitmap source){')
if start>=0:s=s[:start]+method+s[end_of_method(s,start):]
else:
    marker='    public static Bitmap contrast(Bitmap source){'
    if marker not in s: raise SystemExit('contrast marker not found')
    s=s.replace(marker,method+marker,1)
old='Bitmap base=limitSize(source,MAX_LONG);Bitmap crop=cropDni(base);'
new='Bitmap base=limitSize(source,MAX_LONG);Bitmap straight=deskew(base);Bitmap crop=cropDni(straight);if(crop!=straight&&!straight.isRecycled()&&straight!=base)straight.recycle();'
if old in s:s=s.replace(old,new,1)
P.write_text(s,encoding='utf-8')
print('Document deskew preprocessing applied')
