package com.example.minibaseapp.security;

import android.content.Context;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

public class KeystoreAuthManager {

    public interface PasswordCallback {
        void onPassword(char[] password);
        void onCancelled();
        void onError(String message);
    }

    private final Context appContext;
    private final BiometricKeyStoreManager biometricMgr;

    private char[] cachedPassword; // cache en memoria (sesión)

    public KeystoreAuthManager(Context context) throws Exception {
        this.appContext = context.getApplicationContext();
        this.biometricMgr = new BiometricKeyStoreManager(appContext);
        biometricMgr.ensureKeyExists();
    }

    public boolean canUseBiometrics() {
        BiometricManager bm = BiometricManager.from(appContext);
        int res = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return res == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public boolean hasStoredPassword() {
        return biometricMgr.isPasswordStored();
    }

    public boolean hasCachedPassword() {
        return cachedPassword != null && cachedPassword.length > 0;
    }

    public void clearCachedPassword() {
        if (cachedPassword != null) {
            Arrays.fill(cachedPassword, '\0');
            cachedPassword = null;
        }
    }

    public void requestKeystorePassword(FragmentActivity activity, Executor executor, PasswordCallback cb) {
        if (hasCachedPassword()) {
            cb.onPassword(cachedPassword);
            return;
        }

        if (!hasStoredPassword()) {
            cb.onError("NO_STORED_PASSWORD");
            return;
        }

        if (!canUseBiometrics()) {
            cb.onError("BIOMETRICS_NOT_AVAILABLE");
            return;
        }

        try {
            Cipher cipher = biometricMgr.getCipher(Cipher.DECRYPT_MODE);
            authenticate(activity, executor, cipher, new BiometricResult() {
                @Override
                public void onSuccess(Cipher c) {
                    try {
                        char[] pwd = biometricMgr.decryptPassword(c);
                        cachedPassword = pwd;
                        cb.onPassword(pwd);
                    } catch (Exception e) {
                        cb.onError("Error descifrando contraseña: " + e.getMessage());
                    }
                }

                @Override
                public void onError(String msg) { cb.onError(msg); }

                @Override
                public void onCancel() { cb.onCancelled(); }
            });
        } catch (Exception e) {
            cb.onError("No se pudo preparar biometría: " + e.getMessage());
        }
    }

    public void enableBiometricsForPassword(FragmentActivity activity, Executor executor,
                                            char[] passwordToStore,
                                            Runnable onStored,
                                            PasswordCallback onFail) {
        if (!canUseBiometrics()) {
            onFail.onError("BIOMETRICS_NOT_AVAILABLE");
            return;
        }

        try {
            Cipher cipher = biometricMgr.getCipher(Cipher.ENCRYPT_MODE);
            authenticate(activity, executor, cipher, new BiometricResult() {
                @Override
                public void onSuccess(Cipher c) {
                    try {
                        biometricMgr.storePassword(passwordToStore, c);
                        cachedPassword = passwordToStore; // cache sesión
                        onStored.run();
                    } catch (Exception e) {
                        onFail.onError("No se pudo guardar con huella: " + e.getMessage());
                    }
                }

                @Override
                public void onError(String msg) { onFail.onError(msg); }

                @Override
                public void onCancel() { onFail.onCancelled(); }
            });
        } catch (Exception e) {
            onFail.onError("No se pudo preparar cifrado biométrico: " + e.getMessage());
        }
    }

    private interface BiometricResult {
        void onSuccess(Cipher cipher);
        void onError(String msg);
        void onCancel();
    }

    private void authenticate(FragmentActivity activity, Executor executor, Cipher cipher, BiometricResult result) {
        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult authResult) {
                        Cipher c = authResult.getCryptoObject() != null ? authResult.getCryptoObject().getCipher() : null;
                        if (c == null) {
                            result.onError("CryptoObject/Cipher nulo");
                            return;
                        }
                        result.onSuccess(c);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        String msg = (errString != null) ? errString.toString() : "Error biométrico";
                        result.onError(msg);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // huella incorrecta; el prompt sigue abierto
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autenticación biométrica")
                .setSubtitle("Usa tu huella para acceder al almacén de certificados")
                .setNegativeButtonText("Usar contraseña")
                .build();

        prompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }
}
