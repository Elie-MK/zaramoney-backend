package com.zekodnix.zaramoney.security.device;

import com.zekodnix.zaramoney.config.ApplicationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Verifies the device-key signature of a request (headers {@code X-Device-Id}, {@code X-Timestamp}, {@code X-Nonce},
 * {@code X-Signature}, and {@code X-Tx-Signature} for money operations). See {@link CanonicalRequest} for the format.
 */
@Component
public class DeviceSignatureVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceSignatureVerifier.class);

    public static final String HEADER_DEVICE_ID = "X-Device-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";
    public static final String HEADER_TX_SIGNATURE = "X-Tx-Signature";

    private static final Pattern NONCE_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final NonceStore nonceStore;
    private final ApplicationProperties applicationProperties;
    private Clock clock = Clock.systemUTC();

    public DeviceSignatureVerifier(NonceStore nonceStore, ApplicationProperties applicationProperties) {
        this.nonceStore = nonceStore;
        this.applicationProperties = applicationProperties;
    }

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** What the session expects from the caller. */
    public record Expected(String sid, String deviceId, String devicePublicKey, String transactionPublicKey, boolean requireTransactionSignature) {}

    /**
     * @param body the raw body bytes, or {@code null} for a multipart request (uses {@link CanonicalRequest#MULTIPART_BODY_HASH})
     * @throws DeviceAuthException on any failure
     */
    public void verify(HttpServletRequest request, byte[] body, Expected expected) {
        String deviceId = request.getHeader(HEADER_DEVICE_ID);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (deviceId == null || timestamp == null || nonce == null || signature == null) {
            throw fail("device_signature_missing", "Signed request headers are missing", expected);
        }
        if (!deviceId.equals(expected.deviceId())) {
            throw fail("device_mismatch", "This session belongs to another device", expected);
        }
        if (!NONCE_FORMAT.matcher(nonce).matches()) {
            throw fail("device_signature_invalid", "Invalid nonce", expected);
        }

        long window = applicationProperties.getSecurity().getSessions().getSignatureWindowSeconds();
        Instant now = clock.instant();
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw fail("device_signature_invalid", "Invalid timestamp", expected);
        }
        if (Math.abs(now.toEpochMilli() - ts) > window * 1000) {
            throw new DeviceAuthException(
                HttpStatus.UNAUTHORIZED,
                "device_signature_expired",
                "Request timestamp is outside the accepted window, check the device clock",
                now
            );
        }

        String pathAndQuery = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        String bodyHash = body == null ? CanonicalRequest.MULTIPART_BODY_HASH : CanonicalRequest.bodyHash(body);
        String canonical = CanonicalRequest.forRequest(request.getMethod(), pathAndQuery, timestamp, nonce, deviceId, bodyHash);

        if (!DeviceKeys.verify(expected.devicePublicKey(), canonical, signature)) {
            throw fail("device_signature_invalid", "Invalid device signature", expected);
        }
        if (expected.requireTransactionSignature()) {
            String txSignature = request.getHeader(HEADER_TX_SIGNATURE);
            if (txSignature == null || !DeviceKeys.verify(expected.transactionPublicKey(), canonical, txSignature)) {
                throw fail("transaction_signature_invalid", "This operation must be confirmed with biometrics", expected);
            }
        }
        // Only after the signature is valid, so nobody without the key can burn nonces
        if (!nonceStore.markUsed(expected.sid() + ":" + nonce, Duration.ofSeconds(window * 2))) {
            throw fail("device_nonce_reused", "This request was already sent (replay)", expected);
        }
    }

    private static DeviceAuthException fail(String code, String message, Expected expected) {
        // Logged for fraud review; never log signatures or keys
        LOG.warn("Device auth failure {} for session {}", code, expected.sid());
        return DeviceAuthException.unauthorized(code, message);
    }
}
