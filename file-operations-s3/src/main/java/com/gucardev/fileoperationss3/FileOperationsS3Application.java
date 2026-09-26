package com.gucardev.fileoperationss3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FileOperationsS3Application {

    public static void main(String[] args) {
        SpringApplication.run(FileOperationsS3Application.class, args);
    }

}
