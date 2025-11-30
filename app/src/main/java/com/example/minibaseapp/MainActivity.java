package com.example.minibaseapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSignKeyPair = findViewById(R.id.btnSignKeyPair);
        Button btnSignWithCert = findViewById(R.id.btnSignWithCert);
        Button btnCertificates = findViewById(R.id.btnCertificates);

        btnSignKeyPair.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignKeyPairActivity.class);
            startActivity(intent);
        });

        btnSignWithCert.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignWithCertActivity.class);
            startActivity(intent);
        });

        btnCertificates.setOnClickListener(v -> {
            Intent intent = new Intent(this, CertificatesActivity.class);
            startActivity(intent);
        });
    }
}
