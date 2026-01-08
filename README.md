# MiniBaseApp – Aplicación Android para gestión y firma digital de documentos  
**Trabajo de Fin de Grado (TFG)**  
- Autor: Samuel Ignacio Limón Riesgo 
- Tutor: Andres Marin Lopez 
- Universidad: ETSIT UPM  

---

## 📘 Descripción general del proyecto

MiniBaseApp es una aplicación Android cuyo objetivo es gestionar certificados digitales post-quantum, permitiendo firmar documentos y validar firmas siguiendo un flujo seguro.  
Este proyecto investiga y desarrolla mecanismos de firma digital utilizando certificados X.509 y claves generadas con algoritmos postcuánticos como ml-dsa 44, 67 y 85.

El proyecto sirve tanto como herramienta funcional como prueba de concepto didáctica.

---

## 🎯 Objetivos del TFG

- Implementar un sistema de gestión de certificados pqc dentro de Android.
- Permitir la firma digital de documentos PDF o texto.
- Validar documentos y certificados firmados.
- Diseñar una aplicación con interfaz sencilla orientada al uso académico y demostrativo.

---

## 🧩 Funcionalidades actuales

- Generación y almacenamiento de pares de claves. (parte del proyecto previo del cual parte este trabajo)
- Gestión del almacén (creación de contraseña, etc.).
- Importación de certificados digitales pqc.
- Listado de certificados disponibles.
- Firma digital de documentos seleccionando un certificado previamente cargado.
- Verificación de la firma generada, se verifica el resultado criptográfico y se valida que el certificado esté vigente y sea apto para firma, no se valida CA ni se contrasta contra CRL u OCSP.
- Autenticación a los módulos sensibles por medio de biometría o contraseña

---

## 🔧 Tecnologías utilizadas

- Android Studio Otter 2025.2.1
- Java
- API de Seguridad de Android.
- KeyStore BouncyCastle.
- Gradle 9.0.
- Formatos:
  - Certificados X.509
  - Certificados PQC actualmente trabajo con certificados basados en ml-dsa 44, 65 y 87 pero acepta todos los que Bouncy Castle puede utilizar y generar

---
