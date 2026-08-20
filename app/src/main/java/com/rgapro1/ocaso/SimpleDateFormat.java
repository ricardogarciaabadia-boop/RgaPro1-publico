package com.rgapro1.ocaso;

/** Compatibility wrapper used by the minimal Android build. */
public class SimpleDateFormat extends java.text.SimpleDateFormat {
    public SimpleDateFormat(String pattern, java.util.Locale locale) {
        super(pattern, locale);
    }
}
