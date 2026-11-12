package com.zekodnix.zaramoney.security.device;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * EC P-256 public keys (base64 X.509 SubjectPublicKeyInfo, as exported by the iOS Secure Enclave / Android Keystore)
 * and ECDSA-SHA256 signatures (DER, or raw 64-byte r||s).
 */
public final class DeviceKeys {

    /** Order of the secp256r1 (P-256) group: identifies the curve. */
    private static final BigInteger P256_ORDER = new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);

    private DeviceKeys() {}

    /** @throws IllegalArgumentException if not a valid base64 P-256 public key */
    public static ECPublicKey parseP256(String base64Spki) {
        if (base64Spki == null || base64Spki.isBlank() || base64Spki.length() > 512) {
            throw new IllegalArgumentException("Missing or oversized public key");
        }
        try {
            byte[] der = Base64.getDecoder().decode(base64Spki.trim());
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof ECPublicKey ec) || !P256_ORDER.equals(ec.getParams().getOrder())) {
                throw new IllegalArgumentException("Only EC P-256 keys are accepted");
            }
            return ec;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid public key", e);
        }
    }

    /** True if {@code base64Signature} is a valid ECDSA-SHA256 signature of {@code data} (UTF-8) by {@code base64Spki}. */
    public static boolean verify(String base64Spki, String data, String base64Signature) {
        if (base64Spki == null || data == null || base64Signature == null || base64Signature.length() > 256) {
            return false;
        }
        try {
            ECPublicKey key = parseP256(base64Spki);
            byte[] sig = Base64.getDecoder().decode(base64Signature.trim());
            // DER (iOS SecKey / Android Keystore default) or raw r||s (WebCrypto style)
            Signature verifier = Signature.getInstance(sig.length == 64 ? "SHA256withECDSAinP1363Format" : "SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return verifier.verify(sig);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
