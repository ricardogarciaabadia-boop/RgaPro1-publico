package com.rgapro1.ocaso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/** Compact vector-style rendering of the approved RGA PRO identity. */
public final class RgaBrandView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shield = new Path();

    public RgaBrandView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight(), cx=w/2f;
        p.setStyle(Paint.Style.FILL); p.setColor(0xff06162f);
        c.drawRoundRect(0,0,w,h,22,22,p);
        shield.reset();
        shield.moveTo(cx,h*.12f); shield.lineTo(w*.83f,h*.26f); shield.lineTo(w*.79f,h*.65f);
        shield.quadTo(cx,h*.91f,cx,h*.91f); shield.quadTo(w*.21f,h*.65f,w*.17f,h*.26f); shield.close();
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3,w*.018f)); p.setColor(0xff168cff);
        c.drawPath(shield,p);
        p.setStyle(Paint.Style.FILL); p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(w*.30f); p.setColor(0xffffffff);
        c.drawText("RG",cx-w*.045f,h*.48f,p);
        p.setColor(0xff168cff); c.drawText("A",cx+w*.135f,h*.48f,p);
        p.setTextSize(w*.12f); c.drawText("PRO",cx,h*.66f,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2,w*.009f)); p.setColor(0xffffffff);
        float y=h*.80f, r=w*.055f;
        c.drawCircle(cx,y-r*.8f,r*.45f,p); c.drawCircle(cx-w*.10f,y,r*.36f,p); c.drawCircle(cx+w*.10f,y,r*.36f,p);
        c.drawLine(cx-w*.14f,y+r*.55f,cx-w*.06f,y+r*.05f); c.drawLine(cx+w*.14f,y+r*.55f,cx+w*.06f,y+r*.05f);
        p.setStyle(Paint.Style.FILL); p.setColor(0xff168cff); c.drawCircle(cx,y+r*.55f,r*.22f,p);
    }
}
