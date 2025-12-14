package com.example.minibaseapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class CertificatesMenuActivity extends AppCompatActivity {

    private Button btnSignWithCert;
    private Button btnVerifySignature;
    private Button btnManageCerts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificates_menu);

        btnSignWithCert = findViewById(R.id.btnSignWithCert);
        btnVerifySignature = findViewById(R.id.btnVerifySignature);
        btnManageCerts = findViewById(R.id.btnManageCerts);

        // Firmar documento con certificado
        btnSignWithCert.setOnClickListener(v -> {
            Intent i = new Intent(
                    CertificatesMenuActivity.this,
                    SignWithCertActivity.class
            );
            startActivity(i);
        });

        // Verificar firma (nueva Activity que implementaremos luego)
        btnVerifySignature.setOnClickListener(v -> {
            Intent i = new Intent(
                    CertificatesMenuActivity.this,
                    VerifySignatureActivity.class
            );
            startActivity(i);
        });

        // Gestionar certificados (tu CertificatesActivity actual)
        btnManageCerts.setOnClickListener(v -> {
            Intent i = new Intent(
                    CertificatesMenuActivity.this,
                    CertificatesActivity.class
            );
            startActivity(i);
        });
    }
}
