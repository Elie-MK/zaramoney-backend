package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.SessionType;
import java.io.Serializable;
import java.time.Instant;

/** A login session as shown to its user (device list) or to an admin. */
public record SessionDTO(
    String sessionId,
    SessionType type,
    String deviceId,
    String deviceName,
    String platform,
    String ip,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt,
    Instant revokedAt,
    String revokeReason,
    boolean current
)
    implements Serializable {}
