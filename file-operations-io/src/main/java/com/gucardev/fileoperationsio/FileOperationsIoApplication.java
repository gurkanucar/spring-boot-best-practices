package com.gucardev.fileoperationsio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FileOperationsIoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileOperationsIoApplication.class, args);
    }

}
