package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.TransactionRecord;
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
    @Query(
        "select transactionRecord from TransactionRecord transactionRecord where transactionRecord.userLogin.login = ?#{authentication.name}"
    )
    List<TransactionRecord> findByUserLoginIsCurrentUser();

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
        value = "select transactionRecord from TransactionRecord transactionRecord left join fetch transactionRecord.userLogin",
        countQuery = "select count(transactionRecord) from TransactionRecord transactionRecord"
    )
    Page<TransactionRecord> findAllWithToOneRelationships(Pageable pageable);

    @Query("select transactionRecord from TransactionRecord transactionRecord left join fetch transactionRecord.userLogin")
    List<TransactionRecord> findAllWithToOneRelationships();

    @Query(
        "select transactionRecord from TransactionRecord transactionRecord left join fetch transactionRecord.userLogin where transactionRecord.id =:id"
    )
    Optional<TransactionRecord> findOneWithToOneRelationships(@Param("id") Long id);
}
