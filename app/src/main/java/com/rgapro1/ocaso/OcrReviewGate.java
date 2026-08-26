package com.rgapro1.ocaso;

/**
 * Control del paso obligatorio de revisión OCR.
 * No se deben guardar documentos ni clientes hasta que el usuario acepte los datos.
 */
public final class OcrReviewGate {
    private boolean accepted = false;
    private String frontImagePath = "";
    private String backImagePath = "";

    public void setFrontImage(String path) {
        frontImagePath = path == null ? "" : path;
    }

    public void setBackImage(String path) {
        backImagePath = path == null ? "" : path;
    }

    public boolean hasFront() {
        return !frontImagePath.isEmpty();
    }

    public boolean hasBack() {
        return !backImagePath.isEmpty();
    }

    public boolean hasCompleteDni() {
        return hasFront() && hasBack();
    }

    public String getFrontImagePath() {
        return frontImagePath;
    }

    public String getBackImagePath() {
        return backImagePath;
    }

    public void accept() {
        accepted = true;
    }

    public void reject() {
        accepted = false;
    }

    public boolean canSave() {
        return accepted;
    }
}
