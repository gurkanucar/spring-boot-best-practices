package com.gucardev.entityauditing.common.audit;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Spring Data JPA auditing: fills {@code createdBy/At} and {@code lastModifiedBy/At} on the row itself. */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(CurrentUser.get());
    }
}
