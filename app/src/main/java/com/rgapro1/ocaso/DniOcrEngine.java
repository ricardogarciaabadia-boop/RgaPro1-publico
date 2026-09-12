package com.rgapro1.ocaso;

import android.graphics.Bitmap;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public final class DniOcrEngine {
    public interface Callback { void onSuccess(String frontText,String backText,Bitmap preparedFront,Bitmap preparedBack); void onFailure(Exception e); }
    private static final class Candidate { Bitmap source; String text=""; int degrees; int front; int back; int total; }
    private interface CandidateCallback { void done(Candidate c); }
    private DniOcrEngine(){}

    public static void process(Bitmap first,Bitmap second,Callback callback){
        if(first==null||second==null){callback.onFailure(new IllegalArgumentException("Faltan las dos caras del DNI/NIE"));return;}
        classify(first,0,new Candidate(),a->classify(second,0,new Candidate(),b->finish(a,b,callback)));
    }

    private static void classify(Bitmap source,int degrees,Candidate best,CandidateCallback cb){
        if(degrees>=360){if(best.source==null)best.source=source;cb.done(best);return;}
        Bitmap rotated=DniImagePreprocessor.rotate(source,degrees);
        Bitmap small=limit(rotated,1400);
        TextRecognizer rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(InputImage.fromBitmap(small,0)).addOnSuccessListener(result->{
            String t=result==null?"":result.getText();int fs=frontScore(t),bs=backScore(t),score=Math.max(fs,bs);
            if(score>best.total){best.source=source;best.text=t;best.degrees=degrees;best.front=fs;best.back=bs;best.total=score;}
            rec.close();if(small!=rotated&&!small.isRecycled())small.recycle();if(rotated!=source&&!rotated.isRecycled())rotated.recycle();classify(source,degrees+90,best,cb);
        }).addOnFailureListener(e->{rec.close();if(small!=rotated&&!small.isRecycled())small.recycle();if(rotated!=source&&!rotated.isRecycled())rotated.recycle();classify(source,degrees+90,best,cb);});
    }

    private static void finish(Candidate a,Candidate b,Callback cb){
        boolean swap=(a.back+b.front)>(a.front+b.back);
        Candidate f=swap?b:a, r=swap?a:b;
        Bitmap pf=prepare(f.source,f.degrees),pb=prepare(r.source,r.degrees);
        if(pf==null||pb==null){cb.onFailure(new IllegalArgumentException("No se pudo recortar el DNI/NIE"));return;}
        TextRecognizer rec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(InputImage.fromBitmap(pf,0)).addOnSuccessListener(x->{String ft=x==null?"":x.getText();rec.process(InputImage.fromBitmap(pb,0)).addOnSuccessListener(y->{String bt=y==null?"":y.getText();rec.close();cb.onSuccess(ft,bt,pf,pb);}).addOnFailureListener(e->{rec.close();cb.onFailure(e);});}).addOnFailureListener(e->{rec.close();cb.onFailure(e);});
    }

    private static Bitmap prepare(Bitmap source,int degrees){Bitmap rotated=DniImagePreprocessor.rotate(source,degrees);Bitmap p=DniImagePreprocessor.prepare(rotated);if(p!=rotated&&rotated!=source&&!rotated.isRecycled())rotated.recycle();return p;}
    private static Bitmap limit(Bitmap b,int max){int l=Math.max(b.getWidth(),b.getHeight());if(l<=max)return b;float s=max/(float)l;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);}
    private static String norm(String s){return (s==null?"":s).toUpperCase(java.util.Locale.ROOT).replace('Á','A').replace('É','E').replace('Í','I').replace('Ó','O').replace('Ú','U');}
    private static int frontScore(String raw){String u=norm(raw);int s=0;if(u.contains("ESPANA"))s+=4;if(u.contains("DOCUMENTO NACIONAL"))s+=6;if(u.contains("APELLIDOS"))s+=6;if(u.contains("NOMBRE"))s+=5;if(u.contains("NACIONALIDAD"))s+=3;if(u.contains("NACIMIENTO"))s+=5;if(u.contains("SEXO"))s+=2;if(u.matches("(?s).*\\b\\d{8}[A-Z]\\b.*"))s+=5;return s;}
    private static int backScore(String raw){String u=norm(raw);int s=0;if(u.contains("IDESP"))s+=8;if(u.contains("DOMICILIO"))s+=6;if(u.contains("LUGAR DE NACIMIENTO"))s+=4;if(u.contains("EQUIPO"))s+=3;if(u.contains("HIJO DE"))s+=3;if(u.contains("<<<<"))s+=6;return s;}
}
