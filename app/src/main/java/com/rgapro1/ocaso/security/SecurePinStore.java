package com.rgapro1.ocaso.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the PIN encrypted with an Android Keystore AES-256 key. */
public final class SecurePinStore {
    private static final String PREF = "rgapro_security";
    private static final String VALUE = "pin_ciphertext";
    private static final String IV = "pin_iv";
    private static final String ALIAS = "rgapro_pin_aes";
    private static final String KS = "AndroidKeyStore";
    private final Context context;
    private final SharedPreferences prefs;

    public SecurePinStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void setPin(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) throw new IllegalArgumentException("PIN must contain 6 digits");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(pin.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(VALUE, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to protect PIN", e);
        }
    }

    public boolean verify(String pin) {
        if (pin == null) return false;
        try {
            String ciphertext64 = prefs.getString(VALUE, null);
            String iv64 = prefs.getString(IV, null);
            if (ciphertext64 == null || iv64 == null) return false;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv64, Base64.NO_WRAP)));
            byte[] expected = cipher.doFinal(Base64.decode(ciphertext64, Base64.NO_WRAP));
            return MessageDigest.isEqual(expected, pin.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isConfigured() {
        return prefs.contains(VALUE) && prefs.contains(IV);
    }

    public void clear() {
        prefs.edit().remove(VALUE).remove(IV).apply();
        try {
            KeyStore keyStore = KeyStore.getInstance(KS);
            keyStore.load(null);
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KS);
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
