package com.gucardev.jwtauthrefreshtokenroles.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Single time source, so expiry logic never calls Instant.now() directly. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
