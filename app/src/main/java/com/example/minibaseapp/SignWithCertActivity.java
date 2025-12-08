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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

        btnSign.setEnabled(false);

        setupFilePicker();
        setupCreateSignatureFileLauncher();
        setupSignButton();

        // Primero pedimos contraseña del almacén
        showKeystorePasswordDialog();

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

    /**
     * Por seguridad, si el usuario sale de esta pantalla
     * (la Activity deja de estar visible), borramos la firma de memoria.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lastSignatureBytes != null) {
            Arrays.fill(lastSignatureBytes, (byte) 0);
            lastSignatureBytes = null;
        }
    }

    // ====================
    // 1) Contraseña almacén
    // ====================

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

            // En este punto aún no hay certificado ni documento seleccionados
            updateStatusText();

        } catch (Exception e) {
            tvStatus.setText("Error al cargar certificados: " + e.getMessage());
        }
    }

    // ====================
    // 2) Selección de certificado
    // ====================

    private void showCertSelectionDialog() {
        String[] aliasArray = aliasList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Selecciona certificado")
                .setItems(aliasArray, (dialog, which) -> {
                    selectedAlias = aliasArray[which];
                    tvSelectedCert.setText(selectedAlias);

                    // Al cambiar el certificado, borramos cualquier firma anterior en memoria
                    if (lastSignatureBytes != null) {
                        Arrays.fill(lastSignatureBytes, (byte) 0);
                        lastSignatureBytes = null;
                    }

                    updateStatusText();
                    updateSignButtonState();
                })
                .show();
    }

    // ====================
    // 3) Selección de fichero a firmar
    // ====================

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

                        // Al cambiar de fichero, invalidamos firmas anteriores en memoria
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

            // Aceptamos PDF y texto plano como casos típicos
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/pdf",
                    "text/plain"
            });

            selectFileLauncher.launch(intent);
        });
    }

    // ====================
    // 4) Lanzador para "guardar firma como..."
    // ====================

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

                                // 1) Mensaje claro de éxito
                                tvStatus.setText(
                                        "Documento firmado y firma guardada correctamente.\n" +
                                                "Puedes firmar otro documento si lo deseas."
                                );

                                // 2) Limpiamos la firma de memoria
                                Arrays.fill(lastSignatureBytes, (byte) 0);
                                lastSignatureBytes = null;

                                // 3) Reseteamos la selección de certificado y documento
                                selectedAlias = null;
                                selectedFileUri = null;

                                // Textos "estado inicial" (puedes ajustarlos a lo que tengas en el XML)
                                tvSelectedCert.setText("Ningún certificado seleccionado");
                                tvSelectedFile.setText("Ningún documento seleccionado");

                                // 4) Deshabilitamos el botón Firmar hasta nueva selección
                                updateSignButtonState();
                                // OJO: no llamamos a updateStatusText() aquí para no machacar
                                // el mensaje de éxito que acabamos de poner en tvStatus.

                            } catch (Exception e) {
                                tvStatus.setText("Error al guardar la firma: " + e.getMessage());
                            }
                        } else {
                            // Esto solo debería ocurrir si algo raro ha pasado
                            tvStatus.setText("No hay firma disponible para guardar.");
                        }
                    } else {
                        // Usuario canceló el diálogo de guardar
                        tvStatus.setText(
                                "Documento firmado, pero el archivo de firma no se ha guardado.\n" +
                                        "Puedes pulsar de nuevo 'Firmar' para elegir dónde guardarla."
                        );
                        // Aquí NO reseteamos nada: el usuario puede volver a intentar guardar
                        // con la misma firma si quiere.
                    }
                }
        );
    }

    /**
     * Abre el diálogo del sistema para que el usuario elija dónde guardar la firma.
     */
    private void launchCreateSignatureDocument() {
        if (lastSignatureBytes == null || lastSignatureBytes.length == 0) {
            tvStatus.setText("No hay firma disponible para guardar.");
            return;
        }

        // Nombre base del certificado
        String aliasPart = (selectedAlias != null ? selectedAlias : "cert");

        // Nombre del documento (por ejemplo archivoPrueba.pdf)
        String docName = tvSelectedFile.getText().toString();
        if (docName == null || docName.isEmpty()) {
            docName = "documento";
        } else {
            // Quitamos extensión si tiene
            int dotIndex = docName.lastIndexOf('.');
            if (dotIndex > 0) {
                docName = docName.substring(0, dotIndex);
            }
        }

        // Ejemplo: firma_client-11_archivoPrueba.bin
        String suggestedName = "firma_" + aliasPart + "_" + docName + ".bin";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);

        createSignatureFileLauncher.launch(intent);
    }

    // ====================
    // 5) Botón "Firmar"
    // ====================

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

                // Guardamos la firma en memoria para poder exportarla donde el usuario quiera
                if (lastSignatureBytes != null) {
                    Arrays.fill(lastSignatureBytes, (byte) 0);
                }
                lastSignatureBytes = sig;

                tvStatus.setText(
                        "Documento firmado correctamente con el certificado '" + selectedAlias + "'.\n" +
                                "Ahora elige dónde guardar el archivo de firma y si quieres cambia el nombre sugerido."
                );

                // Lanzamos el diálogo de "guardar como"
                launchCreateSignatureDocument();

            } catch (Exception e) {
                tvStatus.setText("Error al firmar: " + e.getMessage());
            }
        });
    }

    // ====================
    // 6) Estado UI (texto y botón Firmar)
    // ====================

    private void updateSignButtonState() {
        btnSign.setEnabled(selectedAlias != null && selectedFileUri != null);
    }

    /**
     * Mensajes guiando al usuario según:
     *  - si hay certificado seleccionado,
     *  - si hay documento seleccionado.
     */
    private void updateStatusText() {
        if (aliasList.isEmpty()) {
            // Sin certificados, ya se informa en loadCertificatesFromStore
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

    // ====================
    // 7) Utilidades de ficheros
    // ====================

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
