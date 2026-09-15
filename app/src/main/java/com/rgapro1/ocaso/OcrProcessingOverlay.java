package com.rgapro1.ocaso;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Indicador sencillo de procesamiento OCR para evitar pantallas aparentemente bloqueadas. */
public final class OcrProcessingOverlay {
    private OcrProcessingOverlay(){}

    public static AlertDialog show(Context context,String message){
        LinearLayout box=new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(32,24,32,24);
        ProgressBar bar=new ProgressBar(context);
        TextView text=new TextView(context);
        text.setText(message==null?"Procesando documento…":message);
        text.setTextSize(16);
        text.setTextColor(Color.DKGRAY);
        text.setPadding(24,0,0,0);
        box.addView(bar,new LinearLayout.LayoutParams(72,72));
        box.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        AlertDialog d=new AlertDialog.Builder(context).setView(box).setCancelable(false).create();
        d.show();
        return d;
    }
}
