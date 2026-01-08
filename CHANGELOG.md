# Changelog

Todos los cambios relevantes del proyecto se documentan en este archivo.

El formato sigue una adaptación de [Keep a Changelog](https://keepachangelog.com/)
y el versionado es incremental y orientado al desarrollo del TFG.

---
## [v4] – 2026-01-08

### Added
- Implementación de la autenticación por biometría.

### Fixed
- Ajuste en la verificación y validación del parametro KeyUsage.digitalSignature para comprobar que los certificados sean aptos para firma.
- Se ha eliminado de issuer y subject en el listado de certificados.
- Se corrije el mensaje tras la verificación de una firma para dejarlo más claro.
- Se corrije el resultado de la verificación para certificados con parámetros erróneos.

---
## [v3] – 2025-12-14

### Added
- Implementación del **módulo de verificación de firmas digitales**.
- Selección de archivo de firma y documento a verificar mediante el gestor de archivos de Android.
- Validación de la integridad del documento firmado.
- Comprobación de la correspondencia entre firma, documento y certificado utilizado.
- Se comprueba que sea un certificado de usuario, que se pueda utilizar para firmar y que no haya caducado.

---

## [v2] – 2025-12-08

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

## [v1] – 2025-11-30

### Added
- Importación del proyecto desde repositorio base.
- Integración del trabajo previo realizado en local.
- Almacenamiento de certificados PQC en el KeyStore interno de la aplicación.
- Firma desacoplada de documentos utilizando certificados PQC.
- Generación de firmas binarias (`*.bin`) almacenadas inicialmente en el almacenamiento interno de la aplicación.
