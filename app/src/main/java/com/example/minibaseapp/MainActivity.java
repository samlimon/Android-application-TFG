package com.example.minibaseapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnKeyPair;
    private Button btnCertificates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnKeyPair = findViewById(R.id.btnKeyPair);
        btnCertificates = findViewById(R.id.btnCertificates);

        btnKeyPair.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, SignKeyPairActivity.class);
            startActivity(i);
        });

        btnCertificates.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, CertificatesMenuActivity.class);
            startActivity(i);
        });
    }
}
