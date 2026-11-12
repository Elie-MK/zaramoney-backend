package com.zekodnix.zaramoney.security.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zekodnix.zaramoney.config.ApplicationProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DeviceSignatureTest {

    private final TestDeviceKey device = TestDeviceKey.generate();
    private final TestDeviceKey tx = TestDeviceKey.generate();
    private final NonceStore nonceStore = new NonceStore();
    private final DeviceSignatureVerifier verifier = new DeviceSignatureVerifier(nonceStore, new ApplicationProperties());

    // --------------------------------------------------------------- keys

    @Test
    void acceptsP256DerAndRawSignatures() {
        assertThat(DeviceKeys.verify(device.publicKey(), "hello", device.sign("hello"))).isTrue();
        assertThat(DeviceKeys.verify(device.publicKey(), "hello", device.signP1363("hello"))).isTrue();
        assertThat(DeviceKeys.verify(device.publicKey(), "hellO", device.sign("hello"))).isFalse();
        assertThat(DeviceKeys.verify(tx.publicKey(), "hello", device.sign("hello"))).isFalse();
        assertThat(DeviceKeys.verify(device.publicKey(), "hello", "not-base64!")).isFalse();
    }

    @Test
    void rejectsOtherCurvesAndKeyTypes() throws Exception {
        String p384 = TestDeviceKey.generate("secp384r1").publicKey();
        assertThatThrownBy(() -> DeviceKeys.parseP256(p384)).isInstanceOf(IllegalArgumentException.class);

        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        String rsaKey = Base64.getEncoder().encodeToString(rsa.generateKeyPair().getPublic().getEncoded());
        assertThatThrownBy(() -> DeviceKeys.parseP256(rsaKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceKeys.parseP256("garbage")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> DeviceKeys.parseP256(device.publicKey())).doesNotThrowAnyException();
    }

    // --------------------------------------------------------------- challenges

    @Test
    void challengeIsSingleUse() {
        String nonce = nonceStore.issueChallenge();
        assertThat(nonceStore.consumeChallenge(nonce)).isTrue();
        assertThat(nonceStore.consumeChallenge(nonce)).isFalse();
        assertThat(nonceStore.consumeChallenge("never-issued")).isFalse();
    }

    // --------------------------------------------------------------- request verification

    private MockHttpServletRequest signed(String method, String uri, String body, String deviceId, long ts, String nonce, TestDeviceKey key) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        String canonical = CanonicalRequest.forRequest(
            method,
            uri,
            String.valueOf(ts),
            nonce,
            deviceId,
            CanonicalRequest.bodyHash(body.getBytes(StandardCharsets.UTF_8))
        );
        req.addHeader("X-Device-Id", deviceId);
        req.addHeader("X-Timestamp", String.valueOf(ts));
        req.addHeader("X-Nonce", nonce);
        req.addHeader("X-Signature", key.sign(canonical));
        req.addHeader("X-Tx-Signature", tx.sign(canonical));
        return req;
    }

    private DeviceSignatureVerifier.Expected expected(boolean requireTx) {
        return new DeviceSignatureVerifier.Expected("sid-1", "device-1234", device.publicKey(), tx.publicKey(), requireTx);
    }

    private static String code(Runnable r) {
        try {
            r.run();
            return "ok";
        } catch (DeviceAuthException e) {
            return e.getCode();
        }
    }

    @Test
    void validRequestPassesOnceThenIsAReplay() {
        long now = System.currentTimeMillis();
        var req = signed("POST", "/api/x", "{\"a\":1}", "device-1234", now, "nonce-00000001", device);
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(code(() -> verifier.verify(req, body, expected(true)))).isEqualTo("ok");
        assertThat(code(() -> verifier.verify(req, body, expected(true)))).isEqualTo("device_nonce_reused");
    }

    @Test
    void tamperedBodyWrongKeyWrongDeviceOldTimestampAreRejected() {
        long now = System.currentTimeMillis();
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        var tampered = signed("POST", "/api/x", "{\"a\":2}", "device-1234", now, "nonce-00000002", device);
        assertThat(code(() -> verifier.verify(tampered, body, expected(false)))).isEqualTo("device_signature_invalid");

        var otherKey = signed("POST", "/api/x", "{\"a\":1}", "device-1234", now, "nonce-00000003", tx);
        assertThat(code(() -> verifier.verify(otherKey, body, expected(false)))).isEqualTo("device_signature_invalid");

        var otherDevice = signed("POST", "/api/x", "{\"a\":1}", "device-9999", now, "nonce-00000004", device);
        assertThat(code(() -> verifier.verify(otherDevice, body, expected(false)))).isEqualTo("device_mismatch");

        var old = signed("POST", "/api/x", "{\"a\":1}", "device-1234", now - 120_000, "nonce-00000005", device);
        assertThatThrownBy(() -> verifier.verify(old, body, expected(false)))
            .isInstanceOf(DeviceAuthException.class)
            .satisfies(e -> {
                assertThat(((DeviceAuthException) e).getCode()).isEqualTo("device_signature_expired");
                assertThat(((DeviceAuthException) e).getServerTime()).isNotNull();
            });

        var otherPath = signed("POST", "/api/y", "{\"a\":1}", "device-1234", now, "nonce-00000006", device);
        otherPath.setRequestURI("/api/x");
        assertThat(code(() -> verifier.verify(otherPath, body, expected(false)))).isEqualTo("device_signature_invalid");

        assertThat(code(() -> verifier.verify(new MockHttpServletRequest("GET", "/api/x"), new byte[0], expected(false)))).isEqualTo(
            "device_signature_missing"
        );
    }

    @Test
    void moneyOperationsNeedTheTransactionKeySignature() {
        long now = System.currentTimeMillis();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        var req = signed("POST", "/api/send", "{}", "device-1234", now, "nonce-00000010", device);
        req.removeHeader("X-Tx-Signature");
        assertThat(code(() -> verifier.verify(req, body, expected(true)))).isEqualTo("transaction_signature_invalid");

        var withDeviceKeyAsTx = signed("POST", "/api/send", "{}", "device-1234", now, "nonce-00000011", device);
        withDeviceKeyAsTx.removeHeader("X-Tx-Signature");
        withDeviceKeyAsTx.addHeader("X-Tx-Signature", withDeviceKeyAsTx.getHeader("X-Signature"));
        assertThat(code(() -> verifier.verify(withDeviceKeyAsTx, body, expected(true)))).isEqualTo("transaction_signature_invalid");
    }

    @Test
    void failedVerificationDoesNotBurnTheNonce() {
        long now = System.currentTimeMillis();
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        var forged = signed("POST", "/api/x", "{\"a\":1}", "device-1234", now, "nonce-00000020", tx);
        assertThat(code(() -> verifier.verify(forged, body, expected(false)))).isEqualTo("device_signature_invalid");
        var genuine = signed("POST", "/api/x", "{\"a\":1}", "device-1234", now, "nonce-00000020", device);
        assertThat(code(() -> verifier.verify(genuine, body, expected(false)))).isEqualTo("ok");
    }
}
