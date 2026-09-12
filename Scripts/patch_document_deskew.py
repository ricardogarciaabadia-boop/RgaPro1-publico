from pathlib import Path
P=Path('app/src/main/java/com/rgapro1/ocaso/DniImagePreprocessor.java')
s=P.read_text(encoding='utf-8')
if 'public static Bitmap deskew(Bitmap source)' not in s:
    method=r'''    /** Endereza pequeñas inclinaciones de documentos antes del OCR. */
    public static Bitmap deskew(Bitmap source){
        if(source==null)return null;int sw=source.getWidth(),sh=source.getHeight();if(sw<160||sh<160)return source;
        float scale=Math.min(1f,650f/Math.max(sw,sh));int w=Math.max(160,Math.round(sw*scale)),h=Math.max(160,Math.round(sh*scale));
        Bitmap thumb=Bitmap.createScaledBitmap(source,w,h,true);int[] px=new int[w*h];thumb.getPixels(px,0,w,0,0,w,h);int[] g=new int[px.length];
        for(int i=0;i<px.length;i++)g[i]=(299*Color.red(px[i])+587*Color.green(px[i])+114*Color.blue(px[i]))/1000;
        double best=-1,bestAngle=0;
        for(int ai=-12;ai<=12;ai++){
            double a=ai*Math.PI/180.0,ca=Math.cos(a),sa=Math.sin(a),score=0;
            for(int y=2;y<h-2;y+=2)for(int x=2;x<w-2;x+=2){int i=y*w+x;int e=Math.abs(g[i-1]-g[i+1])+Math.abs(g[i-w]-g[i+w]);if(e<80)continue;
                double rx=(x-w/2.0)*ca-(y-h/2.0)*sa+w/2.0,ry=(x-w/2.0)*sa+(y-h/2.0)*ca+h/2.0;
                double fx=rx-Math.rint(rx),fy=ry-Math.rint(ry);score+=Math.exp(-12*fx*fx)+Math.exp(-12*fy*fy);
            }
            if(score>best){best=score;bestAngle=ai;}
        }
        thumb.recycle();if(Math.abs(bestAngle)<1.0)return source;
        android.graphics.Matrix m=new android.graphics.Matrix();m.postRotate((float)-bestAngle);
        return Bitmap.createBitmap(source,0,0,sw,sh,m,true);
    }
'''
    s=s.replace('    public static Bitmap contrast(Bitmap source){',method+'    public static Bitmap contrast(Bitmap source){',1)
# Apply deskew inside the shared prepare path so both DNI and document-derived images benefit.
old='public static Bitmap prepare(Bitmap source){if(source==null)return null;Bitmap base=limitSize(source,MAX_LONG);Bitmap crop=cropDni(base);if(crop!=base&&base!=source&&!base.isRecycled())base.recycle();return limitSize(crop,MAX_LONG);}'
if old in s:
    new='public static Bitmap prepare(Bitmap source){if(source==null)return null;Bitmap base=limitSize(source,MAX_LONG);Bitmap straight=deskew(base);Bitmap crop=cropDni(straight);if(crop!=straight&&!straight.isRecycled()&&straight!=base)straight.recycle();if(base!=source&&!base.isRecycled())base.recycle();return limitSize(crop,MAX_LONG);}'
    s=s.replace(old,new,1)
P.write_text(s,encoding='utf-8')
print('Document deskew preprocessing applied')
