package com.gucardev.resillience4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Resillience4jApplication {

    public static void main(String[] args) {
        SpringApplication.run(Resillience4jApplication.class, args);
    }

}
