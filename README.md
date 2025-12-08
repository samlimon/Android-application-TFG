# MiniBaseApp – Aplicación Android para gestión y firma digital de documentos  
**Trabajo de Fin de Grado (TFG)**  
Autor: Samuel Ignacio Limón Riesgo 
Tutor: Andres Marin Lopez 
Universidad: ETSIT UPM  

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
- Importación de certificados digitales pqc.
- Listado de certificados disponibles.
- Firma digital de documentos seleccionando un certificado.
- Validación básica de certificados y firmas.
- Gestión del almacén (creación de contraseña, etc.).

---

## 🔧 Tecnologías utilizadas

- Android Studio Otter 2025.2.1
- Java
- API de Seguridad de Android.
- KeyStore BouncyCastle dependiendo del modo de firma.
- Gradle 9.0.
- Formatos:
  - Certificados X.509
  - Claves PQC actualmente trabajo con certificados ml-dsa 44 pero acepta todos los que Bouncy Castle puede utilizar y generar

---

## 📅 Seguimiento de versiones

v0.1 – 30/11/2025
- Proyecto importado desde repositorio base.
- Había estado trabajando en local hasta ahora, así que tenemos la primera versión funcional
- Almacenamiento de certificados PQC en keystore de la aplicación
- Firma de documentos desacoplada, utilizando los certificados PQC almacenandos, ahora mismo la firma .bin generada se almacena en la propia aplicación

v0.2 – 08/12/2025
-Actualización del sistema de almacenamiento de firmas generadas:
Se reemplaza el almacenamiento interno de la aplicación por el gestor de archivos de Android, permitiendo que el usuario:
  -Seleccione la carpeta de destino.
  -Modifique libremente el nombre del archivo .bin generado.
  -Tenga control total sobre dónde y cómo se guarda la firma.

-Mejoras en la experiencia de uso (UX) y en la usabilidad general:
  -Se añadieron mensajes más claros para mostrar el estado del proceso de firmado (éxito, error, flujo disponible, etc.).
  -Se corrigió el problema de “estado estancado” tras firmar un documento: ahora la aplicación vuelve automáticamente a un estado inicial, permitiendo iniciar un nuevo proceso de firma sin tener que reiniciar la actividad.
---
