package com.rgapro1.ocaso;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Clasificador único y conservador para las entradas OCR. */
public final class DocumentClassifier {
    public static final class Result {
        public final DocumentType type;
        public final int score;
        public final String reason;
        Result(DocumentType type,int score,String reason){this.type=type;this.score=score;this.reason=reason;}
        public boolean isConfident(){return type!=DocumentType.UNKNOWN;}
    }

    private static final Pattern DNI=Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])\\b");
    private static final Pattern MRZ=Pattern.compile("[A-Z0-9<]{20,}");
    private static final Pattern DATE=Pattern.compile("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b");
    private DocumentClassifier(){}

    public static Result classify(String raw){
        String u=norm(raw);
        if(u.isEmpty())return new Result(DocumentType.UNKNOWN,0,"sin texto");

        int front=frontScore(u), back=backScore(u), policy=policyScore(u), receipt=receiptScore(u);
        // La trasera del DNI tiene prioridad: nunca debe caer en el parser de pólizas.
        if(back>=12 && back>=front+2 && back>=policy)return new Result(DocumentType.DNI_BACK,back,"patrones de reverso DNI/NIE");
        if(front>=13 && front>=policy && front>=receipt)return new Result(DocumentType.DNI_FRONT,front,"patrones de anverso DNI/NIE");
        if(policy>=10 && policy>=receipt+2)return new Result(DocumentType.POLICY,policy,"contexto asegurador");
        if(receipt>=10 && receipt>=policy+2)return new Result(DocumentType.RECEIPT,receipt,"contexto de recibo");
        return new Result(DocumentType.UNKNOWN,Math.max(Math.max(front,back),Math.max(policy,receipt)),"confianza insuficiente");
    }

    private static int frontScore(String u){
        int s=0;
        if(u.contains("DOCUMENTO NACIONAL DE IDENTIDAD")||u.contains("DOCUMENTO NACIONAL"))s+=7;
        if(u.contains("APELLIDOS"))s+=5;
        if(u.contains("NOMBRE"))s+=4;
        if(u.contains("NACIONALIDAD"))s+=3;
        if(u.contains("NACIMIENTO"))s+=3;
        if(u.contains("SEXO"))s+=2;
        if(u.contains("ESPANA"))s+=2;
        if(DNI.matcher(u).find())s+=5;
        return s;
    }

    private static int backScore(String u){
        int s=0;
        if(u.contains("IDESP"))s+=10;
        if(u.contains("DOMICILIO"))s+=5;
        if(u.contains("LUGAR DE NACIMIENTO"))s+=4;
        if(u.contains("HIJO DE"))s+=3;
        if(u.contains("EQUIPO"))s+=2;
        if(MRZ.matcher(u).find() || u.contains("<<<<"))s+=8;
        return s;
    }

    private static int policyScore(String u){
        int s=0;
        if(u.contains("POLIZA")||u.contains("POLIZA DE SEGURO")||u.contains("CONDICIONES PARTICULARES"))s+=5;
        if(u.contains("TOMADOR")||u.contains("ASEGURADO"))s+=3;
        if(u.contains("FECHA EFECTO")||u.contains("EFECTO") )s+=2;
        if(u.contains("PRIMA")||u.contains("CAPITAL ASEGURADO")||u.contains("RECIBO"))s+=2;
        if(u.contains("OCASO")||u.contains("AEGON"))s+=2;
        if(DATE.matcher(u).find())s+=1;
        return s;
    }

    private static int receiptScore(String u){
        int s=0;
        if(u.contains("RECIBO"))s+=5;
        if(u.contains("IMPORTE")||u.contains("TOTAL A PAGAR")||u.contains("TOTAL"))s+=3;
        if(u.contains("VENCIMIENTO")||u.contains("FECHA DE CARGO"))s+=2;
        if(u.contains("IBAN")||u.contains("DOMICILIACION"))s+=2;
        return s;
    }

    private static String norm(String s){
        String x=s==null?"":s.toUpperCase(Locale.ROOT);
        x=Normalizer.normalize(x,Normalizer.Form.NFD).replaceAll("\\p{M}","");
        x=x.replace('0','O');
        return x.replaceAll("[^A-Z0-9< /:-]"," ").replaceAll("\\s+"," ").trim();
    }
}
