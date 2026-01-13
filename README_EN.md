[![Español](https://img.shields.io/badge/lang-Español-red)](README.md)
[![English](https://img.shields.io/badge/lang-English-blue)](README_EN.md)

# MiniBaseApp – Android Application for Post-Quantum Digital Certificate Management and Document Signing
**Bachelor’s Degree Final Project (TFG)**  

- **Author:** Samuel Ignacio Limón Riesgo  
- **Supervisor:** Andrés Marín López  
- **University:** ETSIT – Universidad Politécnica de Madrid (UPM)  

---

## 📘 Project Overview

MiniBaseApp is an Android application whose main goal is to manage **post-quantum digital certificates**, enabling document signing and signature verification through a secure and controlled workflow.

This project investigates and implements digital signature mechanisms based on **X.509 certificates** and cryptographic keys generated using **post-quantum signature algorithms**, specifically **ML-DSA-44, ML-DSA-65 and ML-DSA-87**, as standardized by NIST.

The application serves both as a **functional prototype** and as an **educational proof of concept**, exploring the feasibility of integrating post-quantum cryptography into mobile identity and trust services.

---

## 🎯 Project Objectives

- Implement a post-quantum certificate management system on Android.
- Enable digital signing of PDF or text documents using imported certificates.
- Verify digital signatures and perform basic certificate validation.
- Design an intuitive user interface oriented towards academic, demonstrative and experimental use.

---

## 🧩 Current Features

- Generation and storage of cryptographic key pairs  
  *(module inherited from a previous project on which this work is based)*.
- Secure credential store management, including password-based protection.
- Import of post-quantum digital certificates (X.509).
- Listing and selection of available certificates.
- Digital signing of documents using a previously imported certificate.
- Signature verification, including:
  - Cryptographic validation of the signature.
  - Certificate validity period checks.
  - Verification of certificate suitability for digital signature usage.
- Authentication for sensitive operations using **password or biometric authentication**.

> ⚠️ **Note:**  
> Certificate Authority (CA) chain validation, CRL checks and OCSP verification are **not implemented** in the current version, as the project operates in an experimental post-quantum context.

---

## 🔧 Technologies Used

- **Android Studio Otter 2025.2.1**
- **Java**
- **Android Security APIs**
- **Bouncy Castle Cryptographic Provider**
- **Gradle 9.0**

### Cryptographic formats and algorithms

- **X.509 certificates**
- **Post-quantum digital signatures**, currently tested with:
  - ML-DSA-44  
  - ML-DSA-65  
  - ML-DSA-87  

The application supports any post-quantum algorithm and certificate format that can be generated and handled by the **Bouncy Castle** provider.

---

## 🔐 Security Considerations

- Private keys are stored within a PKCS#12 keystore protected by a user-defined password.
- When enabled, the keystore password is encrypted using a symmetric key stored in the **Android Keystore**, bound to biometric authentication.
- Sensitive operations (certificate management and signing) require prior user authentication.

---

## 🚧 Limitations and Future Work

This project represents an **experimental prototype**. Planned future improvements include:

- Integration with standard signature formats such as **PAdES**.
- Full certificate chain validation and trust anchor management.
- CRL and OCSP-based revocation checking.
- Hybrid post-quantum / classical signature support.
- Alignment with the **European Digital Identity Wallet (EUDI Wallet)** ecosystem.

---

## 📄 Academic Context

This project has been developed as a **Bachelor’s Degree Final Project (TFG)** and builds upon a previous academic work focused on post-quantum signature experimentation without certificate management.

The complete source code is publicly available for academic and research purposes.

---

