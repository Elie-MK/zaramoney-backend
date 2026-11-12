package com.zekodnix.zaramoney.repository;

import com.zekodnix.zaramoney.domain.UserSession;
import com.zekodnix.zaramoney.domain.enumeration.SessionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    @Query("select s from UserSession s join fetch s.user where s.sid = :sid")
    Optional<UserSession> findOneBySid(@Param("sid") String sid);

    @Query(
        "select s from UserSession s where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now order by s.createdAt desc"
    )
    List<UserSession> findActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Query(
        "select s from UserSession s where s.user.id = :userId and s.sessionType = :type and s.revokedAt is null " +
        "and s.expiresAt > :now order by s.createdAt asc"
    )
    List<UserSession> findActiveByUserIdAndType(@Param("userId") Long userId, @Param("type") SessionType type, @Param("now") Instant now);

    @Query("select s from UserSession s where s.deviceId = :deviceId and s.revokedAt is null and s.expiresAt > :now")
    List<UserSession> findActiveByDeviceId(@Param("deviceId") String deviceId, @Param("now") Instant now);
}
