package com.rgapro1.ocaso;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility facade for the legacy MainActivity. The existing UI can keep using
 * SharedPreferences while the PIN itself is transparently stored in Android Keystore.
 */
public final class SecurePinPreferences implements SharedPreferences {
    private static final String PIN = "pin";
    private final SharedPreferences delegate;
    private final SecurePinStore secure;

    public SecurePinPreferences(SharedPreferences delegate, SecurePinStore secure) {
        this.delegate = delegate;
        this.secure = secure;
        secure.migrateLegacyPin(delegate, PIN);
    }

    @Override public Map<String, ?> getAll() {
        Map<String, ?> values = new HashMap<>(delegate.getAll());
        values.remove(PIN);
        return values;
    }

    @Override public String getString(String key, String defValue) {
        if (PIN.equals(key)) {
            String pin = secure.readPin();
            return pin == null ? defValue : pin;
        }
        return delegate.getString(key, defValue);
    }

    @Override public Set<String> getStringSet(String key, Set<String> defValues) { return delegate.getStringSet(key, defValues); }
    @Override public int getInt(String key, int defValue) { return delegate.getInt(key, defValue); }
    @Override public long getLong(String key, long defValue) { return delegate.getLong(key, defValue); }
    @Override public float getFloat(String key, float defValue) { return delegate.getFloat(key, defValue); }
    @Override public boolean getBoolean(String key, boolean defValue) { return delegate.getBoolean(key, defValue); }
    @Override public boolean contains(String key) { return PIN.equals(key) ? secure.hasPin() : delegate.contains(key); }

    @Override public Editor edit() { return new SecureEditor(delegate.edit()); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { delegate.registerOnSharedPreferenceChangeListener(listener); }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { delegate.unregisterOnSharedPreferenceChangeListener(listener); }

    private final class SecureEditor implements Editor {
        private final Editor editor;
        private String pendingPin;
        private boolean removePin;
        private boolean clearAll;

        SecureEditor(Editor editor) { this.editor = editor; }

        @Override public Editor putString(String key, String value) {
            if (PIN.equals(key)) pendingPin = value;
            else editor.putString(key, value);
            return this;
        }
        @Override public Editor putStringSet(String key, Set<String> values) { editor.putStringSet(key, values); return this; }
        @Override public Editor putInt(String key, int value) { editor.putInt(key, value); return this; }
        @Override public Editor putLong(String key, long value) { editor.putLong(key, value); return this; }
        @Override public Editor putFloat(String key, float value) { editor.putFloat(key, value); return this; }
        @Override public Editor putBoolean(String key, boolean value) { editor.putBoolean(key, value); return this; }
        @Override public Editor remove(String key) { if (PIN.equals(key)) removePin = true; else editor.remove(key); return this; }
        @Override public Editor clear() { clearAll = true; editor.clear(); return this; }

        private boolean persist() {
            try {
                if (clearAll || removePin) secure.clear();
                if (pendingPin != null) secure.setPin(pendingPin);
                return editor.commit();
            } catch (Exception e) {
                return false;
            }
        }

        @Override public boolean commit() { return persist(); }
        @Override public void apply() { persist(); }
    }
}
