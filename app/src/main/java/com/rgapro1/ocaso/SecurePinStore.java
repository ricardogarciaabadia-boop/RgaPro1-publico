package com.rgapro1.ocaso;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Secure local storage for the application PIN using Android Keystore + AES/GCM. */
public final class SecurePinStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "RgaProPinKey";
    private static final String PREFS = "rgapro_secure_pin";
    private static final String VALUE = "encrypted_pin";
    private static final int GCM_TAG_BITS = 128;

    private final Context context;

    public SecurePinStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasPin() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(VALUE);
    }

    public void setPin(String pin) throws Exception {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN must contain exactly 6 digits");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(pin.getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(encrypted, Base64.NO_WRAP);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(VALUE, value).apply();
    }

    /** Migrates a legacy plaintext PIN once and removes the legacy value. */
    public boolean migrateLegacyPin(SharedPreferences legacyPrefs, String legacyKey) {
        if (legacyPrefs == null || legacyKey == null) return false;
        if (hasPin()) {
            legacyPrefs.edit().remove(legacyKey).apply();
            return true;
        }
        String legacyPin = legacyPrefs.getString(legacyKey, null);
        if (legacyPin == null || !legacyPin.matches("\\d{6}")) return false;
        try {
            setPin(legacyPin);
            legacyPrefs.edit().remove(legacyKey).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Package-private: used only by the compatibility preferences facade for legacy UI code. */
    String readPin() {
        try {
            String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null);
            if (value == null) return null;
            String[] parts = value.split("\\.", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] encrypted = Base64.decode(parts[1], Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean verifyPin(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) return false;
        String stored = readPin();
        return stored != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                pin.getBytes(StandardCharsets.UTF_8));
    }

    public void clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
