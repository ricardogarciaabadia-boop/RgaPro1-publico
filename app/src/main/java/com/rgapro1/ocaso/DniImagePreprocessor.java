package com.rgapro1.ocaso;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/** Preparación de DNI/NIE: recorta la tarjeta aunque la foto venga girada. */
public final class DniImagePreprocessor {
    private static final int DETECT_W = 500;
    private static final int MAX_LONG = 2200;
    private DniImagePreprocessor() {}

    public static Bitmap prepare(Bitmap source) {
        if (source == null) return null;
        Bitmap base = limitSize(source, MAX_LONG);
        Bitmap crop = cropDni(base);
        if (crop != base && base != source) base.recycle();
        return limitSize(crop, MAX_LONG);
    }

    public static Bitmap rotate(Bitmap source, int degrees) {
        if (source == null || degrees % 360 == 0) return source;
        int d = ((degrees % 360) + 360) % 360;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(d);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, true);
    }

    public static Bitmap contrast(Bitmap source) {
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        gray.postConcat(new ColorMatrix(new float[]{1.45f,0,0,0,-55,0,1.45f,0,0,-55,0,0,1.45f,0,-55,0,0,0,1,0}));
        p.setColorFilter(new ColorMatrixColorFilter(gray));
        c.drawBitmap(source, 0, 0, p);
        return out;
    }

    public static Bitmap threshold(Bitmap source) {
        int w=source.getWidth(), h=source.getHeight();
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int[] px=new int[w*h]; source.getPixels(px,0,w,0,0,w,h);
        for(int i=0;i<px.length;i++){
            int y=(299*Color.red(px[i])+587*Color.green(px[i])+114*Color.blue(px[i]))/1000;
            int v=y>=158?255:0; px[i]=Color.rgb(v,v,v);
        }
        out.setPixels(px,0,w,0,0,w,h); return out;
    }

    private static Bitmap cropDni(Bitmap source) {
        int sw=source.getWidth(), sh=source.getHeight();
        if(sw<100 || sh<100) return source;
        float scale=DETECT_W/(float)sw;
        int dw=DETECT_W, dh=Math.max(100,Math.round(sh*scale));
        Bitmap thumb=Bitmap.createScaledBitmap(source,dw,dh,true);
        int[] px=new int[dw*dh]; thumb.getPixels(px,0,dw,0,0,dw,dh);

        // Fondo = mediana de una franja exterior. La tarjeta se detecta por diferencia
        // cromática/tonal respecto a ese fondo, sin asumir que sea blanca o beige.
        int[] samples=new int[Math.max(32,2*dw+2*dh)]; int n=0;
        for(int x=0;x<dw;x+=4){samples[n++]=px[x]; samples[n++]=px[(dh-1)*dw+x];}
        for(int y=0;y<dh;y+=4){samples[n++]=px[y*dw]; samples[n++]=px[y*dw+dw-1];}
        int mr=medianChannel(samples,n,0), mg=medianChannel(samples,n,1), mb=medianChannel(samples,n,2);
        byte[] mask=new byte[px.length];
        for(int y=1;y<dh-1;y++){
            for(int x=1;x<dw-1;x++){
                int c=px[y*dw+x];
                int dr=Color.red(c)-mr,dg=Color.green(c)-mg,db=Color.blue(c)-mb;
                int dist=(int)Math.sqrt(dr*dr+dg*dg+db*db);
                int lum=(299*Color.red(c)+587*Color.green(c)+114*Color.blue(c))/1000;
                int lumDiff=Math.abs(lum-(299*mr+587*mg+114*mb)/1000);
                int edge=Math.abs(gray(px[y*dw+x-1])-gray(c))+Math.abs(gray(px[y*dw+x+1])-gray(c))+Math.abs(gray(px[(y-1)*dw+x])-gray(c))+Math.abs(gray(px[(y+1)*dw+x])-gray(c));
                if(dist>22 || lumDiff>18 || edge>85) mask[y*dw+x]=1;
            }
        }
        // Une la zona de la tarjeta y elimina letras/sombras aisladas.
        mask=dilate(mask,dw,dh,3); mask=erode(mask,dw,dh,3); mask=dilate(mask,dw,dh,2);
        boolean[] seen=new boolean[mask.length]; int[] q=new int[mask.length];
        int bestArea=0,bx0=0,by0=0,bx1=0,by1=0;
        for(int start=0;start<mask.length;start++){
            if(mask[start]==0||seen[start])continue;
            int head=0,tail=0,minX=dw,minY=dh,maxX=-1,maxY=-1,area=0;
            q[tail++]=start;seen[start]=true;
            while(head<tail){
                int idx=q[head++],x=idx%dw,y=idx/dw; area++;
                if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;
                int[] ns={idx-1,idx+1,idx-dw,idx+dw};
                if(x>0&&mask[ns[0]]!=0&&!seen[ns[0]]){seen[ns[0]]=true;q[tail++]=ns[0];}
                if(x+1<dw&&mask[ns[1]]!=0&&!seen[ns[1]]){seen[ns[1]]=true;q[tail++]=ns[1];}
                if(y>0&&mask[ns[2]]!=0&&!seen[ns[2]]){seen[ns[2]]=true;q[tail++]=ns[2];}
                if(y+1<dh&&mask[ns[3]]!=0&&!seen[ns[3]]){seen[ns[3]]=true;q[tail++]=ns[3];}
            }
            int bw=maxX-minX+1,bh=maxY-minY+1;
            float ar=bh==0?0f:bw/(float)bh;
            float areaRatio=area/(float)(dw*dh);
            if(area>bestArea && areaRatio>0.035f && areaRatio<0.90f && ar>1.18f && ar<2.10f && bw>dw*0.22f && bh>dh*0.12f){
                bestArea=area;bx0=minX;by0=minY;bx1=maxX+1;by1=maxY+1;
            }
        }
        thumb.recycle();
        if(bestArea==0) return source;
        int padX=Math.max(6,Math.round((bx1-bx0)*0.06f)),padY=Math.max(6,Math.round((by1-by0)*0.06f));
        int x0=Math.max(0,Math.round((bx0-padX)/scale)),y0=Math.max(0,Math.round((by0-padY)/scale));
        int x1=Math.min(sw,Math.round((bx1+padX)/scale)),y1=Math.min(sh,Math.round((by1+padY)/scale));
        if(x1-x0<140||y1-y0<90)return source;
        return Bitmap.createBitmap(source,x0,y0,x1-x0,y1-y0);
    }

    private static int gray(int c){return (299*Color.red(c)+587*Color.green(c)+114*Color.blue(c))/1000;}
    private static int medianChannel(int[] a,int n,int channel){
        int[] v=new int[n]; for(int i=0;i<n;i++){int c=a[i];v[i]=channel==0?Color.red(c):(channel==1?Color.green(c):Color.blue(c));}
        java.util.Arrays.sort(v); return v[n/2];
    }
    private static byte[] dilate(byte[] in,int w,int h,int r){byte[] out=new byte[in.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){byte v=0;for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r)&&v==0;yy++)for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)if(in[yy*w+xx]!=0){v=1;break;}out[y*w+x]=v;}return out;}
    private static byte[] erode(byte[] in,int w,int h,int r){byte[] out=new byte[in.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){byte v=1;for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r)&&v!=0;yy++)for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)if(in[yy*w+xx]==0){v=0;break;}out[y*w+x]=v;}return out;}
    private static Bitmap limitSize(Bitmap source,int maxLong){int l=Math.max(source.getWidth(),source.getHeight());if(l<=maxLong)return source;float s=maxLong/(float)l;return Bitmap.createScaledBitmap(source,Math.round(source.getWidth()*s),Math.round(source.getHeight()*s),true);}
}
