[![Español](https://img.shields.io/badge/lang-Español-red)](README.md)
[![English](https://img.shields.io/badge/lang-English-blue)](README_EN.md)
  
# MiniBaseApp – Aplicación Android para gestión y firma digital de documentos  
**Trabajo de Fin de Grado (TFG)**  
- Autor: Samuel Ignacio Limón Riesgo 
- Tutor: Andres Marin Lopez 
- Universidad: ETSIT – Universidad Politécnica de Madrid (UPM) 

---

## 📘 Descripción general del proyecto

MiniBaseApp es una aplicación Android cuyo objetivo principal es la **gestión de certificados digitales post-cuánticos**, permitiendo la firma y verificación de documentos mediante un flujo seguro y controlado.

Este proyecto investiga y desarrolla mecanismos de firma digital basados en **certificados X.509** y claves criptográficas generadas mediante **algoritmos de firma post-cuánticos**, concretamente **ML-DSA-44, ML-DSA-65 y ML-DSA-87**, recientemente estandarizados por el NIST.

La aplicación sirve tanto como **prototipo funcional** como **prueba de concepto didáctica**, orientada a analizar la viabilidad de integrar criptografía post-cuántica en entornos móviles y servicios de identidad digital.

---

## 🎯 Objetivos del proyecto

- Implementar un sistema de gestión de certificados post-cuánticos en Android.
- Permitir la firma digital de documentos PDF o de texto mediante certificados importados.
- Verificar firmas digitales y realizar validaciones básicas de certificados.
- Diseñar una interfaz de usuario sencilla, orientada a un uso académico, demostrativo y experimental.

---

## 🧩 Funcionalidades actuales

- Generación y almacenamiento de pares de claves criptográficas  
  *(módulo heredado de un proyecto previo sobre el que se apoya este trabajo)*.
- Gestión segura del almacén de credenciales, incluyendo protección mediante contraseña.
- Importación de certificados digitales post-cuánticos (X.509).
- Listado y selección de certificados disponibles.
- Firma digital de documentos utilizando certificados previamente importados.
- Verificación de firmas digitales, incluyendo:
  - Validación criptográfica de la firma.
  - Comprobación del periodo de validez del certificado.
  - Verificación de la aptitud del certificado para operaciones de firma.
- Autenticación de las operaciones sensibles mediante **contraseña o biometría**.

> ⚠️ **Nota:**  
> La validación de la cadena de certificación, así como las comprobaciones mediante CRL u OCSP, **no están implementadas** en la versión actual, dado el carácter experimental del proyecto y la ausencia de infraestructuras PKI post-cuánticas plenamente operativas.

---

## 🔧 Tecnologías utilizadas

- **Android Studio Otter 2025.2.1**
- **Java**
- **APIs de seguridad de Android**
- **Proveedor criptográfico Bouncy Castle**
- **Gradle 9.0**

### Formatos y algoritmos criptográficos

- **Certificados X.509**
- **Firmas digitales post-cuánticas**, actualmente probadas con:
  - ML-DSA-44  
  - ML-DSA-65  
  - ML-DSA-87  

La aplicación es compatible con cualquier algoritmo post-cuántico y formato de certificado soportado por el proveedor criptográfico **Bouncy Castle**.

---

## 🔐 Consideraciones de seguridad

- Las claves privadas se almacenan en un contenedor PKCS#12 protegido mediante una contraseña definida por el usuario.
- Cuando se habilita la autenticación biométrica, la contraseña del almacén se cifra mediante una clave simétrica protegida por el **Android Keystore**.
- Las operaciones sensibles, como la gestión de certificados y la firma de documentos, requieren autenticación previa del usuario.

---

## 🚧 Limitaciones y líneas futuras

Este proyecto constituye un **prototipo experimental**. Entre las principales líneas de evolución previstas se incluyen:

- Integración con formatos de firma estándar como **PAdES**.
- Validación completa de cadenas de certificación y gestión de anclas de confianza.
- Comprobaciones de revocación mediante **CRL** y **OCSP**.
- Soporte híbrido para firmas clásicas y post-cuánticas.
- Alineamiento con el ecosistema de la **European Digital Identity Wallet (EUDI Wallet)**.

---

## 📄 Contexto académico

Este proyecto se ha desarrollado como **Trabajo de Fin de Grado (TFG)** y se apoya en un trabajo académico previo orientado a la experimentación con algoritmos de firma post-cuántica sin gestión de certificados.

---
