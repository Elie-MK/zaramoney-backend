package com.zekodnix.zaramoney.security.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The exact strings the app signs. Shared contract with the mobile team (see TODO.md item 13).
 *
 * <pre>
 * Request:  METHOD \n PATH?QUERY \n X-Timestamp \n X-Nonce \n X-Device-Id \n base64(SHA-256(raw body))
 * Login:    "login" \n nonce \n deviceId \n username
 * </pre>
 * For multipart requests the body component is the literal {@value #MULTIPART_BODY_HASH} (the files are not hashed;
 * the request is still bound to the device, session, time and nonce).
 */
public final class CanonicalRequest {

    public static final String MULTIPART_BODY_HASH = "MULTIPART";

    private CanonicalRequest() {}

    public static String forRequest(String method, String pathAndQuery, String timestamp, String nonce, String deviceId, String bodyHash) {
        return String.join("\n", method.toUpperCase(java.util.Locale.ROOT), pathAndQuery, timestamp, nonce, deviceId, bodyHash);
    }

    public static String forLogin(String nonce, String deviceId, String username) {
        return String.join("\n", "login", nonce, deviceId, username);
    }

    public static String bodyHash(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
