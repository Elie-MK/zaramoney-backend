package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    /** Row lock: two parallel refreshes with the same token are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.session s join fetch s.user where t.tokenHash = :hash")
    Optional<RefreshToken> findOneByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before or (t.usedAt is not null and t.usedAt < :before)")
    int deleteOldTokens(@Param("before") Instant before);
}
