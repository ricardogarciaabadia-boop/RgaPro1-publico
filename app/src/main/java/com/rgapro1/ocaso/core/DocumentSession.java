package com.rgapro1.ocaso.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** State machine for identity and multipage document capture. */
public final class DocumentSession {
    public enum Type { IDENTITY, MULTIPAGE }
    public enum Side { FRONT, BACK, PAGE }
    private final Type type;
    private final List<Page> pages = new ArrayList<>();
    private boolean finished;

    public DocumentSession(Type type) {
        if (type == null) throw new IllegalArgumentException("Document type is required");
        this.type = type;
    }

    public Type getType() { return type; }

    public void add(String path, String ocrText, Side side) {
        if (finished) throw new IllegalStateException("Session already finished");
        if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("Document path is required");
        if (side == null) throw new IllegalArgumentException("Document side is required");

        if (type == Type.IDENTITY) {
            if (pages.isEmpty() && side != Side.FRONT) {
                throw new IllegalStateException("The first identity image must be the front side");
            }
            if (pages.size() == 1 && side != Side.BACK) {
                throw new IllegalStateException("The second identity image must be the back side");
            }
            if (pages.size() >= 2) {
                throw new IllegalStateException("Identity documents accept exactly front and back");
            }
        } else if (side != Side.PAGE) {
            throw new IllegalStateException("Multipage documents must use PAGE side");
        }

        pages.add(new Page(pages.size() + 1, side, path.trim(), ocrText == null ? "" : ocrText));
    }

    public boolean requiresBackSide() {
        return type == Type.IDENTITY && pages.size() == 1 && pages.get(0).side == Side.FRONT;
    }

    public boolean canFinish() {
        return type == Type.MULTIPAGE ? !pages.isEmpty() : pages.size() == 2;
    }

    public void finish() {
        if (!canFinish()) throw new IllegalStateException("Document incomplete");
        finished = true;
    }

    public boolean isFinished() { return finished; }

    public List<Page> getPages() { return Collections.unmodifiableList(pages); }

    public static final class Page {
        public final int number;
        public final Side side;
        public final String path;
        public final String ocrText;

        Page(int number, Side side, String path, String ocrText) {
            this.number = number;
            this.side = side;
            this.path = path;
            this.ocrText = ocrText;
        }
    }
}
