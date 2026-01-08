package com.example.minibaseapp.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BiometricKeyStoreManager {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "PQC_KEYSTORE_PASSWORD_KEY";

    private static final String PREFS_NAME = "biometric_keystore_prefs";
    private static final String PREF_ENCRYPTED_PWD = "encrypted_pwd";
    private static final String PREF_IV = "pwd_iv";

    private final Context context;

    public BiometricKeyStoreManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Crea la clave AES protegida por biometría si no existe */
    public void ensureKeyExists() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator =
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                    .build();

            keyGenerator.init(spec);
            keyGenerator.generateKey();
        }
    }

    public boolean isPasswordStored() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains(PREF_ENCRYPTED_PWD) && prefs.contains(PREF_IV);
    }

    /** Devuelve Cipher listo para ENCRYPT o DECRYPT */
    public Cipher getCipher(int mode) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, key);
        } else {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String ivB64 = prefs.getString(PREF_IV, null);
            if (ivB64 == null) {
                throw new IllegalStateException("No hay IV guardado para descifrar");
            }
            byte[] iv = Base64.decode(ivB64, Base64.DEFAULT);
            cipher.init(mode, key, new GCMParameterSpec(128, iv));
        }
        return cipher;
    }

    /** Guarda contraseña cifrada (se llama tras biometría OK en modo ENCRYPT) */
    public void storePassword(char[] password, Cipher cipher) throws Exception {
        byte[] pwdBytes = new String(password).getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = cipher.doFinal(pwdBytes);
        byte[] iv = cipher.getIV();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_ENCRYPTED_PWD, Base64.encodeToString(encrypted, Base64.DEFAULT))
                .putString(PREF_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                .apply();
    }

    /** Descifra contraseña (se llama tras biometría OK en modo DECRYPT) */
    public char[] decryptPassword(Cipher cipher) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encB64 = prefs.getString(PREF_ENCRYPTED_PWD, null);
        if (encB64 == null) {
            throw new IllegalStateException("No hay contraseña guardada");
        }
        byte[] enc = Base64.decode(encB64, Base64.DEFAULT);

        byte[] decrypted = cipher.doFinal(enc);
        return new String(decrypted, StandardCharsets.UTF_8).toCharArray();
    }
}
