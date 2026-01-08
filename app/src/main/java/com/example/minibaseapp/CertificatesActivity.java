package com.example.minibaseapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.minibaseapp.crypto.ImportedCert;
import com.example.minibaseapp.crypto.PqcCertificateManager;
import com.example.minibaseapp.security.KeystoreAuthManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CertificatesActivity extends AppCompatActivity {

    private static final String TAG_BENCH = "BENCH";

    private TextView tvCertList;
    private PqcCertificateManager certManager;

    // Biometría / acceso al keystore
    private KeystoreAuthManager ksAuth;

    // Contraseña activa (en memoria) para operar en esta sesión
    private char[] keystorePassword;
    private char[] lastKeystorePassword;

    // Estado temporal durante el flujo de importación
    private String pendingAlias;
    private Uri pendingUserCertUri;
    private Uri pendingUserKeyUri;

    private ActivityResultLauncher<Intent> pickUserCertLauncher;
    private ActivityResultLauncher<Intent> pickUserKeyLauncher;
    private ActivityResultLauncher<Intent> pickCaCertLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificates);

        certManager = new PqcCertificateManager(this);

        tvCertList = findViewById(R.id.tvCertList);
        Button btnAddCredential = findViewById(R.id.btnAddCredential);

        // Inicializa auth biométrico
        try {
            ksAuth = new KeystoreAuthManager(this);
        } catch (Exception e) {
            // Si falla por cualquier motivo, seguimos con contraseña manual
            ksAuth = null;
            tvCertList.setText("Aviso: no se pudo inicializar biometría. Se usará contraseña.\n" + e.getMessage());
        }

        // 1) Launcher para seleccionar user_cert.pem
        pickUserCertLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        pendingUserCertUri = result.getData().getData();
                        Toast.makeText(this, "Selecciona ahora la CLAVE PRIVADA (.key)", Toast.LENGTH_LONG).show();
                        launchPickUserKey();
                    }
                });

        // 2) Launcher para seleccionar user_key
        pickUserKeyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        pendingUserKeyUri = result.getData().getData();
                        Toast.makeText(this, "Selecciona ahora el CERTIFICADO de la CA (.pem)", Toast.LENGTH_LONG).show();
                        launchPickCaCert();
                    }
                });

        // 3) Launcher para seleccionar cacert.pem
        pickCaCertLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri caCertUri = result.getData().getData();
                        importCredentialWithCa(pendingAlias, keystorePassword,
                                pendingUserCertUri, pendingUserKeyUri, caCertUri);
                    }
                });

        btnAddCredential.setOnClickListener(v -> showAddCredentialDialog());

        // Al entrar: intentamos huella si está configurada; si no, pedimos contraseña
        requestKeystoreAccessThenRefreshList();
    }

    // -----------------------
    // Acceso al keystore (huella/contraseña)
    // -----------------------

    private void requestKeystoreAccessThenRefreshList() {
        if (ksAuth != null && ksAuth.hasStoredPassword()) {
            tvCertList.setText("Autentícate con huella para ver los certificados.");
            ksAuth.requestKeystorePassword(this, getMainExecutor(), new KeystoreAuthManager.PasswordCallback() {
                @Override
                public void onPassword(char[] password) {
                    keystorePassword = password;
                    lastKeystorePassword = password;
                    refreshCertList();
                }

                @Override
                public void onCancelled() {
                    tvCertList.setText("Acceso cancelado. Introduce la contraseña para continuar.");
                    showKeystorePasswordDialog(() -> {
                        refreshCertList();
                        offerEnableBiometricsIfPossible();
                    });
                }

                @Override
                public void onError(String message) {
                    showKeystorePasswordDialog(() -> {
                        refreshCertList();
                        offerEnableBiometricsIfPossible();
                    });
                }
            });
        } else {
            tvCertList.setText("Introduce la contraseña del almacén para ver los certificados.");
            showKeystorePasswordDialog(() -> {
                refreshCertList();
                offerEnableBiometricsIfPossible();
            });
        }
    }

    /**
     * Diálogo de contraseña manual con callback "onOk".
     */
    private void showKeystorePasswordDialog(Runnable onOk) {
        EditText et = new EditText(this);
        et.setHint("Contraseña del almacén");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Contraseña del almacén")
                .setView(et)
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    String pwd = et.getText().toString();
                    if (pwd.isEmpty()) {
                        Toast.makeText(this, "La contraseña es obligatoria", Toast.LENGTH_SHORT).show();
                        showKeystorePasswordDialog(onOk);
                        return;
                    }
                    keystorePassword = pwd.toCharArray();
                    lastKeystorePassword = keystorePassword;
                    if (onOk != null) onOk.run();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    tvCertList.setText("No se puede mostrar el almacén sin contraseña.");
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Ofrece activar huella después de introducir contraseña manual (primera vez),
     * guardando la contraseña cifrada y protegida por biometría.
     */
    private void offerEnableBiometricsIfPossible() {
        if (ksAuth == null) return;
        if (keystorePassword == null) return;
        if (ksAuth.hasStoredPassword()) return; // ya activada

        new AlertDialog.Builder(this)
                .setTitle("Activar huella")
                .setMessage("¿Quieres usar tu huella para acceder al almacén a partir de ahora?\n\n" +
                        "La contraseña se guardará cifrada y protegida por biometría.")
                .setPositiveButton("Sí", (d, w) -> {
                    ksAuth.enableBiometricsForPassword(
                            this,
                            getMainExecutor(),
                            keystorePassword,
                            () -> Toast.makeText(this,
                                    "Huella activada. La próxima vez no tendrás que introducir contraseña.",
                                    Toast.LENGTH_LONG).show(),
                            new KeystoreAuthManager.PasswordCallback() {
                                @Override public void onPassword(char[] password) { }
                                @Override public void onCancelled() {
                                    Toast.makeText(CertificatesActivity.this,
                                            "Activación de huella cancelada.",
                                            Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onError(String message) {
                                    Toast.makeText(CertificatesActivity.this,
                                            "No se pudo activar huella: " + message,
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                })
                .setNegativeButton("No", null)
                .show();
    }

    // -----------------------
    // Importación de certificado (tu flujo original)
    // -----------------------

    private void showAddCredentialDialog() {
        // Si aún no tenemos contraseña (ni por huella ni manual), pedimos acceso primero
        if (keystorePassword == null) {
            requestKeystoreAccessThenRefreshList();
            Toast.makeText(this, "Primero debes abrir el almacén.", Toast.LENGTH_SHORT).show();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.modal_add_credential, null);

        EditText etAlias = dialogView.findViewById(R.id.etDialogAlias);

        new AlertDialog.Builder(this)
                .setTitle("Nuevo certificado")
                .setView(dialogView)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String alias = etAlias.getText().toString().trim();
                    if (alias.isEmpty()) {
                        Toast.makeText(this, "El alias es obligatorio", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    pendingAlias = alias;

                    Toast.makeText(this,
                            "Selecciona ahora el CERTIFICADO del usuario (.pem)",
                            Toast.LENGTH_LONG).show();
                    launchPickUserCert();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void launchPickUserCert() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickUserCertLauncher.launch(intent);
    }

    private void launchPickUserKey() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickUserKeyLauncher.launch(intent);
    }

    private void launchPickCaCert() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickCaCertLauncher.launch(intent);
    }

    private void importCredentialWithCa(String alias,
                                        char[] password,
                                        Uri userCertUri,
                                        Uri userKeyUri,
                                        Uri caCertUri) {
        if (userCertUri == null || userKeyUri == null || caCertUri == null) {
            Toast.makeText(this, "Faltan ficheros por seleccionar", Toast.LENGTH_LONG).show();
            return;
        }
        if (password == null) {
            Toast.makeText(this, "No se ha abierto el almacén", Toast.LENGTH_LONG).show();
            return;
        }

        // ---- START TIMER (solo import/parse/guardar en keystore) ----
        final long t0 = SystemClock.elapsedRealtimeNanos();

        try {
            certManager.importCredentialFromPemAndKey(userCertUri, userKeyUri, caCertUri, alias, password);

            // ---- STOP TIMER ----
            double ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0;
            Log.i(TAG_BENCH, "IMPORT_MS=" + String.format(Locale.US, "%.3f", ms)
                    + " alias=" + alias);

            Toast.makeText(this, "Certificado + CA importados correctamente", Toast.LENGTH_LONG).show();

            // Limpiamos estado temporal
            pendingAlias = null;
            pendingUserCertUri = null;
            pendingUserKeyUri = null;

            // Refrescamos la lista
            refreshCertList();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshCertList() {
        if (lastKeystorePassword == null) {
            tvCertList.setText("Añade un certificado para ver la lista.");
            return;
        }

        try {
            List<ImportedCert> certs = certManager.listCertificates(lastKeystorePassword);
            if (certs.isEmpty()) {
                tvCertList.setText("No hay certificados.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (ImportedCert c : certs) {
                sb.append("Alias: ").append(c.alias).append("\n");
                sb.append("Expira: ").append(c.notAfter).append("\n");
                sb.append("Vigente ahora: ").append(c.currentlyValid ? "Sí" : "No").append("\n");
                sb.append("-----------------------------\n");
            }
            tvCertList.setText(sb.toString());

        } catch (Exception e) {
            tvCertList.setText("Error al cargar certificados: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ksAuth != null) ksAuth.clearCachedPassword();

        if (keystorePassword != null) {
            Arrays.fill(keystorePassword, '\0');
            keystorePassword = null;
        }
        if (lastKeystorePassword != null && lastKeystorePassword != keystorePassword) {
            Arrays.fill(lastKeystorePassword, '\0');
            lastKeystorePassword = null;
        }
    }
}
