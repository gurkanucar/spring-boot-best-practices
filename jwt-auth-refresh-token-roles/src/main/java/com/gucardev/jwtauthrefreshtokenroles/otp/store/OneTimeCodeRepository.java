package com.gucardev.jwtauthrefreshtokenroles.otp.store;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCodeEntity, Long> {

    Optional<OneTimeCodeEntity> findByUserIdAndPurposeAndChannel(UUID userId, OtpPurpose purpose, OtpChannel channel);

    Optional<OneTimeCodeEntity> findByCodeHashAndPurposeAndChannel(String codeHash, OtpPurpose purpose, OtpChannel channel);

    // Bulk delete runs immediately. A remove() + save() pair would not work here: Hibernate flushes
    // inserts before deletes and the unique (user_id, purpose, channel) constraint would fire.
    @Modifying(flushAutomatically = true)
    @Query("delete from OneTimeCodeEntity o where o.userId = :userId and o.purpose = :purpose and o.channel = :channel")
    int deleteByKey(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose,
                    @Param("channel") OtpChannel channel);

    @Modifying
    @Query("delete from OneTimeCodeEntity o where o.userId = :userId and o.purpose = :purpose")
    int deleteByUserIdAndPurpose(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose);

    /** 1 when an attempt was taken, 0 when none is left; the row lock serialises parallel guesses. */
    @Modifying
    @Query("update OneTimeCodeEntity o set o.attempts = o.attempts + 1 where o.id = :id and o.attempts < :maxAttempts")
    int reserveAttempt(@Param("id") long id, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query("delete from OneTimeCodeEntity o where o.id = :id")
    int deleteCode(@Param("id") long id);

    @Modifying
    @Query("delete from OneTimeCodeEntity o where o.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
