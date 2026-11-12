package com.zekodnix.zaramoney.security.device;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** An EC P-256 key pair standing in for a phone's Secure Enclave / Keystore key in tests. */
public final class TestDeviceKey {

    private final KeyPair keyPair;

    private TestDeviceKey(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public static TestDeviceKey generate() {
        return generate("secp256r1");
    }

    public static TestDeviceKey generate(String curve) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec(curve));
            return new TestDeviceKey(gen.generateKeyPair());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Base64 X.509 SubjectPublicKeyInfo, as the app sends it. */
    public String publicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /** Base64 DER ECDSA-SHA256 signature. */
    public String sign(String data) {
        return sign(data, "SHA256withECDSA");
    }

    /** Base64 raw r||s (64 bytes) signature. */
    public String signP1363(String data) {
        return sign(data, "SHA256withECDSAinP1363Format");
    }

    private String sign(String data, String algorithm) {
        try {
            Signature s = Signature.getInstance(algorithm);
            s.initSign(keyPair.getPrivate());
            s.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
