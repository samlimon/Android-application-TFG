package com.example.minibaseapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SignWithCertActivity extends AppCompatActivity {

    private static final String TAG_BENCH = "BENCH";

    private TextView tvSelectedCert;
    private TextView tvSelectedFile;
    private TextView tvStatus;
    private Button btnChooseCert;
    private Button btnSelectFile;
    private Button btnSign;

    private PqcCertificateManager certManager;

    // acceso por huella/contraseña
    private KeystoreAuthManager ksAuth;
    private char[] keystorePassword;

    private List<ImportedCert> certsInStore = new ArrayList<>();
    private List<String> aliasList = new ArrayList<>();

    private String selectedAlias;
    private Uri selectedFileUri;

    private ActivityResultLauncher<Intent> selectFileLauncher;
    private ActivityResultLauncher<Intent> createSignatureFileLauncher;

    // Firma generada en memoria (se limpia al salir de la Activity)
    private byte[] lastSignatureBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_with_cert);

        tvSelectedCert = findViewById(R.id.tvSelectedCert);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvStatus = findViewById(R.id.tvStatus);
        btnChooseCert = findViewById(R.id.btnChooseCert);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnSign = findViewById(R.id.btnSign);

        certManager = new PqcCertificateManager(this);

        try {
            ksAuth = new KeystoreAuthManager(this);
        } catch (Exception e) {
            ksAuth = null;
            Toast.makeText(this, "Aviso: biometría no disponible. Se usará contraseña.", Toast.LENGTH_LONG).show();
        }

        btnSign.setEnabled(false);

        setupFilePicker();
        setupCreateSignatureFileLauncher();
        setupSignButton();

        // Antes pedías contraseña siempre: ahora intentamos huella si está configurada
        openKeystoreAndLoadCertificates();

        btnChooseCert.setOnClickListener(v -> {
            if (aliasList.isEmpty()) {
                Toast.makeText(this,
                        "No hay certificados en el almacén. Añade uno primero en 'Gestionar certificados'.",
                        Toast.LENGTH_LONG).show();
            } else {
                showCertSelectionDialog();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (ksAuth != null) ksAuth.clearCachedPassword();

        if (keystorePassword != null) {
            Arrays.fill(keystorePassword, '\0');
            keystorePassword = null;
        }

        if (lastSignatureBytes != null) {
            Arrays.fill(lastSignatureBytes, (byte) 0);
            lastSignatureBytes = null;
        }
    }

    // Abrir almacén: huella o contraseña
    private void openKeystoreAndLoadCertificates() {
        if (ksAuth != null && ksAuth.hasStoredPassword()) {
            tvStatus.setText("Autentícate con huella para abrir el almacén de certificados.");
            ksAuth.requestKeystorePassword(this, getMainExecutor(), new KeystoreAuthManager.PasswordCallback() {
                @Override
                public void onPassword(char[] password) {
                    keystorePassword = password;
                    loadCertificatesFromStore();
                }

                @Override
                public void onCancelled() {
                    showKeystorePasswordDialog();
                }

                @Override
                public void onError(String message) {
                    showKeystorePasswordDialog();
                }
            });
        } else {
            showKeystorePasswordDialog();
        }
    }

    // Contraseña del almacén
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
                    loadCertificatesFromStore();

                    // Ofrecer activar huella si aún no está configurada
                    offerEnableBiometricsIfPossible();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    Toast.makeText(this,
                            "No se puede firmar sin abrir el almacén",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void offerEnableBiometricsIfPossible() {
        if (ksAuth == null) return;
        if (keystorePassword == null) return;
        if (ksAuth.hasStoredPassword()) return; // ya activado

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
                                @Override public void onPassword(char[] p) {}
                                @Override public void onCancelled() {
                                    Toast.makeText(SignWithCertActivity.this,
                                            "Activación de huella cancelada.",
                                            Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onError(String msg) {
                                    Toast.makeText(SignWithCertActivity.this,
                                            "No se pudo activar huella: " + msg,
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void loadCertificatesFromStore() {
        try {
            certsInStore = certManager.listCertificates(keystorePassword);
            aliasList.clear();

            if (certsInStore.isEmpty()) {
                tvStatus.setText("No hay certificados. Añade uno en 'Gestionar certificados'.");
                return;
            }

            for (ImportedCert c : certsInStore) {
                aliasList.add(c.alias);
            }

            updateStatusText();

        } catch (Exception e) {
            tvStatus.setText("Error al cargar certificados: " + e.getMessage());
        }
    }

    // Selección del certificado con el que firmar
    private void showCertSelectionDialog() {
        String[] aliasArray = aliasList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Selecciona certificado")
                .setItems(aliasArray, (dialog, which) -> {
                    selectedAlias = aliasArray[which];
                    tvSelectedCert.setText(selectedAlias);

                    if (lastSignatureBytes != null) {
                        Arrays.fill(lastSignatureBytes, (byte) 0);
                        lastSignatureBytes = null;
                    }

                    updateStatusText();
                    updateSignButtonState();
                })
                .show();
    }

    // Selección del fichero a firmar
    private void setupFilePicker() {
        selectFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedFileUri = result.getData().getData();
                        if (selectedFileUri != null) {
                            String displayName = getDisplayNameFromUri(selectedFileUri);
                            tvSelectedFile.setText(displayName);
                        }

                        if (lastSignatureBytes != null) {
                            Arrays.fill(lastSignatureBytes, (byte) 0);
                            lastSignatureBytes = null;
                        }

                        updateStatusText();
                        updateSignButtonState();
                    }
                }
        );

        btnSelectFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/pdf",
                    "text/plain"
            });

            selectFileLauncher.launch(intent);
        });
    }

    // Guardar firma como...
    private void setupCreateSignatureFileLauncher() {
        createSignatureFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null && lastSignatureBytes != null) {
                            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                                if (os == null) {
                                    tvStatus.setText("No se pudo abrir el destino para guardar la firma.");
                                    return;
                                }
                                os.write(lastSignatureBytes);
                                os.flush();

                                tvStatus.setText(
                                        "Documento firmado y firma guardada correctamente.\n" +
                                                "Puedes firmar otro documento si lo deseas."
                                );

                                Arrays.fill(lastSignatureBytes, (byte) 0);
                                lastSignatureBytes = null;

                                selectedAlias = null;
                                selectedFileUri = null;

                                tvSelectedCert.setText("Ningún certificado seleccionado");
                                tvSelectedFile.setText("Ningún documento seleccionado");

                                updateSignButtonState();

                            } catch (Exception e) {
                                tvStatus.setText("Error al guardar la firma: " + e.getMessage());
                            }
                        } else {
                            tvStatus.setText("No hay firma disponible para guardar.");
                        }
                    } else {
                        tvStatus.setText(
                                "Documento firmado, pero el archivo de firma no se ha guardado.\n" +
                                        "Puedes pulsar de nuevo 'Firmar' para elegir dónde guardarla."
                        );
                    }
                }
        );
    }

    private void launchCreateSignatureDocument() {
        if (lastSignatureBytes == null || lastSignatureBytes.length == 0) {
            tvStatus.setText("No hay firma disponible para guardar.");
            return;
        }

        String aliasPart = (selectedAlias != null ? selectedAlias : "cert");

        String docName = tvSelectedFile.getText().toString();
        if (docName == null || docName.isEmpty()) {
            docName = "documento";
        } else {
            int dotIndex = docName.lastIndexOf('.');
            if (dotIndex > 0) {
                docName = docName.substring(0, dotIndex);
            }
        }

        String suggestedName = "firma_" + aliasPart + "_" + docName + ".bin";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);

        createSignatureFileLauncher.launch(intent);
    }

    // Firmado del documento
    private void setupSignButton() {
        btnSign.setOnClickListener(v -> {
            if (selectedAlias == null) {
                tvStatus.setText("Selecciona un certificado.");
                return;
            }
            if (selectedFileUri == null) {
                tvStatus.setText("Selecciona un fichero a firmar.");
                return;
            }
            if (keystorePassword == null) {
                tvStatus.setText("No se ha abierto el almacén (contraseña/huella).");
                return;
            }

            // Medicion del firmado
            final long t0 = SystemClock.elapsedRealtimeNanos();

            try {
                byte[] data = readFileContent(selectedFileUri);
                byte[] sig = certManager.signDataWithAlias(selectedAlias, keystorePassword, data);

                //STOP TIMER
                double ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0;

                // Log
                Log.i(TAG_BENCH, "SIGN_MS=" + String.format(Locale.US, "%.3f", ms)
                        + " alias=" + selectedAlias);

                if (lastSignatureBytes != null) {
                    Arrays.fill(lastSignatureBytes, (byte) 0);
                }
                lastSignatureBytes = sig;

                tvStatus.setText(
                        "Documento firmado correctamente con el certificado '" + selectedAlias + "'.\n" +
                                "Ahora elige dónde guardar el archivo de firma y si quieres cambia el nombre sugerido."
                );

                // SAF (no entra en el benchmark)
                launchCreateSignatureDocument();

            } catch (Exception e) {
                // Si quieres que el benchmark también salga en errores, podrías loguearlo aquí con ok=false
                tvStatus.setText("Error al firmar: " + e.getMessage());
            }
        });
    }

    // Estado UI
    private void updateSignButtonState() {
        btnSign.setEnabled(selectedAlias != null && selectedFileUri != null);
    }

    private void updateStatusText() {
        if (aliasList.isEmpty()) {
            return;
        }

        if (selectedAlias == null && selectedFileUri == null) {
            tvStatus.setText("Certificados cargados. Pulsa en 'Seleccionar certificado'.");
        } else if (selectedAlias != null && selectedFileUri == null) {
            tvStatus.setText("Certificado seleccionado. Ahora selecciona un documento a firmar.");
        } else if (selectedAlias == null && selectedFileUri != null) {
            tvStatus.setText("Documento seleccionado. Ahora selecciona un certificado.");
        } else {
            tvStatus.setText("Certificado y documento seleccionados. Pulsa en 'Firmar'.");
        }
    }

    // Utilidades de ficheros
    private String getDisplayNameFromUri(Uri uri) {
        String result = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (result == null) {
            String lastSegment = uri.getLastPathSegment();
            result = (lastSegment != null) ? lastSegment : uri.toString();
        }

        return result;
    }

    private byte[] readFileContent(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedInputStream bin = new BufferedInputStream(in);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            if (in == null) {
                throw new RuntimeException("No se pudo abrir InputStream para el fichero");
            }

            byte[] tmp = new byte[4096];
            int n;
            while ((n = bin.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al leer fichero a firmar: " + e.getMessage(), e);
        }
    }
}
