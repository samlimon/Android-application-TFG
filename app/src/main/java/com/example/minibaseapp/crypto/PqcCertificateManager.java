package com.example.minibaseapp.crypto;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.cert.X509CertificateHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class PqcCertificateManager {

    private static final String TAG = "PqcCertificateManager";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String KEYSTORE_PROVIDER = "BC";
    private static final String KEYSTORE_FILE_NAME = "pqc_keystore.p12";

    private final Context context;

    // Aseguro que BC está registrado
    public PqcCertificateManager(Context context) {
        this.context = context.getApplicationContext();
        Security.removeProvider("BC"); // evitar conflicto con Android-BC
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // -------------------------
    // Clase auxiliar: clave + cert
    // -------------------------
    public static class KeyAndCert {
        public final PrivateKey privateKey;
        public final X509Certificate certificate;

        public KeyAndCert(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }

    /**
     * Clase auxiliar para la verificación de certificados
     * Con algoritmo, checks básicos, confianza CA
     * */
    public static class CertValidationResult {

        // Algoritmo / tipo
        public String algorithm;
        public boolean isPqc;

        // Checks básicos
        public boolean timeValid;
        public boolean isEndEntity;
        public boolean keyUsageOk;

        // CA opcional
        public boolean caSignatureChecked;  // true si hemos intentado verificar
        public boolean caSignatureOk;       // true si la verificación con la CA ha ido bien

        // Texto explicativo para logs / UI técnica
        public String diagnostics;

        // Helpers cómodos
        public boolean isOverallAcceptableForSigning() {
            // Aquí puedes decidir tu criterio mínimo
            return timeValid && isEndEntity && keyUsageOk;
        }
    }


    /** Pasamos la uri (documento .pem y .key que nos sube el usuario del dispositivo)
    * a bytes para luego poder importar el certificado y la clave*/
    private byte[] readAllBytesFromUri(android.net.Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("No se pudo abrir InputStream para Uri: " + uri);
            }
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    // Importamos el certificado
    private X509Certificate parseCertificateFromPemBytes(byte[] certBytes) throws Exception {
        try (PEMParser pemParser = new PEMParser(
                new InputStreamReader(new ByteArrayInputStream(certBytes), StandardCharsets.UTF_8))) {

            Object obj = pemParser.readObject();
            if (obj == null) {
                throw new Exception("El fichero PEM está vacío o no contiene un objeto válido");
            }

            if (!(obj instanceof X509CertificateHolder)) {
                throw new Exception("El PEM no contiene un certificado X.509, tipo: " +
                        obj.getClass().getName());
            }

            X509CertificateHolder holder = (X509CertificateHolder) obj;

            JcaX509CertificateConverter converter =
                    new JcaX509CertificateConverter().setProvider(KEYSTORE_PROVIDER);

            return converter.getCertificate(holder);

        } catch (Exception e) {
            Log.e(TAG, "Error al parsear certificado", e);
            throw new Exception("Error al parsear certificado X.509 con BouncyCastle", e);
        }
    }

    // Importamos la clave privada
    private PrivateKey parsePrivateKeyFromPemBytes(byte[] keyBytes) throws Exception {
        try (PEMParser pemParser = new PEMParser(
                new InputStreamReader(new ByteArrayInputStream(keyBytes), StandardCharsets.UTF_8))) {

            Object obj = pemParser.readObject();

            if (obj == null) {
                throw new Exception("El fichero de clave privada está vacío o no es PEM válido");
            }

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(KEYSTORE_PROVIDER);

            // 1) Caso típico: PKCS#8 -> PrivateKeyInfo
            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                org.bouncycastle.asn1.pkcs.PrivateKeyInfo pkInfo =
                        (org.bouncycastle.asn1.pkcs.PrivateKeyInfo) obj;
                return converter.getPrivateKey(pkInfo);
            }

            // 2) Caso keypair (por si algún día usas "BEGIN PRIVATE KEY" de otro tipo o "BEGIN KEY PAIR")
            if (obj instanceof org.bouncycastle.openssl.PEMKeyPair) {
                org.bouncycastle.openssl.PEMKeyPair keyPair =
                        (org.bouncycastle.openssl.PEMKeyPair) obj;
                return converter.getKeyPair(keyPair).getPrivate();
            }

            // 3) Caso clave privada *encriptada* (por si algún día usas password en el .key)
            if (obj instanceof org.bouncycastle.openssl.PEMEncryptedKeyPair) {
                throw new Exception("La clave privada está cifrada (PEMEncryptedKeyPair). " +
                        "El soporte de descifrado con contraseña no está implementado todavía.");
            }

            // 4) Cualquier otro tipo -> info detallada para depurar
            throw new Exception("Tipo de objeto PEM no soportado: " + obj.getClass().getName());
        } catch (Exception e) {
            Log.e(TAG, "Error al parsear clave privada", e);
            throw new Exception("Error al parsear clave privada: " +
                    e.getClass().getSimpleName() + " - " + e.getMessage(), e);
        }
    }

    /** Creamos un keystore PKCS#12 para almacenar el certificado y la clave
    * Si existe un keystore lo abre y si no crea uno nuevo*/
    private KeyStore loadOrCreateKeyStore(char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE, KEYSTORE_PROVIDER);
        try {
            // Intentamos abrir un p12 existente
            try (FileInputStream fis = context.openFileInput(KEYSTORE_FILE_NAME)) {
                ks.load(fis, password);
            }
        } catch (IOException e) {
            // Si no existe o hay problema al leer, creamos uno nuevo vacío
            ks.load(null, password);
        }
        return ks;
    }

    // Guardamos el keystore generado
    private void storeKeyStore(KeyStore ks, char[] password) throws Exception {
        try (FileOutputStream fos = context.openFileOutput(KEYSTORE_FILE_NAME, Context.MODE_PRIVATE)) {
            ks.store(fos, password);
        }
    }

    public void importCredentialFromPemAndKey(Uri certUri, Uri keyUri, Uri caCertUri, String alias, char[] keystorePassword) throws Exception {
        // Leo los bytes desde la Uri tanto del certificado como de la clave privada
        byte[] certBytes = readAllBytesFromUri(certUri);
        byte[] keyBytes = readAllBytesFromUri(keyUri);
        byte[] caCertBytes   = readAllBytesFromUri(caCertUri);

        // Parseo el certificado y clave privada
        X509Certificate cert = parseCertificateFromPemBytes(certBytes);
        PrivateKey privateKey = parsePrivateKeyFromPemBytes(keyBytes);
        X509Certificate caCert   = parseCertificateFromPemBytes(caCertBytes);

        // Validación básica (modo estricto) antes de guardar en el almacén
        CertValidationResult vr = validateCertificate(cert, caCert);

        // Criterio mínimo: debe ser end-entity, vigente y con KeyUsage.digitalSignature
        if (!vr.isOverallAcceptableForSigning()) {
            Log.w(TAG, "Certificado rechazado en importación:\n" + vr.diagnostics);
            throw new Exception("Certificado no apto para firma electrónica.\n" +
                    "Motivo:\n" + vr.diagnostics);
        }

        // Carga o creación del KeyStore PKCS#12
        KeyStore ks = loadOrCreateKeyStore(keystorePassword);

        // Creación de la entrada PrivateKeyEntry con cadena de certificados
        Certificate[] chain = new Certificate[]{cert, caCert};
        KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(privateKey, chain);

        /**Creación de la contraseña del contenedor PKCS#12 el usuario deberá introducirla
        * cada vez que quiera utilizar el certificado*/
        KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(keystorePassword);

        // Creación/actualización del alias
        ks.setEntry(alias, entry, protParam);

        // Guardado del KeyStore
        storeKeyStore(ks, keystorePassword);
    }
    /**
     * Lista todos los certificados del keystore PKCS#12 interno.
     * Por simplicidad usamos una contraseña fija "changeit" para abrir el keystore.
     * Más adelante puedes cambiar esto para pedir la contraseña al usuario.
     */
    public List<ImportedCert> listCertificates(char[] keystorePassword) throws Exception {
        List<ImportedCert> result = new ArrayList<>();
        KeyStore ks = loadOrCreateKeyStore(keystorePassword);
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate cert = ks.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;
                result.add(toImportedCert(alias, x509));
            }
        }
        return result;
    }
    private ImportedCert toImportedCert(String alias, X509Certificate cert) {
        String subject = cert.getSubjectX500Principal().getName();
        String issuer = cert.getIssuerX500Principal().getName();
        java.util.Date notBefore = cert.getNotBefore();
        java.util.Date notAfter = cert.getNotAfter();
        boolean valid = isCurrentlyValid(cert);
        return new ImportedCert(alias, subject, issuer, notBefore, notAfter, valid);
    }

    public boolean isCurrentlyValid(X509Certificate cert) {
        try {
            cert.checkValidity();
            return true;
        } catch (CertificateException e) {
            return false;
        }
    }

    // -------------------------
    // Obtener clave privada + cert por alias
    // -------------------------
    public KeyAndCert getKeyAndCertificate(String alias, char[] keystorePassword) throws Exception {
        KeyStore ks = loadOrCreateKeyStore(keystorePassword);

        Key key = ks.getKey(alias, keystorePassword);
        if (!(key instanceof PrivateKey)) {
            throw new Exception("El alias " + alias + " no tiene una clave privada asociada");
        }

        Certificate cert = ks.getCertificate(alias);
        if (!(cert instanceof X509Certificate)) {
            throw new Exception("El alias " + alias + " no tiene un certificado X.509 válido");
        }

        return new KeyAndCert((PrivateKey) key, (X509Certificate) cert);
    }

    // -------------------------
    // Firmar datos con un alias
    // -------------------------
    public byte[] signDataWithAlias(String alias, char[] keystorePassword, byte[] data) throws Exception {
        KeyAndCert kc = getKeyAndCertificate(alias, keystorePassword);
        PrivateKey privateKey = kc.privateKey;

        String algName = privateKey.getAlgorithm(); // debería reflejar ML-DSA-44, etc.
        Log.d(TAG, "Algoritmo de la clave privada: " + algName);

        Signature sig = Signature.getInstance(algName, KEYSTORE_PROVIDER);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    /**
     * Carga un certificado X.509 desde un Uri (por ejemplo, un .pem que el usuario selecciona
     * con el gestor de archivos del dispositivo).
     *
     * Reutiliza el parseo PEM ya existente en parseCertificateFromPemBytes(...).
     */
    public X509Certificate loadCertificateFromUri(Context ctx, Uri certUri) throws Exception {
        // Leemos todos los bytes del fichero (PEM)
        byte[] certBytes = readAllBytesFromUri(certUri);
        // Reutilizamos el parseador PEM de BouncyCastle
        return parseCertificateFromPemBytes(certBytes);
    }

    /**
     * Verifica criptográficamente una firma sobre unos datos usando la clave pública
     * del certificado proporcionado.
     *
     * @param cert           Certificado del firmante (X.509)
     * @param data           Datos originales (documento) en bytes
     * @param signatureBytes Firma en bytes (por ejemplo, el .bin generado por la app)
     * @return true si la firma es válida para esos datos y ese certificado, false en caso contrario
     */
    public boolean verifyDataWithCertificate(
            X509Certificate cert,
            byte[] data,
            byte[] signatureBytes
    ) throws Exception {

        PublicKey publicKey = cert.getPublicKey();
        String algName = publicKey.getAlgorithm();
        if (algName == null || algName.isEmpty()) {
            // Fallback por si acaso
            algName = cert.getSigAlgName();
        }

        Log.d(TAG, "Verificando firma con algoritmo: " + algName);

        Signature sig = Signature.getInstance(algName, KEYSTORE_PROVIDER);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signatureBytes);
    }

    public CertValidationResult validateCertificate(
            X509Certificate userCert,
            @Nullable X509Certificate caCert
    ) {
        CertValidationResult result = new CertValidationResult();
        StringBuilder diag = new StringBuilder();

        // 1) Algoritmo e indicador PQC
        String algFromKey = null;
        try {
            algFromKey = userCert.getPublicKey().getAlgorithm();
        } catch (Exception ignored) {
        }
        String algFromSig = null;
        try {
            algFromSig = userCert.getSigAlgName();
        } catch (Exception ignored) {
        }

        String algorithm = (algFromKey != null) ? algFromKey :
                (algFromSig != null ? algFromSig : "DESCONOCIDO");
        result.algorithm = algorithm;

        String algLower = algorithm.toLowerCase(Locale.ROOT);
        result.isPqc = algLower.contains("ml-dsa") || algLower.contains("dilithium");

        diag.append("Algoritmo del certificado: ").append(algorithm)
                .append(result.isPqc ? " (PQC)\n" : "\n");

        // 2) Vigencia temporal
        try {
            userCert.checkValidity();
            result.timeValid = true;
            diag.append("Vigencia temporal: OK (dentro de notBefore/notAfter)\n");
        } catch (CertificateExpiredException e) {
            result.timeValid = false;
            diag.append("Vigencia temporal: NO VÁLIDA (certificado caducado)\n");
        } catch (CertificateNotYetValidException e) {
            result.timeValid = false;
            diag.append("Vigencia temporal: NO VÁLIDA (todavía no es válido)\n");
        }

        // 3) BasicConstraints (end-entity vs CA)
        int bc = userCert.getBasicConstraints();
        // En X.509: <0 => no es CA; >=0 => es CA
        result.isEndEntity = (bc < 0);
        if (bc < 0) {
            diag.append("BasicConstraints: certificado de usuario (no CA)\n");
        } else {
            diag.append("BasicConstraints: certificado de CA (no debería usarse directamente para firmar documentos)\n");
        }

        // 4) KeyUsage (digitalSignature)
        boolean[] keyUsage = userCert.getKeyUsage();
        if (keyUsage == null) {
            // Modo estricto: si no está KeyUsage, NO aceptamos para firma
            result.keyUsageOk = false;
            diag.append("KeyUsage: NO presente (modo estricto) -> NO apto para firma\n");
        } else {
            boolean digitalSignature = keyUsage.length > 0 && keyUsage[0];
            result.keyUsageOk = digitalSignature;
            diag.append("KeyUsage.digitalSignature: ")
                    .append(digitalSignature ? "true (OK)\n" : "false -> NO apto para firma\n");
        }

        result.diagnostics = diag.toString();
        return result;
    }

}
