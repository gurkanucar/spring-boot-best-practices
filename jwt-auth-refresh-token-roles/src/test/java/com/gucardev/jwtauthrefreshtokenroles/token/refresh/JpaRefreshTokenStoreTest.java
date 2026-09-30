package com.gucardev.jwtauthrefreshtokenroles.token.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.jwtauthrefreshtokenroles.common.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JpaRefreshTokenStoreTest {

    @Autowired
    private JpaRefreshTokenStore store;

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private TokenHasher tokenHasher;

    private static String newUserId() {
        return UUID.randomUUID().toString();
    }

    private static String newToken() {
        return "token-" + UUID.randomUUID();
    }

    @Test
    void consumeReturnsUserIdOnlyOnce() {
        String userId = newUserId();
        String token = newToken();
        store.save(token, userId, Duration.ofDays(2));

        assertThat(store.consume(token)).contains(userId);
        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void storesOnlyTheHash() {
        String token = newToken();
        store.save(token, newUserId(), Duration.ofDays(2));

        assertThat(repository.findById(token)).isEmpty();
        assertThat(repository.findById(tokenHasher.hash(token))).isPresent();
    }

    @Test
    void expiredTokenIsRejectedEvenBeforeCleanup() {
        String token = newToken();
        store.save(token, newUserId(), Duration.ofSeconds(-1));

        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void unknownTokenIsRejected() {
        assertThat(store.consume("never-issued")).isEmpty();
    }

    @Test
    void revokeAllForUserDropsOnlyThatUsersTokens() {
        String alice = newUserId();
        String bob = newUserId();
        String aliceLaptop = newToken();
        String alicePhone = newToken();
        String bobToken = newToken();
        store.save(aliceLaptop, alice, Duration.ofDays(2));
        store.save(alicePhone, alice, Duration.ofDays(2));
        store.save(bobToken, bob, Duration.ofDays(2));

        store.revokeAllForUser(alice);

        assertThat(store.consume(aliceLaptop)).isEmpty();
        assertThat(store.consume(alicePhone)).isEmpty();
        assertThat(store.consume(bobToken)).contains(bob);
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredRows() {
        String expired = newToken();
        String valid = newToken();
        store.save(expired, newUserId(), Duration.ofSeconds(-1));
        store.save(valid, newUserId(), Duration.ofDays(2));

        int deleted = store.deleteExpired(Instant.now());

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(repository.findById(tokenHasher.hash(expired))).isEmpty();
        assertThat(repository.findById(tokenHasher.hash(valid))).isPresent();
        assertThat(store.name()).isEqualTo("refresh tokens");
    }
}
