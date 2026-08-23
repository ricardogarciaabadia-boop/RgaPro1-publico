package com.rgapro1.ocaso;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Lanzador seguro de la nueva interfaz RgaPro. */
public class SecureMainActivity extends MainActivityV2 {
    private SharedPreferences secureLocalPreferences;

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (!"rgapro_local".equals(name)) return super.getSharedPreferences(name, mode);
        if (secureLocalPreferences == null) {
            SharedPreferences delegate = super.getSharedPreferences(name, mode);
            secureLocalPreferences = new SecurePinPreferences(
                    delegate,
                    new SecurePinStore(getApplicationContext())
            );
        }
        return secureLocalPreferences;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { ClientAutoLinker.start(this); } catch (Exception ignored) {}
    }
}
