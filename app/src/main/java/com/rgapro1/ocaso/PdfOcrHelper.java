package com.rgapro1.ocaso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.Locale;

/**
 * OCR de PDF página a página optimizado para pólizas escaneadas.
 *
 * Muchas pólizas reales llegan como fotografías/escaneos (CamScanner/Xodo),
 * con texto pequeño, fondo gris, sombras, inclinación y tablas. Para no perder
 * campos importantes se prueban dos imágenes por página: una nítida en color y
 * otra en escala de grises con contraste reforzado. Se conserva la variante
 * con mejor puntuación semántica para pólizas.
 */
public final class PdfOcrHelper {
    public interface Callback {
        void onSuccess(String text);
        void onError(Exception error);
    }

    private static final int MAX_WIDTH = 2800;
    private static final int MAX_HEIGHT = 3800;
    private static final int MIN_WIDTH = 1600;
    private static final int MIN_HEIGHT = 2200;

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
                            android.graphics.pdf.PdfRenderer.Page page = null;
                            Bitmap source = null;
                            Bitmap enhanced = null;
                            try {
                                page = renderer.openPage(i);
                                int[] size = renderSize(page.getWidth(), page.getHeight());
                                source = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                                page.render(source, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                                String bestText = recognize(recognizer, source);
                                enhanced = enhanceForPolicyOcr(source);

                                // En documentos escaneados la versión mejorada suele recuperar
                                // letras pequeñas, pero no la imponemos si empeora el texto.
                                String enhancedText = recognize(recognizer, enhanced);
                                if (scoreOcr(enhancedText) > scoreOcr(bestText)) {
                                    bestText = enhancedText;
                                }

                                // Si ambas variantes producen poco texto, un tercer intento
                                // binarizado rescata formularios antiguos en blanco y negro.
                                if (scoreOcr(bestText) < 55) {
                                    Bitmap binary = null;
                                    try {
                                        binary = binarizeForPolicyOcr(enhanced);
                                        String binaryText = recognize(recognizer, binary);
                                        if (scoreOcr(binaryText) > scoreOcr(bestText)) bestText = binaryText;
                                    } finally {
                                        if (binary != null && !binary.isRecycled()) binary.recycle();
                                    }
                                }

                                all.append("\n--- Página ").append(i + 1).append(" ---\n");
                                if (bestText != null && !bestText.trim().isEmpty()) {
                                    all.append(cleanOcr(bestText));
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
                                if (enhanced != null && !enhanced.isRecycled()) enhanced.recycle();
                                if (source != null && !source.isRecycled()) source.recycle();
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

    private static int[] renderSize(int pageWidth, int pageHeight) {
        float ratio = pageHeight / (float) Math.max(1, pageWidth);
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.round(pageWidth * 2.35f)));
        int height = Math.round(width * ratio);
        if (height > MAX_HEIGHT) {
            height = MAX_HEIGHT;
            width = Math.max(MIN_WIDTH, Math.round(height / ratio));
        }
        return new int[]{width, height};
    }

    private static String recognize(TextRecognizer recognizer, Bitmap bitmap) throws Exception {
        if (bitmap == null || bitmap.isRecycled()) return "";
        return Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).getText();
    }

    /** Contraste y escala de grises conservando bordes finos de letras y tablas. */
    private static Bitmap enhanceForPolicyOcr(Bitmap input) {
        Bitmap out = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        // Contraste ~1.35 y brillo ligeramente elevado para fondos de escáner.
        float c = 1.35f;
        float t = 128f * (1f - c) + 8f;
        ColorMatrix contrast = new ColorMatrix(new float[]{
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0
        });
        gray.postConcat(contrast);
        paint.setColorFilter(new ColorMatrixColorFilter(gray));
        canvas.drawBitmap(input, null, new Rect(0, 0, out.getWidth(), out.getHeight()), paint);
        return out;
    }

    /** Binarización conservadora para documentos muy lavados o con texto negro. */
    private static Bitmap binarizeForPolicyOcr(Bitmap input) {
        Bitmap out = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
        int w = input.getWidth();
        int h = input.getHeight();
        int[] pixels = new int[w];
        for (int y = 0; y < h; y++) {
            input.getPixels(pixels, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int p = pixels[x];
                int g = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000;
                int v = g < 178 ? 0 : 255;
                pixels[x] = Color.rgb(v, v, v);
            }
            out.setPixels(pixels, 0, w, 0, y, w, 1);
        }
        return out;
    }

    /**
     * Puntuación orientada a pólizas: no busca solo cantidad de caracteres,
     * sino presencia de encabezados, importes, fechas, coberturas y datos del tomador.
     */
    private static int scoreOcr(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String s = text.toUpperCase(Locale.ROOT);
        int score = Math.min(40, s.length() / 55);
        String[] anchors = {
                "OCASO", "POLIZA", "PÓLIZA", "TOMADOR", "ASEGURADO", "GARANTIA", "GARANTÍAS",
                "COBERTURA", "CAPITAL", "SUMA ASEGURADA", "IMPORTE", "PRIMA", "RECIBO",
                "FECHA", "DOMICILIO", "DIRECCION", "DIRECCIÓN", "DECESOS", "FALLECIMIENTO",
                "HOGAR", "VIDA", "ACCIDENTE", "ASISTENCIA", "BENEFICIARIOS", "RIESGO"
        };
        for (String anchor : anchors) if (s.contains(anchor)) score += 8;
        if (s.matches("(?s).*\\b[0-9]{5,12}\\b.*")) score += 8;
        if (s.matches("(?s).*\\b[0-3]?[0-9][./-][0-1]?[0-9][./-](?:19|20)[0-9]{2}\\b.*")) score += 8;
        if (s.matches("(?s).*\\b[0-9]{1,3}(?:[.,][0-9]{2})\\b.*")) score += 6;
        return score;
    }

    private static String cleanOcr(String text) {
        if (text == null) return "";
        return text.replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }
}
