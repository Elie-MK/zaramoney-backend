package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the IdempotencyRecord entity.
 */
@SuppressWarnings("unused")
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long>, JpaSpecificationExecutor<IdempotencyRecord> {
    Optional<IdempotencyRecord> findByKeyHash(String keyHash);
}
