package com.example.minibaseapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
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
import java.util.Locale;

public class VerifySignatureActivity extends AppCompatActivity {

    private static final String TAG_BENCH = "BENCH";

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

        // Estado inicial consistente
        btnResetVerification.setVisibility(View.GONE);
        tvDetails.setText("");
        tvSummary.setText("Selecciona certificado, documento y fichero de firma para empezar.");
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

                            try {
                                selectedCert = certManager.loadCertificateFromUri(
                                        getApplicationContext(),
                                        uri
                                );
                            } catch (Exception e) {
                                selectedCert = null;
                                tvSummary.setText("Error al leer el certificado: " + e.getMessage());
                            }

                            // Si cambias selección, ocultamos botón de reset hasta que haya verificación real
                            btnResetVerification.setVisibility(View.GONE);
                            tvDetails.setText("");

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

                            btnResetVerification.setVisibility(View.GONE);
                            tvDetails.setText("");

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

                            btnResetVerification.setVisibility(View.GONE);
                            tvDetails.setText("");

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
            intent.setType("*/*"); // más adelante podrías poner application/pdf si quieres
            pickDocumentLauncher.launch(intent);
        });

        btnSelectSignature.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // o application/octet-stream
            pickSignatureLauncher.launch(intent);
        });

        btnVerify.setOnClickListener(v -> performVerification());

        btnResetVerification.setOnClickListener(v -> resetVerificationState());
    }

    private void updateVerifyButtonState() {
        boolean enabled = (selectedCert != null
                && selectedDocumentUri != null
                && selectedSignatureUri != null);
        btnVerify.setEnabled(enabled);

        // Actualizamos mensaje guía si aún no se ha verificado (o si estamos en selección)
        updateStatusText();
    }

    private void performVerification() {
        // Medición E2E: desde click hasta resultado final en pantalla
        final long t0 = SystemClock.elapsedRealtimeNanos();

        if (selectedCert == null || selectedDocumentUri == null || selectedSignatureUri == null) {
            tvSummary.setText("Faltan datos para realizar la verificación.");
            logBenchVerify("unknown", false, msSince(t0));
            return;
        }

        tvSummary.setText("Verificando firma...");
        tvDetails.setText("");

        boolean success = false;
        String alg = "unknown";

        try {
            // 1) Leemos documento y firma
            byte[] docBytes = readAllBytesFromUri(selectedDocumentUri);
            byte[] sigBytes = readAllBytesFromUri(selectedSignatureUri);

            // 2) Verificación criptográfica
            alg = safeAlgFromCert(selectedCert);
            boolean signatureOk = certManager.verifyDataWithCertificate(
                    selectedCert,
                    docBytes,
                    sigBytes
            );

            if (!signatureOk) {
                tvSummary.setText("❌ La firma NO es válida.");
                tvDetails.setText("La firma no coincide con el contenido del documento o el certificado proporcionado.");
                btnResetVerification.setVisibility(View.VISIBLE);

                logBenchVerify(alg, false, msSince(t0));
                return;
            }

            // 3) Validaciones básicas del certificado (sin CA)
            PqcCertificateManager.CertValidationResult cv =
                    certManager.validateCertificate(selectedCert, null);

            // Construimos resumen final (sin CA)
            StringBuilder summary = new StringBuilder();
            summary.append("✅ La firma es VÁLIDA.\n");

            // Vigencia temporal
            if (!cv.timeValid) {
                summary.append("❌ Certificado NO vigente (caducado o aún no válido).\n");
            } else {
                summary.append("✔ Certificado vigente.\n");
            }

            // End-entity
            if (!cv.isEndEntity) {
                summary.append("❌ Certificado no apto: es un certificado de CA.\n");
            }

            // KeyUsage estricto (si falta o es false, cv.keyUsageOk será false con tu cambio)
            if (!cv.keyUsageOk) {
                summary.append("❌ Certificado no apto para firma electrónica.\n");
                tvSummary.setText(summary.toString());

                // Detalle técnico
                tvDetails.setText("Detalles técnicos:\n\n" + cv.diagnostics);
                btnResetVerification.setVisibility(View.VISIBLE);

                logBenchVerify(alg, false, msSince(t0));
                return;
            }

            // Si pasa requisitos mínimos, ok final
            tvSummary.setText(summary.toString());

            // Detalles técnicos (útiles para tribunal; si quieres, puedes acortarlo)
            tvDetails.setText("Detalles técnicos:\n\n" + cv.diagnostics);

            btnResetVerification.setVisibility(View.VISIBLE);
            success = true;

            logBenchVerify(alg, true, msSince(t0));

        } catch (IOException e) {
            tvSummary.setText("Error leyendo documento o firma: " + e.getMessage());
            logBenchVerify(alg, false, msSince(t0));
        } catch (Exception e) {
            tvSummary.setText("Error durante la verificación: " + e.getMessage());
            logBenchVerify(alg, false, msSince(t0));
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
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }

        if (result == null || result.isEmpty()) {
            result = uri.getLastPathSegment();
        }
        if (result == null || result.isEmpty()) {
            result = uri.toString();
        }
        return result;
    }

    private void updateStatusText() {
        // Si no hay nada seleccionado
        if (selectedCert == null && selectedDocumentUri == null && selectedSignatureUri == null) {
            tvSummary.setText("Selecciona certificado, documento y fichero de firma para empezar.");
            return;
        }

        if (selectedCert == null) {
            tvSummary.setText("Selecciona primero el certificado del firmante.");
            return;
        }

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

        tvSummary.setText("Archivos cargados correctamente. Puede proceder a verificar la firma.");
    }

    private void resetVerificationState() {
        selectedCertUri = null;
        selectedDocumentUri = null;
        selectedSignatureUri = null;
        selectedCert = null;

        tvSelectedCert.setText("Ningún certificado seleccionado");
        tvSelectedDocument.setText("Ningún documento seleccionado");
        tvSelectedSignature.setText("Ningún fichero de firma seleccionado");

        tvDetails.setText("");
        tvSummary.setText("Selecciona certificado, documento y fichero de firma para empezar.");

        updateVerifyButtonState();

        btnResetVerification.setVisibility(View.GONE);
    }

    // -------------------------------
    // Helpers para mediciones de rendimiento
    // -------------------------------
    private static double msSince(long startNanos) {
        return (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0;
    }

    private static String safeAlgFromCert(X509Certificate cert) {
        try {
            if (cert != null && cert.getPublicKey() != null) {
                String a = cert.getPublicKey().getAlgorithm();
                if (a != null && !a.isEmpty()) return a;
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    /**
     * Log para medición de tiempos
     */
    private void logBenchVerify(String alg, boolean ok, double ms) {
        android.util.Log.i("BENCH", "VERIFY_MS=" +
                String.format(java.util.Locale.US, "%.3f", ms) +
                " alg=" + alg +
                " ok=" + ok);
    }
}
