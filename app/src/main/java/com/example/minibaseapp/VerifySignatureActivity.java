package com.example.minibaseapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.minibaseapp.crypto.PqcCertificateManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;

public class VerifySignatureActivity extends AppCompatActivity {

    private Button btnSelectCert;
    private Button btnSelectDocument;
    private Button btnSelectSignature;
    private Button btnVerify;
    private Button btnResetVerification;

    private TextView tvSelectedCert;
    private TextView tvSelectedDocument;
    private TextView tvSelectedSignature;
    private TextView tvSummary;
    private TextView tvDetails;

    private PqcCertificateManager certManager;

    private Uri selectedCertUri = null;
    private Uri selectedDocumentUri = null;
    private Uri selectedSignatureUri = null;

    private X509Certificate selectedCert = null;

    private ActivityResultLauncher<Intent> pickCertLauncher;
    private ActivityResultLauncher<Intent> pickDocumentLauncher;
    private ActivityResultLauncher<Intent> pickSignatureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_signature);

        certManager = new PqcCertificateManager(getApplicationContext());

        initViews();
        setupLaunchers();
        setupButtons();
        updateVerifyButtonState();
    }

    private void initViews() {
        btnSelectCert = findViewById(R.id.btnSelectCert);
        btnSelectDocument = findViewById(R.id.btnSelectDocument);
        btnSelectSignature = findViewById(R.id.btnSelectSignature);
        btnVerify = findViewById(R.id.btnVerify);
        btnResetVerification = findViewById(R.id.btnResetVerification);
        tvSelectedCert = findViewById(R.id.tvSelectedCert);
        tvSelectedDocument = findViewById(R.id.tvSelectedDocument);
        tvSelectedSignature = findViewById(R.id.tvSelectedSignature);
        tvSummary = findViewById(R.id.tvSummary);
        tvDetails = findViewById(R.id.tvDetails);
    }

    private void setupLaunchers() {
        // Certificado del firmante
        pickCertLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedCertUri = uri;
                            tvSelectedCert.setText(getDisplayNameFromUri(uri));

                            // Intentamos cargar el X509Certificate
                            try {
                                selectedCert = certManager.loadCertificateFromUri(
                                        getApplicationContext(),
                                        uri
                                );
                                //tvSummary.setText("Certificado cargado. Ahora selecciona documento y fichero de firma.");
                            } catch (Exception e) {
                                selectedCert = null;
                                tvSummary.setText("Error al leer el certificado: " + e.getMessage());
                            }

                            updateVerifyButtonState();
                        }
                    }
                }
        );

        // Documento
        pickDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedDocumentUri = uri;
                            tvSelectedDocument.setText(getDisplayNameFromUri(uri));
                            updateVerifyButtonState();
                        }
                    }
                }
        );

        // Fichero de firma (.bin)
        pickSignatureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedSignatureUri = uri;
                            tvSelectedSignature.setText(getDisplayNameFromUri(uri));
                            updateVerifyButtonState();
                        }
                    }
                }
        );
    }

    private void setupButtons() {
        btnSelectCert.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            pickCertLauncher.launch(intent);
        });

        btnSelectDocument.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // puedes restringir a "application/pdf" más adelante
            pickDocumentLauncher.launch(intent);
        });

        btnSelectSignature.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // o "application/octet-stream"
            pickSignatureLauncher.launch(intent);
        });

        btnVerify.setOnClickListener(v -> performVerification());

        btnResetVerification.setOnClickListener(v -> {
            resetVerificationState();
        });
    }

    private void updateVerifyButtonState() {
        boolean enabled = (selectedCert != null
                && selectedDocumentUri != null
                && selectedSignatureUri != null);
        btnVerify.setEnabled(enabled);

        // Cada vez que cambia algo relevante, actualizamos el texto de estado
        updateStatusText();
    }

    private void performVerification() {
        if (selectedCert == null || selectedDocumentUri == null || selectedSignatureUri == null) {
            tvSummary.setText("Faltan datos para realizar la verificación.");
            return;
        }

        tvSummary.setText("Verificando firma...");
        tvDetails.setText("");

        try {
            byte[] docBytes = readAllBytesFromUri(selectedDocumentUri);
            byte[] sigBytes = readAllBytesFromUri(selectedSignatureUri);

            // 1) Verificación criptográfica
            boolean signatureOk = certManager.verifyDataWithCertificate(
                    selectedCert,
                    docBytes,
                    sigBytes
            );

            if (!signatureOk) {
                tvSummary.setText("❌ La firma NO es válida para este documento y certificado.");
                tvDetails.setText("Se ha realizado la verificación criptográfica y la firma NO coincide con el contenido del documento.");
                btnResetVerification.setVisibility(View.VISIBLE);
                return;
            }

            // 2) Validación básica del certificado (sin CA de momento)
            PqcCertificateManager.CertValidationResult cv =
                    certManager.validateCertificate(selectedCert, null);

            // Resumen amigable
            StringBuilder summary = new StringBuilder();
            summary.append("✅ La firma es criptográficamente VÁLIDA.\n");

            if (!cv.timeValid) {
                summary.append("⚠ El certificado NO está vigente (caducado o aún no válido).\n");
            } else {
                summary.append("✔ El certificado está vigente en este momento.\n");
            }

            if (!cv.isEndEntity) {
                summary.append("⚠ El certificado se identifica como CA, no como certificado de usuario.\n");
            }

            if (!cv.keyUsageOk) {
                summary.append("⚠ El uso de clave no es el típico para firma digital (KeyUsage.digitalSignature = false).\n");
            }

            tvSummary.setText(summary.toString());

            // Detalles técnicos
            StringBuilder details = new StringBuilder();
            details.append("Detalles técnicos de la validación del certificado:\n\n");
            details.append(cv.diagnostics);

            tvDetails.setText(details.toString());
            btnResetVerification.setVisibility(View.VISIBLE);

        } catch (IOException e) {
            tvSummary.setText("Error leyendo documento o firma: " + e.getMessage());
        } catch (Exception e) {
            tvSummary.setText("Error durante la verificación: " + e.getMessage());
        }
    }

    private byte[] readAllBytesFromUri(Uri uri) throws IOException {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedInputStream bis = new BufferedInputStream(is)) {
            if (is == null) {
                throw new IOException("No se pudo abrir InputStream para el Uri.");
            }
            return bis.readAllBytes();
        }
    }

    private String getDisplayNameFromUri(Uri uri) {
        String result = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            // opcional: loggear si quieres
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Fallbacks por si algo salió mal
        if (result == null || result.isEmpty()) {
            result = uri.getLastPathSegment();
        }
        if (result == null || result.isEmpty()) {
            result = uri.toString();
        }
        return result;
    }

    private void updateStatusText() {
        // Si ya has mostrado un resultado de verificación, normalmente
        // no llamaremos a esto hasta que el usuario pulse "Nueva verificación".
        if (selectedCert == null && selectedDocumentUri == null && selectedSignatureUri == null) {
            tvSummary.setText("Selecciona certificado, documento y fichero de firma para empezar.");
            return;
        }

        if (selectedCert == null) {
            tvSummary.setText("Selecciona primero el certificado del firmante.");
            return;
        }

        // A partir de aquí, ya hay certificado seleccionado
        if (selectedDocumentUri == null && selectedSignatureUri == null) {
            tvSummary.setText("Certificado cargado. Ahora selecciona el documento y el fichero de firma.");
            return;
        }

        if (selectedDocumentUri == null) {
            tvSummary.setText("Certificado cargado. Falta seleccionar el documento.");
            return;
        }

        if (selectedSignatureUri == null) {
            tvSummary.setText("Certificado y documento cargados. Falta seleccionar el fichero de firma.");
            return;
        }

        // Si llegamos aquí, los 3 están seleccionados
        tvSummary.setText("Archivos cargados correctamente. Puede proceder a verificar la firma.");
    }

    private void resetVerificationState() {
        // Limpiamos estado interno
        selectedCertUri = null;
        selectedDocumentUri = null;
        selectedSignatureUri = null;
        selectedCert = null;

        // Limpiamos los textos de selección
        tvSelectedCert.setText("Ningún certificado seleccionado");
        tvSelectedDocument.setText("Ningún documento seleccionado");
        tvSelectedSignature.setText("Ningún fichero de firma seleccionado");

        // Limpiamos resultados
        tvDetails.setText("");
        // Reset del mensaje principal al estado inicial
        tvSummary.setText("Selecciona certificado, documento y fichero de firma para empezar.");

        // Deshabilitar botón de verificar y actualizar estado
        updateVerifyButtonState();

        // Ocultar botón de “Nueva verificación” hasta la próxima vez
        btnResetVerification.setVisibility(View.GONE);
    }
}
