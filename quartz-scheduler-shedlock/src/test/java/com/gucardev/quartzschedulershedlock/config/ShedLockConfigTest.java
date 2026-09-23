package com.gucardev.quartzschedulershedlock.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ShedLockConfigTest {

    @Autowired
    private LockProvider lockProvider;

    @Test
    void acquiresAndReleasesALockAgainstTheRealDatabase() {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(), "shed-lock-config-test", Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> firstAcquisition = lockProvider.lock(lockConfiguration);
        assertThat(firstAcquisition).isPresent();

        Optional<SimpleLock> secondAcquisitionWhileHeld = lockProvider.lock(lockConfiguration);
        assertThat(secondAcquisitionWhileHeld).isEmpty();

        firstAcquisition.get().unlock();

        Optional<SimpleLock> acquisitionAfterRelease = lockProvider.lock(lockConfiguration);
        assertThat(acquisitionAfterRelease).isPresent();
        acquisitionAfterRelease.get().unlock();
    }
}
