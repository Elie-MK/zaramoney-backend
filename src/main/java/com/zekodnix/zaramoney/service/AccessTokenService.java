package com.zekodnix.zaramoney.service;

import static com.zekodnix.zaramoney.security.SecurityUtils.AUTHORITIES_CLAIM;
import static com.zekodnix.zaramoney.security.SecurityUtils.DEVICE_ID_CLAIM;
import static com.zekodnix.zaramoney.security.SecurityUtils.JWT_ALGORITHM;
import static com.zekodnix.zaramoney.security.SecurityUtils.SESSION_ID_CLAIM;
import static com.zekodnix.zaramoney.security.SecurityUtils.USER_ID_CLAIM;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues access tokens (HS512 JWT). Every token carries the session id ({@code sid}), so it can be revoked at once. */
@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;

    public AccessTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String create(String login, Long userId, Collection<String> authorities, String sid, String deviceId, long validitySeconds) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(validitySeconds))
            .subject(login)
            .claim(AUTHORITIES_CLAIM, String.join(" ", authorities))
            .claim(SESSION_ID_CLAIM, sid);
        if (userId != null) {
            claims.claim(USER_ID_CLAIM, userId);
        }
        if (deviceId != null) {
            claims.claim(DEVICE_ID_CLAIM, deviceId);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(JWT_ALGORITHM).build(), claims.build())).getTokenValue();
    }
}
