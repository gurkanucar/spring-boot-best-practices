package com.gucardev.jwtauthrefreshtokenroles.token.refresh;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    @Query("select r.userId from RefreshTokenEntity r where r.tokenHash = :hash")
    Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);

    /** Returns 1 only for the one caller that actually deleted an unexpired token. */
    @Modifying
    @Query("delete from RefreshTokenEntity r where r.tokenHash = :hash and r.expiresAt > :now")
    int deleteValid(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshTokenEntity r where r.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from RefreshTokenEntity r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
