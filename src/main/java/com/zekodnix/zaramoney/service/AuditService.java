package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.AuditEvent;
import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import com.zekodnix.zaramoney.repository.AuditEventRepository;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.service.dto.AuditEventDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Persistent audit trail ({@code audit_event}, append-only).
 *
 * <p>{@link #record} joins the CALLER's transaction: if the audited action rolls back, so does its audit row (a
 * deposit that failed is not audited as done). Actor = current login (null when anonymous / system), IP = the current
 * request's client IP (real client behind nginx, see {@code server.forward-headers-strategy}). Each event is also
 * logged as an {@code AUDIT} line.
 */
@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final int DETAILS_MAX = 2000;

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void record(AuditAction action, String targetType, String targetId, String details) {
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(Instant.now());
        event.setActorLogin(SecurityUtils.getCurrentUserLogin().orElse(null));
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(truncate(targetId, 100));
        event.setDetails(truncate(details, DETAILS_MAX));
        event.setIp(currentIp());
        auditEventRepository.save(event);
        LOG.info("AUDIT {} {}:{} by {} [{}] {}", action, targetType, targetId, event.getActorLogin(), event.getIp(), event.getDetails());
    }

    /** Newest first; every filter is optional. */
    @Transactional(readOnly = true)
    public Page<AuditEventDTO> search(
        String actorLogin,
        AuditAction action,
        String targetType,
        String targetId,
        Instant from,
        Instant to,
        Pageable pageable
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("from must be before to", "invaliddaterange");
        }
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (actorLogin != null && !actorLogin.isBlank()) {
                p.add(cb.equal(root.get("actorLogin"), actorLogin.trim()));
            }
            if (action != null) {
                p.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null && !targetType.isBlank()) {
                p.add(cb.equal(root.get("targetType"), targetType.trim()));
            }
            if (targetId != null && !targetId.isBlank()) {
                p.add(cb.equal(root.get("targetId"), targetId.trim()));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("occurredAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return auditEventRepository.findAll(spec, newestFirst).map(AuditEventDTO::of);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return truncate(attributes.getRequest().getRemoteAddr(), 64);
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
