package com.rgapro1.ocaso.domain.documents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps document captures ordered and supports multi-page documents. */
public final class DocumentCaptureSession {
    private final List<String> pages = new ArrayList<>();
    private boolean finished;

    public void addPage(String localUri) {
        if (finished || localUri == null || localUri.trim().isEmpty()) return;
        pages.add(localUri);
    }

    public List<String> getPages() {
        return Collections.unmodifiableList(pages);
    }

    public int pageCount() {
        return pages.size();
    }

    public void finish() {
        finished = true;
    }

    public boolean isFinished() {
        return finished;
    }
}
