package com.gucardev.reportgenerationlighttaskwithscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ReportGenerationLightTaskWithSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportGenerationLightTaskWithSchedulerApplication.class, args);
    }

}
