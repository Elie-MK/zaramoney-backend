package com.zekodnix.zaramoney.domain.enumeration;

/**
 * <ul>
 *   <li>{@code DEVICE}: mobile app with registered device keys; every request is signed; gets refresh tokens.</li>
 *   <li>{@code ADMIN}: admin without a device key (Postman / web tool); short access token, no refresh token, no signing.</li>
 *   <li>{@code LEGACY}: old app version without keys; only allowed while device binding is in {@code optional} mode.</li>
 * </ul>
 */
public enum SessionType {
    DEVICE,
    ADMIN,
    LEGACY,
}
