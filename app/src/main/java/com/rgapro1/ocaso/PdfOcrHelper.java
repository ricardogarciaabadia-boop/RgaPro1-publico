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

/** OCR de PDF página a página para limitar RAM y evitar lanzar múltiples Bitmaps simultáneamente. */
public final class PdfOcrHelper {
    public interface Callback {
        void onSuccess(String text);
        void onError(Exception error);
    }

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
                    Exception firstError = null;
                    try {
                        for (int i = 0; i < pages; i++) {
                            Bitmap bitmap = null;
                            android.graphics.pdf.PdfRenderer.Page page = null;
                            try {
                                page = renderer.openPage(i);
                                int width = Math.min(2400, Math.max(1200, page.getWidth() * 2));
                                int height = Math.min(3200, Math.max(1600, page.getHeight() * 2));
                                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                                String text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).getText();
                                all.append("\n--- Página ").append(i + 1).append(" ---\n");
                                if (text != null && !text.trim().isEmpty()) {
                                    all.append(text.trim());
                                } else {
                                    all.append("[No se pudo leer esta página]");
                                    if (firstError == null) firstError = new IOException("OCR vacío en página " + (i + 1));
                                }
                                all.append('\n');
                            } catch (Exception pageError) {
                                if (firstError == null) firstError = pageError;
                                all.append("\n--- Página ").append(i + 1).append(" ---\n[No se pudo leer esta página]\n");
                            } finally {
                                if (page != null) page.close();
                                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                            }
                        }
                    } finally {
                        recognizer.close();
                    }

                    final String result = all.toString().trim();
                    final Exception error = firstError;
                    if (result.isEmpty()) {
                        callback.onError(error != null ? error : new IOException("No se pudo leer ninguna página del PDF"));
                    } else {
                        callback.onSuccess(result);
                    }
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        }, "RgaPro-PdfOcr").start();
    }
}
