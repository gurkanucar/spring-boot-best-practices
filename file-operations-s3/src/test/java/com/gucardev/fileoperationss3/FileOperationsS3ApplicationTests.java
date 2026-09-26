package com.gucardev.fileoperationss3;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// No S3 needed just to start: bucket setup is off, and clients connect lazily.
@SpringBootTest(properties = {"file-storage.s3.create-bucket=false", "spring.datasource.url=jdbc:h2:mem:context-test"})
class FileOperationsS3ApplicationTests {

    @Test
    void contextLoads() {
    }

}
