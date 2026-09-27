package com.gucardev.logtobasicsecurity.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogtoId(String logtoId);

    /**
     * Creates the row unless it exists, in one statement. A sign-up sends webhooks while the user is
     * signing in for the first time, so two transactions may both see "no row" and both insert:
     * with "find, then save" the second one fails on the unique key, and if that is the sign-in, the
     * user sees an error page. {@code ON CONFLICT DO NOTHING} (PostgreSQL) lets both succeed.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_user (logto_id, created_at) VALUES (:logtoId, :now)
            ON CONFLICT (logto_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(String logtoId, Instant now);
}
