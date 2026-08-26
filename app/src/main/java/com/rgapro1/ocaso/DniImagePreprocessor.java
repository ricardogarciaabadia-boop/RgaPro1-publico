package com.rgapro1.ocaso;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Preparación de imágenes de DNI/NIE antes del OCR.
 * La prioridad es quitar el fondo de la fotografía, ampliar el documento
 * y generar variantes legibles sin destruir el original preparado.
 */
public final class DniImagePreprocessor {
    private static final int DETECT_W = 400;
    private static final int MAX_OCR_LONG_SIDE = 1800;

    private DniImagePreprocessor() {}

    public static Bitmap prepare(Bitmap source) {
        if (source == null) return null;
        Bitmap base = limitSize(source, 1800);
        Bitmap crop = cropDni(base);
        if (crop != base && base != source) base.recycle();
        Bitmap deskewed = cropDni(crop);
        if (deskewed != crop) crop.recycle();
        return limitSize(deskewed, MAX_OCR_LONG_SIDE);
    }

    /** Devuelve una versión monocroma con contraste reforzado. */
    public static Bitmap contrast(Bitmap source) {
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.45f,0,0,0,-55,
                0,1.45f,0,0,-55,
                0,0,1.45f,0,-55,
                0,0,0,1,0
        });
        gray.postConcat(contrast);
        p.setColorFilter(new ColorMatrixColorFilter(gray));
        c.drawBitmap(source, 0, 0, p);
        return out;
    }

    /** Variante muy contrastada útil para MRZ y caracteres pequeños. */
    public static Bitmap threshold(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];
        source.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int i = 0; i < pixels.length; i++) {
            int r = Color.red(pixels[i]), g = Color.green(pixels[i]), b = Color.blue(pixels[i]);
            int y = (299 * r + 587 * g + 114 * b) / 1000;
            int v = y >= 158 ? 255 : 0;
            pixels[i] = Color.rgb(v, v, v);
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    private static Bitmap cropDni(Bitmap source) {
        if (source.getWidth() < 80 || source.getHeight() < 80) return source;

        float ratio = source.getHeight() / (float) source.getWidth();
        int th = Math.max(80, Math.round(DETECT_W * ratio));
        Bitmap thumb = Bitmap.createScaledBitmap(source, DETECT_W, th, true);
        int[] px = new int[DETECT_W * th];
        thumb.getPixels(px, 0, DETECT_W, 0, 0, DETECT_W, th);

        // El fondo de las fotos aportadas es beige y el DNI es mucho más claro/azulado.
        // Usamos B-R + luminosidad para aislar la tarjeta sin depender de un color exacto.
        byte[] mask = new byte[px.length];
        int count = 0;
        for (int i = 0; i < px.length; i++) {
            int r = Color.red(px[i]), g = Color.green(px[i]), b = Color.blue(px[i]);
            int y = (299 * r + 587 * g + 114 * b) / 1000;
            if (b - r > -8 && y > 125) { mask[i] = 1; count++; }
        }

        // Cierre morfológico simple para unir las zonas del documento.
        mask = dilate(mask, DETECT_W, th, 4);
        mask = erode(mask, DETECT_W, th, 4);
        mask = erode(mask, DETECT_W, th, 2);
        mask = dilate(mask, DETECT_W, th, 2);

        int minX = DETECT_W, minY = th, maxX = -1, maxY = -1, area = 0;
        boolean[] seen = new boolean[mask.length];
        int[] q = new int[mask.length];
        int bestArea = 0, bx0 = 0, by0 = 0, bx1 = 0, by1 = 0;

        for (int start = 0; start < mask.length; start++) {
            if (mask[start] == 0 || seen[start]) continue;
            int head = 0, tail = 0;
            q[tail++] = start;
            seen[start] = true;
            minX = DETECT_W; minY = th; maxX = -1; maxY = -1; area = 0;
            while (head < tail) {
                int idx = q[head++];
                int x = idx % DETECT_W, y = idx / DETECT_W;
                area++;
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                int left = idx - 1, right = idx + 1, up = idx - DETECT_W, down = idx + DETECT_W;
                if (x > 0 && mask[left] != 0 && !seen[left]) { seen[left] = true; q[tail++] = left; }
                if (x + 1 < DETECT_W && mask[right] != 0 && !seen[right]) { seen[right] = true; q[tail++] = right; }
                if (y > 0 && mask[up] != 0 && !seen[up]) { seen[up] = true; q[tail++] = up; }
                if (y + 1 < th && mask[down] != 0 && !seen[down]) { seen[down] = true; q[tail++] = down; }
            }
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            float ar = ch == 0 ? 0f : cw / (float) ch;
            if (area > bestArea && area > DETECT_W * th * 0.015f && ar > 1.15f && ar < 1.95f) {
                bestArea = area; bx0 = minX; by0 = minY; bx1 = maxX + 1; by1 = maxY + 1;
            }
        }
        thumb.recycle();
        if (bestArea == 0 || count < DETECT_W * th * 0.01f) return source;

        int padX = Math.max(3, Math.round((bx1 - bx0) * 0.05f));
        int padY = Math.max(3, Math.round((by1 - by0) * 0.05f));
        float sx = source.getWidth() / (float) DETECT_W;
        float sy = source.getHeight() / (float) th;
        int x0 = Math.max(0, Math.round((bx0 - padX) * sx));
        int y0 = Math.max(0, Math.round((by0 - padY) * sy));
        int x1 = Math.min(source.getWidth(), Math.round((bx1 + padX) * sx));
        int y1 = Math.min(source.getHeight(), Math.round((by1 + padY) * sy));
        if (x1 - x0 < 120 || y1 - y0 < 70) return source;
        return Bitmap.createBitmap(source, x0, y0, x1 - x0, y1 - y0);
    }

    private static byte[] dilate(byte[] in, int w, int h, int r) {
        byte[] out = new byte[in.length];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            byte v=0;
            for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r)&&v==0;yy++)
                for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)
                    if(in[yy*w+xx]!=0){v=1;break;}
            out[y*w+x]=v;
        }
        return out;
    }

    private static byte[] erode(byte[] in, int w, int h, int r) {
        byte[] out = new byte[in.length];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            byte v=1;
            for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r)&&v!=0;yy++)
                for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx++)
                    if(in[yy*w+xx]==0){v=0;break;}
            out[y*w+x]=v;
        }
        return out;
    }

    private static Bitmap limitSize(Bitmap source, int maxLong) {
        int longSide = Math.max(source.getWidth(), source.getHeight());
        if (longSide <= maxLong) return source;
        float s = maxLong / (float) longSide;
        return Bitmap.createScaledBitmap(source, Math.round(source.getWidth()*s), Math.round(source.getHeight()*s), true);
    }
}
