MiniBaseApp – Android Application for Digital Certificate Management and Document Signing

Bachelor’s Degree Final Project (TFG)

Author: Samuel Ignacio Limón Riesgo

Supervisor: Andrés Marín López

University: ETSIT – Universidad Politécnica de Madrid (UPM)

📘 Project Overview

MiniBaseApp is an Android application whose main goal is to manage post-quantum digital certificates, enabling document signing and signature verification following a secure workflow.
This project investigates and implements digital signature mechanisms based on X.509 certificates and cryptographic keys generated using post-quantum algorithms such as ML-DSA-44, ML-DSA-65 and ML-DSA-87.

The project serves both as a functional tool and as an educational proof of concept.

🎯 Project Objectives

Implement a post-quantum certificate management system within Android.

Enable digital signing of PDF or text documents.

Validate signed documents and associated certificates.

Design an application with a simple user interface oriented towards academic and demonstrative use.

🧩 Current Features

Generation and storage of cryptographic key pairs
(module inherited from a previous project on which this work is based).

Credential store management (password creation and access control).

Import of post-quantum digital certificates.

Listing of available certificates.

Digital signing of documents using a previously imported certificate.

Signature verification, including cryptographic validation and certificate checks (validity period and signature usage).
Certificate authority validation and CRL/OCSP checks are not implemented in the current version.

Authentication for sensitive modules using password or biometric authentication.

🔧 Technologies Used

Android Studio Otter 2025.2.1

Java

Android Security APIs

Bouncy Castle KeyStore

Gradle 9.0

Supported formats and cryptography

X.509 certificates

Post-quantum certificates, currently tested with:

ML-DSA-44

ML-DSA-65

ML-DSA-87

The application supports any post-quantum certificate and algorithm that can be generated and handled by the Bouncy Castle cryptographic provider.
