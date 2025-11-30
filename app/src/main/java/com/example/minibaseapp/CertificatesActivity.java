package com.example.minibaseapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
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

import java.util.List;

public class CertificatesActivity extends AppCompatActivity {

    private TextView tvCertList;
    private PqcCertificateManager certManager;

    // Estado temporal durante el flujo de importación
    private String pendingAlias;
    private char[] keystorePassword;
    private char[] pendingPassword;
    private char[] lastKeystorePassword;

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

        // 1) Launcher para seleccionar user_cert.pem
        pickUserCertLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        pendingUserCertUri = result.getData().getData();
                        Toast.makeText(this, "Selecciona ahora la CLAVE PRIVADA del usuario / servidor (.key)", Toast.LENGTH_LONG).show();
                        launchPickUserKey();
                    }
                });

        // 2) Launcher para seleccionar user_key.pem
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
                        importCredentialWithCa(pendingAlias, pendingPassword,
                                pendingUserCertUri, pendingUserKeyUri, caCertUri);
                    }
                });

        btnAddCredential.setOnClickListener(v -> showAddCredentialDialog());

        tvCertList.setText("Introduce la contraseña del almacén para ver los certificados.");
        showKeystorePasswordDialog();
    }

    private void showAddCredentialDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.modal_add_credential, null);

        EditText etAlias = dialogView.findViewById(R.id.etDialogAlias);
        // Si ya has quitado la contraseña del layout, elimina esto:
        // EditText etPassword = dialogView.findViewById(R.id.etDialogPassword);

        new AlertDialog.Builder(this)
                .setTitle("Nuevo certificado")
                .setView(dialogView)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String alias = etAlias.getText().toString().trim();
                    if (alias.isEmpty()) {
                        Toast.makeText(this,
                                "El alias es obligatorio",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (keystorePassword == null) {
                        Toast.makeText(this,
                                "Primero debes introducir la contraseña del almacén.",
                                Toast.LENGTH_LONG).show();
                        showKeystorePasswordDialog();
                        return;
                    }

                    pendingAlias = alias;
                    pendingPassword = keystorePassword;    // 🔹 usamos SIEMPRE la contraseña del almacén
                    lastKeystorePassword = keystorePassword;

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
        intent.setType("*/*"); // podrías filtrar por mime si quieres
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

        try {
            certManager.importCredentialFromPemAndKey(userCertUri, userKeyUri, caCertUri, alias, password);
            Toast.makeText(this, "Certificado + CA importados correctamente", Toast.LENGTH_LONG).show();

            // Limpiamos estado temporal
            pendingAlias = null;
            pendingPassword = null;
            pendingUserCertUri = null;
            pendingUserKeyUri = null;

            // Ahora refrescamos la lista usando la última contraseña conocida
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
                sb.append("Subject: ").append(c.subject).append("\n");
                sb.append("Issuer: ").append(c.issuer).append("\n");
                sb.append("Expira: ").append(c.notAfter).append("\n");
                sb.append("Vigente ahora: ").append(c.currentlyValid ? "Sí" : "No").append("\n");
                sb.append("-----------------------------\n");
            }
            tvCertList.setText(sb.toString());
        } catch (Exception e) {
            tvCertList.setText("Error al cargar certificados: " + e.getMessage());
        }
    }

    private void showKeystorePasswordDialog() {
        EditText et = new EditText(this);
        et.setHint("Contraseña del almacén");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Contraseña del almacén")
                .setView(et)
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    String pwd = et.getText().toString();
                    if (pwd.isEmpty()) {
                        Toast.makeText(this,
                                "La contraseña es obligatoria",
                                Toast.LENGTH_SHORT).show();
                        showKeystorePasswordDialog();
                        return;
                    }
                    keystorePassword = pwd.toCharArray();
                    lastKeystorePassword = keystorePassword;
                    refreshCertList();   // 🔹 intenta cargar lo que haya en el keystore
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    tvCertList.setText("No se puede mostrar el almacén sin contraseña.");
                })
                .setCancelable(false)
                .show();
    }
}
