package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionRecord entity.
 */
@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long>, JpaSpecificationExecutor<TransactionRecord> {
    default Optional<TransactionRecord> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<TransactionRecord> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<TransactionRecord> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select transactionRecord from TransactionRecord transactionRecord left join fetch transactionRecord.sender left join fetch transactionRecord.receiver",
        countQuery = "select count(transactionRecord) from TransactionRecord transactionRecord"
    )
    Page<TransactionRecord> findAllWithToOneRelationships(Pageable pageable);

    @Query(
        "select transactionRecord from TransactionRecord transactionRecord left join fetch transactionRecord.sender left join fetch transactionRecord.receiver"
    )
    List<TransactionRecord> findAllWithToOneRelationships();

    @Query(
        "select transactionRecord from TransactionRecord transactionRecord left join fetch transactionRecord.sender left join fetch transactionRecord.receiver where transactionRecord.id =:id"
    )
    Optional<TransactionRecord> findOneWithToOneRelationships(@Param("id") Long id);

    /** Total debited from an account (every outgoing COMPLETED transaction, whatever its type) since {@code from}. Used for limits. */
    @Query(
        "select coalesce(sum(t.sendAmount), 0) from TransactionRecord t " +
        "where t.sender.id = :accountId and t.transactionStatus = :status and t.transactionDate >= :from"
    )
    BigDecimal sumOutgoingSince(
        @Param("accountId") Long accountId,
        @Param("status") TransactionStatus status,
        @Param("from") Instant from
    );

    /** COMPLETED transactions where the user is sender or receiver (blocked attempts are never shown to customers). */
    @Query(
        value = "select t from TransactionRecord t left join t.sender s left join s.user su left join t.receiver r left join r.user ru " +
        "where (su.login = :login or ru.login = :login) and t.transactionStatus = com.zekodnix.zaramoney.domain.enumeration.TransactionStatus.COMPLETED",
        countQuery = "select count(t) from TransactionRecord t left join t.sender s left join s.user su left join t.receiver r left join r.user ru " +
        "where (su.login = :login or ru.login = :login) and t.transactionStatus = com.zekodnix.zaramoney.domain.enumeration.TransactionStatus.COMPLETED"
    )
    Page<TransactionRecord> findAllForUserLogin(@Param("login") String login, Pageable pageable);

    Optional<TransactionRecord> findOneByTransactionReference(String transactionReference);

    boolean existsBySender_IdOrReceiver_Id(Long senderId, Long receiverId);

    // ---------------------------------------------------------------------------------------------- fraud scoring

    /** Payment attempts (any status) sent from an account since {@code from}. */
    @Query(
        "select count(t) from TransactionRecord t where t.sender.id = :accountId and t.transactionType in :types and t.transactionDate >= :from"
    )
    long countOutgoingSince(
        @Param("accountId") Long accountId,
        @Param("types") java.util.Collection<com.zekodnix.zaramoney.domain.enumeration.TransactionType> types,
        @Param("from") Instant from
    );

    /** Completed payments of these types sent from an account since {@code from}: count and total. */
    @Query(
        "select count(t), coalesce(sum(t.sendAmount), 0) from TransactionRecord t where t.sender.id = :accountId " +
        "and t.transactionType in :types and t.transactionStatus = :status and t.transactionDate >= :from"
    )
    List<Object[]> countAndSumOutgoingSince(
        @Param("accountId") Long accountId,
        @Param("types") java.util.Collection<com.zekodnix.zaramoney.domain.enumeration.TransactionType> types,
        @Param("status") TransactionStatus status,
        @Param("from") Instant from
    );

    /** Different receivers paid by an account since {@code from}, not counting {@code exceptReceiverId}. */
    @Query(
        "select count(distinct t.receiver.id) from TransactionRecord t where t.sender.id = :accountId and t.receiver.id <> :exceptReceiverId " +
        "and t.transactionType = :type and t.transactionStatus = :status and t.transactionDate >= :from"
    )
    long countDistinctReceiversSince(
        @Param("accountId") Long accountId,
        @Param("exceptReceiverId") Long exceptReceiverId,
        @Param("type") com.zekodnix.zaramoney.domain.enumeration.TransactionType type,
        @Param("status") TransactionStatus status,
        @Param("from") Instant from
    );

    /** Different senders who paid an account since {@code from}, not counting {@code exceptSenderId}. */
    @Query(
        "select count(distinct t.sender.id) from TransactionRecord t where t.receiver.id = :accountId and t.sender.id <> :exceptSenderId " +
        "and t.transactionType = :type and t.transactionStatus = :status and t.transactionDate >= :from"
    )
    long countDistinctSendersSince(
        @Param("accountId") Long accountId,
        @Param("exceptSenderId") Long exceptSenderId,
        @Param("type") com.zekodnix.zaramoney.domain.enumeration.TransactionType type,
        @Param("status") TransactionStatus status,
        @Param("from") Instant from
    );

    /** Admin fraud review queue (newest first). */
    @Query(
        value = "select t from TransactionRecord t where t.fraudStatus in :statuses " +
        "and (:unreviewedOnly = false or t.fraudReviewOutcome is null) order by t.transactionDate desc",
        countQuery = "select count(t) from TransactionRecord t where t.fraudStatus in :statuses " +
        "and (:unreviewedOnly = false or t.fraudReviewOutcome is null)"
    )
    Page<TransactionRecord> findFraudQueue(
        @Param("statuses") java.util.Collection<com.zekodnix.zaramoney.domain.enumeration.FraudStatus> statuses,
        @Param("unreviewedOnly") boolean unreviewedOnly,
        Pageable pageable
    );
}
