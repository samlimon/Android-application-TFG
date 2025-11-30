package com.example.minibaseapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SignWithCertActivity extends AppCompatActivity {

    private TextView tvSelectedCert;
    private TextView tvSelectedFile;
    private TextView tvStatus;
    private Button btnChooseCert;
    private Button btnSelectFile;
    private Button btnSign;

    private PqcCertificateManager certManager;
    private char[] keystorePassword;

    private List<ImportedCert> certsInStore = new ArrayList<>();
    private List<String> aliasList = new ArrayList<>();

    private String selectedAlias;
    private Uri selectedFileUri;

    private ActivityResultLauncher<Intent> selectFileLauncher;

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

        btnSign.setEnabled(false);

        setupFilePicker();
        setupSignButton();

        // Primero pedimos contraseña del almacén
        showKeystorePasswordDialog();

        btnChooseCert.setOnClickListener(v -> {
            if (aliasList.isEmpty()) {
                Toast.makeText(this,
                        "No hay certificados en el almacén. Añade uno primero.",
                        Toast.LENGTH_LONG).show();
            } else {
                showCertSelectionDialog();
            }
        });
    }
    /**
     * Lo primero es pedir la contraseña del almacen de certificados
     * */
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
    /**
     *  Cargamos todos los certificados guardados en el keystore, solo alias
     * */
    private void loadCertificatesFromStore() {
        try {
            certsInStore = certManager.listCertificates(keystorePassword);
            aliasList.clear();

            if (certsInStore.isEmpty()) {
                tvStatus.setText("No hay certificados. Añade uno en 'Gestionar certificados'.");
                // No hay alias → no tiene sentido mostrar otros mensajes
                return;
            }

            for (ImportedCert c : certsInStore) {
                aliasList.add(c.alias);
            }

            // En este punto aún no hay certificado ni documento seleccionados
            // Deja que updateStatusText() decida el mensaje adecuado
            updateStatusText();

        } catch (Exception e) {
            tvStatus.setText("Error al cargar certificados: " + e.getMessage());
        }
    }

    /**
     *  El usuario selecciona el certificado
     * */
    private void showCertSelectionDialog() {
        String[] aliasArray = aliasList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Selecciona certificado")
                .setItems(aliasArray, (dialog, which) -> {
                    selectedAlias = aliasArray[which];
                    tvSelectedCert.setText(selectedAlias);

                    // Actualizamos el texto de estado según el nuevo estado global
                    updateStatusText();
                    updateSignButtonState();
                })
                .show();
    }

    /**
     *  El usuario selecciona el documento para firmar
     * */
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

                        // Actualizamos el texto de estado según el nuevo estado global
                        updateStatusText();
                        updateSignButtonState();
                    }
                }
        );

        btnSelectFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            // Aceptamos PDF y texto plano como casos típicos
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/pdf",
                    "text/plain"
            });

            selectFileLauncher.launch(intent);
        });
    }

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
                tvStatus.setText("No se ha establecido contraseña del almacén.");
                return;
            }

            try {
                byte[] data = readFileContent(selectedFileUri);
                byte[] sig = certManager.signDataWithAlias(selectedAlias, keystorePassword, data);

                // Firma desacoplada: guardamos en fichero independiente
                String sigFileName = "signature_" + selectedAlias + ".bin";
                try (FileOutputStream fos = openFileOutput(sigFileName, MODE_PRIVATE)) {
                    fos.write(sig);
                }

                tvStatus.setText("Documento firmado correctamente.\n" +
                        "Firma generada con " + selectedAlias +
                        "\nGuardada como " + sigFileName +
                        " (" + sig.length + " bytes)");

            } catch (Exception e) {
                tvStatus.setText("Error al firmar: " + e.getMessage());
            }
        });
    }

    /**
     * Activa o desactiva el botón "Firmar" según si hay certificado y documento seleccionados.
     */
    private void updateSignButtonState() {
        btnSign.setEnabled(selectedAlias != null && selectedFileUri != null);
    }

    /**
     * Actualiza el mensaje de estado (tvStatus) en función de:
     *  - si hay certificado seleccionado,
     *  - si hay documento seleccionado.
     */
    private void updateStatusText() {
        if (aliasList.isEmpty()) {
            // Sin certificados en el almacén, ya se gestiona en loadCertificatesFromStore()
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

    /**
     * Obtiene un nombre legible (ej. archivoPrueba.pdf) a partir de una Uri.
     */
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
            } catch (Exception e) {
                // ignoramos y usamos fallback
            }
        }

        if (result == null) {
            String lastSegment = uri.getLastPathSegment();
            result = (lastSegment != null) ? lastSegment : uri.toString();
        }

        return result;
    }

    /**
     * Lee todos los bytes del fichero apuntado por la Uri.
     */
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
