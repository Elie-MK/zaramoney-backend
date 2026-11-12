package com.zekodnix.zaramoney.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Login / refresh response. {@code id_token} is kept (same value as {@code access_token}) for older app versions.
 * {@code refresh_token} is only present for DEVICE sessions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokensDTO(
    @JsonProperty("id_token") String idToken,
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("refresh_expires_in") Long refreshExpiresIn,
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("session_type") String sessionType
) {
    public static AuthTokensDTO of(String accessToken, long expiresIn, String refreshToken, Long refreshExpiresIn, String sid, String type) {
        return new AuthTokensDTO(accessToken, accessToken, "Bearer", expiresIn, refreshToken, refreshExpiresIn, sid, type);
    }
}
