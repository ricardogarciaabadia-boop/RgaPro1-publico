package com.rgapro1.ocaso;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the local customer cartera with an app-specific Android Keystore key. */
public final class SecureLocalDataStore {
    private static final String KEYSTORE="AndroidKeyStore";
    private static final String ALIAS="RgaProLocalDataKey";
    private static final String PREFIX="enc:v1:";
    private final Context context;

    public SecureLocalDataStore(Context context){this.context=context.getApplicationContext();}

    public String encode(String plain){
        if(plain==null)return null;
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey());
            byte[] encrypted=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX+Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+
                    Base64.encodeToString(encrypted,Base64.NO_WRAP);
        }catch(Exception e){throw new IllegalStateException("No se pudo cifrar la cartera local",e);}
    }

    public String decode(String stored){
        if(stored==null||!stored.startsWith(PREFIX))return stored;
        try{
            String[] parts=stored.substring(PREFIX.length()).split("\\.",2);
            if(parts.length!=2)throw new IllegalArgumentException("Formato cifrado inválido");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,getExistingKey(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("No se pudo descifrar la cartera local",e);}
    }

    /** Converts old plaintext JSON once; the original remains if encryption fails. */
    public void migrate(SharedPreferences prefs,String key){
        String stored=prefs.getString(key,null);
        if(stored==null||stored.isEmpty()||stored.startsWith(PREFIX))return;
        String encrypted=encode(stored);
        prefs.edit().putString(key,encrypted).commit();
    }

    public static String read(Context context,SharedPreferences prefs,String key,String fallback){
        String value=prefs.getString(key,fallback);
        return new SecureLocalDataStore(context).decode(value);
    }

    private SecretKey getOrCreateKey()throws Exception{
        KeyStore ks=KeyStore.getInstance(KEYSTORE);ks.load(null);
        if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private SecretKey getExistingKey()throws Exception{
        KeyStore ks=KeyStore.getInstance(KEYSTORE);ks.load(null);
        if(!ks.containsAlias(ALIAS))throw new IllegalStateException("Falta la clave de cifrado de la cartera");
        return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
    }
}
