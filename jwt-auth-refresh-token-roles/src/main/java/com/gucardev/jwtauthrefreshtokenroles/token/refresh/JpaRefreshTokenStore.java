package com.gucardev.jwtauthrefreshtokenroles.token.refresh;

import com.gucardev.jwtauthrefreshtokenroles.common.cleanup.ExpiredEntryCleaner;
import com.gucardev.jwtauthrefreshtokenroles.common.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaRefreshTokenStore implements RefreshTokenStore, ExpiredEntryCleaner {

    private final RefreshTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    @Override
    @Transactional
    public void save(String token, String userId, Duration ttl) {
        repository.save(new RefreshTokenEntity(
                tokenHasher.hash(token), UUID.fromString(userId), clock.instant().plus(ttl)));
    }

    /**
     * The conditional DELETE is the single-use guarantee: when two requests race with the same token,
     * only one delete affects a row, so only one of them gets the userId back.
     */
    @Override
    @Transactional
    public Optional<String> consume(String token) {
        String hash = tokenHasher.hash(token);
        Optional<UUID> userId = repository.findUserIdByTokenHash(hash);
        if (userId.isEmpty() || repository.deleteValid(hash, clock.instant()) != 1) {
            return Optional.empty();
        }
        return userId.map(UUID::toString);
    }

    @Override
    @Transactional
    public void revokeAllForUser(String userId) {
        repository.deleteByUserId(UUID.fromString(userId));
    }

    @Override
    public String name() {
        return "refresh tokens";
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return repository.deleteExpired(now);
    }
}
