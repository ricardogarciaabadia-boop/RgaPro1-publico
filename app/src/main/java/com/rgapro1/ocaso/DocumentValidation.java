package com.rgapro1.ocaso;

/** Decide si un resultado OCR puede guardarse automáticamente. */
public final class DocumentValidation {
    public enum Decision { SAVE, REVIEW, REJECT }

    private DocumentValidation(){}

    public static Decision decide(DocumentClassifier.Result result){
        if(result==null || result.type==DocumentType.UNKNOWN) return Decision.REJECT;
        if(result.score>=18) return Decision.SAVE;
        if(result.score>=10) return Decision.REVIEW;
        return Decision.REJECT;
    }
}
