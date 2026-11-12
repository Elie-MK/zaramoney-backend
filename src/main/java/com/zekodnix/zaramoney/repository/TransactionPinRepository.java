package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.TransactionPin;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionPinRepository extends JpaRepository<TransactionPin, Long> {
    boolean existsByUserId(Long userId);

    Optional<TransactionPin> findOneByUserId(Long userId);

    /** Locks the row so parallel requests cannot bypass the failed-attempt counter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TransactionPin p where p.user.id = :userId")
    Optional<TransactionPin> findOneByUserIdForUpdate(@Param("userId") Long userId);
}
