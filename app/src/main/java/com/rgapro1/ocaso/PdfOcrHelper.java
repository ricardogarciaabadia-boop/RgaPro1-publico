package com.rgapro1.ocaso;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

/** OCR de PDF página a página. Prioriza legibilidad y no inventa campos cuando una página falla. */
public final class PdfOcrHelper {
    public interface Callback { void onSuccess(String text); void onError(Exception error); }
    private PdfOcrHelper() {}

    public static void process(Context context, Uri uri, Callback callback) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try (ParcelFileDescriptor pfd = appContext.getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd == null) throw new IOException("No se pudo abrir el PDF");
                try (android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(pfd)) {
                    final int pages = renderer.getPageCount();
                    if (pages == 0) throw new IOException("El PDF no contiene páginas");
                    TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    StringBuilder all = new StringBuilder();
                    int readablePages = 0;
                    try {
                        for (int i = 0; i < pages; i++) {
                            Bitmap bitmap = null;
                            android.graphics.pdf.PdfRenderer.Page page = null;
                            try {
                                page = renderer.openPage(i);
                                // Alta resolución, pero con límite para evitar OOM en móviles.
                                float scale = 3.0f;
                                int width = Math.min(3400, Math.max(1800, Math.round(page.getWidth() * scale)));
                                int height = Math.min(4600, Math.max(2400, Math.round(page.getHeight() * scale)));
                                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

                                String text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).getText();
                                all.append("\n--- Página ").append(i + 1).append(" ---\n");
                                if (text != null && !text.trim().isEmpty()) {
                                    readablePages++;
                                    all.append(clean(text));
                                } else {
                                    all.append("[PÁGINA SIN TEXTO DETECTABLE — REVISAR IMAGEN ORIGINAL]");
                                }
                                all.append('\n');
                            } catch (Exception pageError) {
                                all.append("\n--- Página ").append(i + 1).append(" ---\n[PÁGINA NO LEÍDA — REVISAR IMAGEN ORIGINAL]\n");
                            } finally {
                                if (page != null) page.close();
                                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                            }
                        }
                    } finally { recognizer.close(); }

                    if (readablePages == 0) {
                        callback.onError(new IOException("No se pudo leer ninguna página del PDF"));
                    } else {
                        callback.onSuccess(all.toString().trim());
                    }
                }
            } catch (Exception e) { callback.onError(e); }
        }, "RgaPro-PdfOcr").start();
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replace('\r', '\n').replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\\n\\n").trim();
    }
}
