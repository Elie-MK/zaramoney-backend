package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the UserDetailsAccount entity.
 */
@SuppressWarnings("unused")
@Repository
public interface UserDetailsAccountRepository
    extends JpaRepository<UserDetailsAccount, Long>, JpaSpecificationExecutor<UserDetailsAccount> {
    default Optional<UserDetailsAccount> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<UserDetailsAccount> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<UserDetailsAccount> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select userDetailsAccount from UserDetailsAccount userDetailsAccount left join fetch userDetailsAccount.user",
        countQuery = "select count(userDetailsAccount) from UserDetailsAccount userDetailsAccount"
    )
    Page<UserDetailsAccount> findAllWithToOneRelationships(Pageable pageable);

    @Query("select userDetailsAccount from UserDetailsAccount userDetailsAccount left join fetch userDetailsAccount.user")
    List<UserDetailsAccount> findAllWithToOneRelationships();

    @Query(
        "select userDetailsAccount from UserDetailsAccount userDetailsAccount left join fetch userDetailsAccount.user where userDetailsAccount.id =:id"
    )
    Optional<UserDetailsAccount> findOneWithToOneRelationships(@Param("id") Long id);

    Optional<UserDetailsAccount> findByUserEmail(String login);
}
