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
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String ALGORITHM_NAME = "Dilithium2"; // Cambia a tu algoritmo deseado
    private Uri selectedFileUri;
    private Signature signature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectFileButton = findViewById(R.id.selectFileButton);
        Button signFileButton = findViewById(R.id.signFileButton);
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

        // Firmar archivo
        signFileButton.setOnClickListener(v -> {
            try {
                // Generar clave
                signature = new Signature(ALGORITHM_NAME);
                byte[] publicKey = signature.generate_keypair();

                // Leer el archivo seleccionado
                byte[] fileContent = readFileContent(selectedFileUri);

                // Firmar el contenido del archivo
                byte[] signatureBytes = signature.sign(fileContent);

                // Mostrar resultados
                resultText.setText("Firma generada: " + bytesToHex(signatureBytes) + "\n\nClave pública: " + bytesToHex(publicKey));

            } catch (RuntimeException e) {
                resultText.setText("Error: " + e.getMessage());
            } catch (IOException e) {
                resultText.setText("Error al leer el archivo: " + e.getMessage());
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

    // Método para convertir bytes a hex para mostrar
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}