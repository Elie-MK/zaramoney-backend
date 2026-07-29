package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.LedgerEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the LedgerEntry entity.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, JpaSpecificationExecutor<LedgerEntry> {
    default Optional<LedgerEntry> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<LedgerEntry> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<LedgerEntry> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select ledgerEntry from LedgerEntry ledgerEntry left join fetch ledgerEntry.account left join fetch ledgerEntry.transaction",
        countQuery = "select count(ledgerEntry) from LedgerEntry ledgerEntry"
    )
    Page<LedgerEntry> findAllWithToOneRelationships(Pageable pageable);

    @Query("select ledgerEntry from LedgerEntry ledgerEntry left join fetch ledgerEntry.account left join fetch ledgerEntry.transaction")
    List<LedgerEntry> findAllWithToOneRelationships();

    @Query(
        "select ledgerEntry from LedgerEntry ledgerEntry left join fetch ledgerEntry.account left join fetch ledgerEntry.transaction where ledgerEntry.id =:id"
    )
    Optional<LedgerEntry> findOneWithToOneRelationships(@Param("id") Long id);

    @Query(
        """
        select le
        from LedgerEntry le
        left join fetch le.account a
        left join fetch a.user
        left join fetch le.transaction t
        where a.user.login = :login
        """
    )
    Page<LedgerEntry> findAllByCurrentUserLogin(@Param("login") String login, Pageable pageable);

    @Query("""
            select le
            from LedgerEntry le
            left join fetch le.account a
            left join fetch a.user
            left join fetch le.transaction t
            where t.id = :id AND a.user.login = :login
            """)
    Optional<LedgerEntry> findOneByTransactionId(Long id, @Param("login") String login );
}
