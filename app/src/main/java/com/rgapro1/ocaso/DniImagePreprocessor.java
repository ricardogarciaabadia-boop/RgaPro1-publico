package com.rgapro1.ocaso;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/** Preparación de DNI/NIE: recorta la tarjeta después de corregir su orientación. */
public final class DniImagePreprocessor {
    private static final int DETECT_LONG=500, MAX_LONG=2200;
    private DniImagePreprocessor(){}
    public static Bitmap prepare(Bitmap source){if(source==null)return null;Bitmap base=limitSize(source,MAX_LONG);Bitmap crop=cropDni(base);if(crop!=base&&base!=source&&!base.isRecycled())base.recycle();return limitSize(crop,MAX_LONG);}
    public static Bitmap rotate(Bitmap source,int degrees){if(source==null||degrees%360==0)return source;android.graphics.Matrix m=new android.graphics.Matrix();m.postRotate(((degrees%360)+360)%360);return Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);}
    public static Bitmap contrast(Bitmap source){Bitmap out=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);ColorMatrix g=new ColorMatrix();g.setSaturation(0f);g.postConcat(new ColorMatrix(new float[]{1.45f,0,0,0,-55,0,1.45f,0,0,-55,0,0,1.45f,0,-55,0,0,0,1,0}));p.setColorFilter(new ColorMatrixColorFilter(g));c.drawBitmap(source,0,0,p);return out;}
    public static Bitmap threshold(Bitmap source){int w=source.getWidth(),h=source.getHeight();Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);int[] px=new int[w*h];source.getPixels(px,0,w,0,0,w,h);for(int i=0;i<px.length;i++){int y=(299*Color.red(px[i])+587*Color.green(px[i])+114*Color.blue(px[i]))/1000;int v=y>=158?255:0;px[i]=Color.rgb(v,v,v);}out.setPixels(px,0,w,0,0,w,h);return out;}

    private static Bitmap cropDni(Bitmap source){
        int sw=source.getWidth(),sh=source.getHeight();if(sw<120||sh<120)return source;
        float scale=DETECT_LONG/(float)Math.max(sw,sh);int dw=Math.max(120,Math.round(sw*scale)),dh=Math.max(120,Math.round(sh*scale));
        Bitmap thumb=Bitmap.createScaledBitmap(source,dw,dh,true);int[] px=new int[dw*dh];thumb.getPixels(px,0,dw,0,0,dw,dh);int[] gray=new int[px.length];
        for(int i=0;i<px.length;i++)gray[i]=(299*Color.red(px[i])+587*Color.green(px[i])+114*Color.blue(px[i]))/1000;
        byte[] edge=new byte[px.length];for(int y=1;y<dh-1;y++)for(int x=1;x<dw-1;x++){int i=y*dw+x;int g=Math.abs(gray[i-1]-gray[i+1])+Math.abs(gray[i-dw]-gray[i+dw]);edge[i]=(byte)(g>48?1:0);}
        edge=dilate(edge,dw,dh,4);edge=erode(edge,dw,dh,4);
        boolean[] seen=new boolean[edge.length];int[] q=new int[edge.length];int bestArea=0,bx=0,by=0,bw=0,bh=0;
        for(int start=0;start<edge.length;start++){
            if(edge[start]==0||seen[start])continue;int head=0,tail=0,minX=dw,minY=dh,maxX=-1,maxY=-1,area=0;q[tail++]=start;seen[start]=true;
            while(head<tail){int idx=q[head++],x=idx%dw,y=idx/dw;area++;if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;int a=idx-1,b=idx+1,c=idx-dw,d=idx+dw;if(x>0&&edge[a]!=0&&!seen[a]){seen[a]=true;q[tail++]=a;}if(x+1<dw&&edge[b]!=0&&!seen[b]){seen[b]=true;q[tail++]=b;}if(y>0&&edge[c]!=0&&!seen[c]){seen[c]=true;q[tail++]=c;}if(y+1<dh&&edge[d]!=0&&!seen[d]){seen[d]=true;q[tail++]=d;}}
            int cw=maxX-minX+1,ch=maxY-minY+1;float ar=ch==0?0:cw/(float)ch,ratio=area/(float)(dw*dh);
            if(area>bestArea&&ratio>0.025f&&ratio<0.75f&&ar>1.15f&&ar<2.20f&&cw>dw*.22f&&ch>dh*.12f){bestArea=area;bx=minX;by=minY;bw=cw;bh=ch;}
        }
        thumb.recycle();if(bestArea==0)return source;
        int pxPad=Math.max(8,Math.round(bw*.06f)),pyPad=Math.max(8,Math.round(bh*.06f));int x0=Math.max(0,Math.round((bx-pxPad)/scale)),y0=Math.max(0,Math.round((by-pyPad)/scale));int x1=Math.min(sw,Math.round((bx+bw+pxPad)/scale)),y1=Math.min(sh,Math.round((by+bh+pyPad)/scale));if(x1-x0<140||y1-y0<90)return source;return Bitmap.createBitmap(source,x0,y0,x1-x0,y1-y0);
    }
    private static byte[] dilate(byte[] in,int w,int h,int r){byte[] out=new byte[in.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){byte v=0;for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r)&&v==0;yy++)for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)if(in[yy*w+xx]!=0){v=1;break;}out[y*w+x]=v;}return out;}
    private static byte[] erode(byte[] in,int w,int h,int r){byte[] out=new byte[in.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){byte v=1;for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r)&&v!=0;yy++)for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)if(in[yy*w+xx]==0){v=0;break;}out[y*w+x]=v;}return out;}
    private static Bitmap limitSize(Bitmap source,int max){int l=Math.max(source.getWidth(),source.getHeight());if(l<=max)return source;float s=max/(float)l;return Bitmap.createScaledBitmap(source,Math.round(source.getWidth()*s),Math.round(source.getHeight()*s),true);}
}
