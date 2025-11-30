package com.example.minibaseapp;

import android.app.Application;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }
}