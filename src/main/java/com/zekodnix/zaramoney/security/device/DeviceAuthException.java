package com.zekodnix.zaramoney.security.device;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Device-binding / session failure. Rendered as Problem JSON with {@code message = "error." + code}. */
public class DeviceAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;
    private final Instant serverTime;

    public DeviceAuthException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public DeviceAuthException(HttpStatus status, String code, String message, Instant serverTime) {
        super(message);
        this.status = status;
        this.code = code;
        this.serverTime = serverTime;
    }

    public static DeviceAuthException unauthorized(String code, String message) {
        return new DeviceAuthException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** Set for clock-skew errors so the app can correct its clock. */
    public Instant getServerTime() {
        return serverTime;
    }
}
