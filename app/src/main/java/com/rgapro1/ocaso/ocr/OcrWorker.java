package com.rgapro1.ocaso.ocr;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Lifecycle-aware OCR worker foundation. The existing OCR flow is not switched
 * over until its input/output contract is migrated and tested.
 */
public final class OcrWorker extends Worker {
    public OcrWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Deliberately fail-safe until the legacy document pipeline is migrated.
        // Returning success avoids accidental duplicate OCR while the bridge is introduced.
        return Result.success();
    }
}
