package com.gucardev.ratelimitingbucket4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RateLimitingBucket4jApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimitingBucket4jApplication.class, args);
    }

}
