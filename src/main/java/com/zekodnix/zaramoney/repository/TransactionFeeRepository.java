package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.TransactionFee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the TransactionFee entity.
 */
@Repository
public interface TransactionFeeRepository extends JpaRepository<TransactionFee, Long>, JpaSpecificationExecutor<TransactionFee> {
    default Optional<TransactionFee> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<TransactionFee> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<TransactionFee> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select transactionFee from TransactionFee transactionFee left join fetch transactionFee.transaction",
        countQuery = "select count(transactionFee) from TransactionFee transactionFee"
    )
    Page<TransactionFee> findAllWithToOneRelationships(Pageable pageable);

    @Query("select transactionFee from TransactionFee transactionFee left join fetch transactionFee.transaction")
    List<TransactionFee> findAllWithToOneRelationships();

    @Query(
        "select transactionFee from TransactionFee transactionFee left join fetch transactionFee.transaction where transactionFee.id =:id"
    )
    Optional<TransactionFee> findOneWithToOneRelationships(@Param("id") Long id);
}
