package com.gucardev.restapiintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableResilientMethods
public class RestApiIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestApiIntegrationApplication.class, args);
    }

}
