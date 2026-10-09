package com.gucardev.casdoorbasicsecurity.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByCasdoorId(String casdoorId);

    /**
     * Creates the row unless it exists, in one statement. Two first sign-ins of the same user at the
     * same moment (two tabs) may both see "no row" and both insert:
     * with "find, then save" the second one fails on the unique key, and if that is the sign-in, the
     * user sees an error page. {@code ON CONFLICT DO NOTHING} (PostgreSQL) lets both succeed.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_user (casdoor_id, created_at) VALUES (:casdoorId, :now)
            ON CONFLICT (casdoor_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(String casdoorId, Instant now);
}
