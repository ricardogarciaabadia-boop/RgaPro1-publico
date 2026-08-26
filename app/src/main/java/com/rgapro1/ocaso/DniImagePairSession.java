package com.rgapro1.ocaso;

import android.net.Uri;

/**
 * Sesión de captura DNI: permite trabajar con anverso y reverso
 * antes de ejecutar OCR. Evita tratar un DNI fotografiado como una sola imagen.
 */
public class DniImagePairSession {
    private Uri frontImage;
    private Uri backImage;

    public void setFrontImage(Uri uri){
        frontImage = uri;
    }

    public void setBackImage(Uri uri){
        backImage = uri;
    }

    public Uri getFrontImage(){
        return frontImage;
    }

    public Uri getBackImage(){
        return backImage;
    }

    public boolean hasFront(){
        return frontImage != null;
    }

    public boolean hasBack(){
        return backImage != null;
    }

    public boolean readyForOcr(){
        return hasFront() && hasBack();
    }

    public String status(){
        if(!hasFront() && !hasBack()) return "Pendiente: añadir anverso y reverso";
        if(!hasFront()) return "Pendiente: añadir anverso";
        if(!hasBack()) return "Pendiente: añadir reverso";
        return "DNI completo listo para OCR";
    }
}
