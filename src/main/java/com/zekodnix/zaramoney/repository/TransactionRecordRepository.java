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

    /** Transactions where the user is sender or receiver. */
    @Query(
        value = "select t from TransactionRecord t left join t.sender s left join s.user su left join t.receiver r left join r.user ru " +
        "where su.login = :login or ru.login = :login",
        countQuery = "select count(t) from TransactionRecord t left join t.sender s left join s.user su left join t.receiver r left join r.user ru " +
        "where su.login = :login or ru.login = :login"
    )
    Page<TransactionRecord> findAllForUserLogin(@Param("login") String login, Pageable pageable);

    Optional<TransactionRecord> findOneByTransactionReference(String transactionReference);

    boolean existsBySender_IdOrReceiver_Id(Long senderId, Long receiverId);
}
