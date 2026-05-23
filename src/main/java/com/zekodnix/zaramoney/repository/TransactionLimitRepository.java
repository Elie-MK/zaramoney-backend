package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.TransactionLimit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionLimit entity.
 */
@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Long>, JpaSpecificationExecutor<TransactionLimit> {
    default Optional<TransactionLimit> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<TransactionLimit> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<TransactionLimit> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select transactionLimit from TransactionLimit transactionLimit left join fetch transactionLimit.account",
        countQuery = "select count(transactionLimit) from TransactionLimit transactionLimit"
    )
    Page<TransactionLimit> findAllWithToOneRelationships(Pageable pageable);

    @Query("select transactionLimit from TransactionLimit transactionLimit left join fetch transactionLimit.account")
    List<TransactionLimit> findAllWithToOneRelationships();

    @Query(
        "select transactionLimit from TransactionLimit transactionLimit left join fetch transactionLimit.account where transactionLimit.id =:id"
    )
    Optional<TransactionLimit> findOneWithToOneRelationships(@Param("id") Long id);
}
