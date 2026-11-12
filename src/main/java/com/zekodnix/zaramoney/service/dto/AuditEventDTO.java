package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.AuditEvent;
import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import java.time.Instant;

public record AuditEventDTO(
    Long id,
    Instant occurredAt,
    String actorLogin,
    AuditAction action,
    String targetType,
    String targetId,
    String details,
    String ip
) {
    public static AuditEventDTO of(AuditEvent e) {
        return new AuditEventDTO(e.getId(), e.getOccurredAt(), e.getActorLogin(), e.getAction(), e.getTargetType(), e.getTargetId(), e.getDetails(), e.getIp());
    }
}
