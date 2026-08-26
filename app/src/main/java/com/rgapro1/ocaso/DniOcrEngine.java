package com.rgapro1.ocaso;

import android.graphics.Bitmap;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/** OCR de DNI/NIE con varias pasadas sobre imágenes recortadas y mejoradas. */
public final class DniOcrEngine {
    public interface Callback {
        void onSuccess(String frontText, String backText, Bitmap preparedFront, Bitmap preparedBack);
        void onFailure(Exception e);
    }

    private DniOcrEngine() {}

    public static void process(Bitmap front, Bitmap back, Callback callback) {
        final Bitmap preparedFront;
        final Bitmap preparedBack;
        try {
            preparedFront = DniImagePreprocessor.prepare(front);
            preparedBack = DniImagePreprocessor.prepare(back);
            if (preparedFront == null || preparedBack == null) throw new IllegalArgumentException("DNI/NIE sin imagen válida");
        } catch (Exception e) {
            callback.onFailure(e);
            return;
        }

        final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        final StringBuilder frontText = new StringBuilder();
        final StringBuilder backText = new StringBuilder();
        Bitmap[] frontVariants = variants(preparedFront);
        Bitmap[] backVariants = variants(preparedBack);
        processVariants(recognizer, frontVariants, 0, frontText, new Runnable() {
            @Override public void run() {
                processVariants(recognizer, backVariants, 0, backText, new Runnable() {
                    @Override public void run() {
                        recognizer.close();
                        recycleVariants(frontVariants, preparedFront);
                        recycleVariants(backVariants, preparedBack);
                        callback.onSuccess(frontText.toString(), backText.toString(), preparedFront, preparedBack);
                    }
                }, callback);
            }
        }, callback);
    }

    private static Bitmap[] variants(Bitmap base) {
        Bitmap contrast = DniImagePreprocessor.contrast(base);
        Bitmap threshold = DniImagePreprocessor.threshold(base);
        return new Bitmap[]{base, contrast, threshold};
    }

    private static void processVariants(TextRecognizer recognizer, Bitmap[] variants, int index,
                                        StringBuilder out, Runnable done, Callback callback) {
        if (index >= variants.length) { done.run(); return; }
        Bitmap image = variants[index];
        Task<Text> task = recognizer.process(InputImage.fromBitmap(image, 0));
        task.addOnSuccessListener(text -> {
            if (text != null && text.getText() != null && !text.getText().trim().isEmpty()) {
                if (out.length() > 0) out.append('\n');
                out.append(text.getText());
            }
            if (index > 0 && image != variants[0]) image.recycle();
            processVariants(recognizer, variants, index + 1, out, done, callback);
        }).addOnFailureListener(e -> {
            if (index > 0 && image != variants[0]) image.recycle();
            // Una pasada puede fallar por tamaño/memoria; no descartamos las demás.
            processVariants(recognizer, variants, index + 1, out, done, callback);
        });
    }

    private static void recycleVariants(Bitmap[] variants, Bitmap keep) {
        for (Bitmap b : variants) if (b != null && b != keep && !b.isRecycled()) b.recycle();
    }
}
