package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Insert and read only: audit_event is append-only (DB trigger). Never call delete / update. */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {}
