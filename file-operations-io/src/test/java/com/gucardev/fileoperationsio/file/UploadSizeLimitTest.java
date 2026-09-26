package com.gucardev.fileoperationsio.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The size limit is enforced by the servlet container's multipart parsing, which MockMvc skips,
 * so this test sends a real HTTP request.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:files-size-test",
        "file-storage.max-file-size=1KB"
})
class UploadSizeLimitTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("file-storage.directory", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @Test
    void fileOverTheLimitIsRejected() {
        byte[] large = new byte[2048];
        Arrays.fill(large, (byte) 'a');
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(large) {
            @Override
            public String getFilename() {
                return "large.txt";
            }
        });

        HttpClientErrorException error = catchThrowableOfType(HttpClientErrorException.class, () ->
                RestClient.create("http://localhost:" + port).post().uri("/api/files")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity());

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(storageDir).isEmptyDirectory();
    }
}
