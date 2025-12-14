# Changelog

Todos los cambios relevantes del proyecto se documentan en este archivo.

El formato sigue una adaptación de [Keep a Changelog](https://keepachangelog.com/)
y el versionado es incremental y orientado al desarrollo del TFG.

---
## [v0.2] – 2025-12-14

### Added
- Implementación del **módulo de verificación de firmas digitales**.
- Selección de archivo de firma y documento a verificar mediante el gestor de archivos de Android.
- Validación de la integridad del documento firmado.
- Comprobación de la correspondencia entre firma, documento y certificado utilizado.
- Se comprueba que sea un certificado de usuario, que se pueda utilizar para firmar y que no haya caducado.
- Se comprueba la CA si procede (al trabajar con PQC no hay CA's compatibles actualmente).

---

## [v0.2] – 2025-12-08

### Added
- Integración del gestor de archivos de Android para el guardado de firmas generadas.
- Posibilidad de seleccionar la carpeta de destino y personalizar el nombre del archivo `.bin`.

### Changed
- El almacenamiento de firmas deja de realizarse en el almacenamiento interno de la aplicación.
- Mejoras en la experiencia de usuario (UX) durante el proceso de firmado.
- Actualización de mensajes de estado para reflejar correctamente el progreso y resultado del proceso.

### Fixed
- Corrección del problema de estado estancado tras completar una firma correctamente.
- Posibilidad de iniciar un nuevo proceso de firma sin reiniciar la actividad.

---

## [v0.1] – 2025-11-30

### Added
- Importación del proyecto desde repositorio base.
- Integración del trabajo previo realizado en local.
- Almacenamiento de certificados PQC en el KeyStore interno de la aplicación.
- Firma desacoplada de documentos utilizando certificados PQC.
- Generación de firmas binarias (`*.bin`) almacenadas inicialmente en el almacenamiento interno de la aplicación.
