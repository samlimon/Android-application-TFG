package com.example.minibaseapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.liboqs.Sigs;
import com.example.liboqs.Signature;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String ALGORITHM_NAME = "Dilithium2"; // Cambia a tu algoritmo deseado
    private Uri selectedFileUri;
    private Signature signature;
    private byte[] privateKey;
    private byte[] publicKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectFileButton = findViewById(R.id.selectFileButton);
        Button signFileButton = findViewById(R.id.signFileButton);
        Button generateKeysButton = findViewById(R.id.generateKeysButton);
        Button verifySignatureButton = findViewById(R.id.verifySignatureButton);
        TextView resultText = findViewById(R.id.resultText);

        // Inicializa la clase Sigs
        Sigs.get_instance();

        // Verifica permisos
        checkPermissions();

        // Listar algoritmos soportados
        List<String> supportedAlgorithms = Sigs.get_supported_sigs();
        StringBuilder supportedAlgorithmsText = new StringBuilder("Algoritmos soportados:\n");
        for (String algorithm : supportedAlgorithms) {
            supportedAlgorithmsText.append(algorithm).append("\n");
        }
        resultText.setText(supportedAlgorithmsText.toString());

        // Seleccionar archivo
        ActivityResultLauncher<Intent> selectFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedFileUri = result.getData().getData();
                        signFileButton.setEnabled(true);
                    }
                });

        selectFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // Selecciona cualquier tipo de archivo
            selectFileLauncher.launch(intent);
        });

        // Generar claves
        generateKeysButton.setOnClickListener(v -> {
            try {
                // Generar clave
                signature = new Signature(ALGORITHM_NAME);
                publicKey = signature.generate_keypair();
                privateKey = signature.export_secret_key();

                // Guardar las claves en archivos locales
                saveToFile("publicKey.bin", publicKey);
                saveToFile("privateKey.bin", privateKey);

                // Mostrar resultados
                resultText.setText("Claves generadas:\n\nClave pública: " + bytesToHex(publicKey) + "\n\nClave privada: " + bytesToHex(privateKey));

            } catch (RuntimeException | IOException e) {
                resultText.setText("Error: " + e.getMessage());
            }
        });

        // Firmar archivo
        signFileButton.setOnClickListener(v -> {
            try {
                if (privateKey == null) {
                    resultText.setText("Primero genera las claves privadas.");
                    return;
                }

                // Inicializar la firma con la clave privada
                signature = new Signature(ALGORITHM_NAME, privateKey);

                // Leer el archivo seleccionado
                byte[] fileContent = readFileContent(selectedFileUri);

                // Firmar el contenido del archivo
                byte[] signatureBytes = signature.sign(fileContent);

                // Guardar la firma en un archivo
                saveToFile("signature.bin", signatureBytes);

                // Mostrar resultados
                resultText.setText("Firma generada: " + bytesToHex(signatureBytes) + "\n\nClave pública: " + bytesToHex(publicKey));

            } catch (RuntimeException | IOException e) {
                resultText.setText("Error: " + e.getMessage());
            }
        });

        // Verificar firma
        verifySignatureButton.setOnClickListener(v -> {
            try {
                // Leer el archivo seleccionado
                byte[] fileContent = readFileContent(selectedFileUri);

                // Leer la firma desde el archivo
                byte[] signatureBytes = readFromFile("signature.bin");

                // Verificar la firma
                boolean isValid = signature.verify(fileContent, signatureBytes, publicKey);

                // Mostrar resultados
                resultText.setText(isValid ? "Firma válida" : "Firma inválida");

            } catch (RuntimeException | IOException e) {
                resultText.setText("Error: " + e.getMessage());
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    @SuppressLint("NewApi")
    private byte[] readFileContent(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
            return bufferedInputStream.readAllBytes(); // Leer todos los bytes del archivo
        }
    }

    private void saveToFile(String fileName, byte[] data) throws IOException {
        try (FileOutputStream fos = openFileOutput(fileName, MODE_PRIVATE)) {
            fos.write(data);
        }
    }

    private byte[] readFromFile(String fileName) throws IOException {
        try (FileInputStream fis = openFileInput(fileName)) {
            return fis.readAllBytes();
        }
    }

    // Método para convertir bytes a hex para mostrar
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}