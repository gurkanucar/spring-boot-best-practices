package com.gucardev.fileoperationss3.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Runs against a real MinIO, which enforces the bucket policy and presigned signatures. Presigned
 * URLs are called with a plain HTTP client, like a browser would, without any AWS credentials.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:s3-test")
@Testcontainers
class FileS3IntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    // Real magic bytes: the server decides by these, never by the name.
    private static final byte[] PNG = Arrays.copyOf(
            new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
    private static final byte[] PDF = "%PDF-1.4\n%demo\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EXE = Arrays.copyOf(new byte[]{'M', 'Z'}, 128);
    private static final byte[] HTML = "<!DOCTYPE html><html><script>alert(1)</script></html>"
            .getBytes(StandardCharsets.UTF_8);

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");

    @DynamicPropertySource
    static void s3(DynamicPropertyRegistry registry) {
        registry.add("file-storage.s3.endpoint", minio::getS3URL);
        registry.add("file-storage.s3.access-key", minio::getUserName);
        registry.add("file-storage.s3.secret-key", minio::getPassword);
        registry.add("file-storage.s3.public-base-url", () -> minio.getS3URL() + "/files");
    }

    @LocalServerPort
    int port;

    @Autowired
    S3Client s3;

    private final HttpClient http = HttpClient.newHttpClient();
    private RestClient api;

    @BeforeEach
    void api() {
        // Never throw on 4xx: the tests assert the status themselves.
        api = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @Test
    void publicFileIsReadableByAnyoneThroughItsPermanentUrl() throws Exception {
        var file = uploadThroughApi("logo.png", PNG, "PUBLIC").getBody();
        assertThat(file.get("visibility")).isEqualTo("PUBLIC");
        String publicUrl = (String) file.get("publicUrl");
        assertThat(publicUrl).endsWith("/files/public/" + file.get("id"));

        HttpResponse<byte[]> anonymous = httpGet(publicUrl);

        assertThat(anonymous.statusCode()).isEqualTo(200);
        assertThat(anonymous.body()).isEqualTo(PNG);
        assertThat(anonymous.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(anonymous.headers().firstValue("Content-Disposition")).hasValueSatisfying(
                value -> assertThat(value).startsWith("attachment;").contains("logo.png"));
    }

    @Test
    void privateFileIsOnlyReadableThroughAPresignedUrl() throws Exception {
        var file = uploadThroughApi("contract.pdf", PDF, "PRIVATE").getBody();
        assertThat(file.get("publicUrl")).isNull();

        HttpResponse<byte[]> anonymous = httpGet(minio.getS3URL() + "/files/private/" + file.get("id"));
        assertThat(anonymous.statusCode()).isEqualTo(403);

        var downloadUrl = api.get().uri("/api/files/{id}/download-url", file.get("id")).retrieve().body(JSON);
        assertThat(downloadUrl.get("expiresAt")).isNotNull();
        HttpResponse<byte[]> presigned = httpGet((String) downloadUrl.get("url"));

        assertThat(presigned.statusCode()).isEqualTo(200);
        assertThat(presigned.body()).isEqualTo(PDF);
        assertThat(presigned.headers().firstValue("Content-Disposition")).hasValueSatisfying(
                value -> assertThat(value).startsWith("attachment;").contains("contract.pdf"));
    }

    @Test
    void presignedUploadBecomesReadyAfterValidation() throws Exception {
        var upload = createUpload("report.pdf", PDF.length, "PRIVATE");
        String id = (String) upload.get("id");

        assertThat(httpPut(upload, PDF).statusCode()).isEqualTo(200);
        var completed = api.post().uri("/api/files/{id}/complete", id).retrieve().toEntity(JSON);

        assertThat(completed.getStatusCode().value()).isEqualTo(200);
        assertThat(completed.getBody().get("status")).isEqualTo("READY");
        assertThat(completed.getBody().get("contentType")).isEqualTo("application/pdf");
        assertThat(api.get().uri("/api/files/{id}/content", id).retrieve().body(byte[].class)).isEqualTo(PDF);
        assertPendingObjectIsGone(id);
    }

    @Test
    void presignedUploadWithWrongContentIsRejectedAndDeleted() throws Exception {
        var upload = createUpload("invoice.pdf", EXE.length, "PUBLIC");
        String id = (String) upload.get("id");
        assertThat(httpPut(upload, EXE).statusCode()).isEqualTo(200);

        var completed = api.post().uri("/api/files/{id}/complete", id).retrieve().toEntity(JSON);

        assertThat(completed.getStatusCode().value()).isEqualTo(415);
        assertThat(api.get().uri("/api/files/{id}", id).retrieve().body(JSON).get("status")).isEqualTo("REJECTED");
        assertThat(api.get().uri("/api/files/{id}/download-url", id).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(404);
        assertPendingObjectIsGone(id);
    }

    @Test
    void presignedUrlOnlyAcceptsTheDeclaredSize() throws Exception {
        var upload = createUpload("notes.txt", 100, "PRIVATE");

        HttpResponse<byte[]> put = httpPut(upload, "only a few bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(put.statusCode()).isEqualTo(403); // signature covers Content-Length
    }

    @Test
    void presignedUploadWithBlockedExtensionIsRefusedUpFront() {
        var response = api.post().uri("/api/files/uploads").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filename", "setup.exe", "size", 10)).retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(415);
    }

    @Test
    void completingBeforeTheUploadIsAConflict() {
        var upload = createUpload("later.txt", 10, "PRIVATE");

        var response = api.post().uri("/api/files/{id}/complete", upload.get("id")).retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void disallowedTypeThroughTheApiIsRejected() {
        assertThat(uploadThroughApi("page.txt", HTML, "PRIVATE").getStatusCode().value()).isEqualTo(415);
    }

    @Test
    void unknownIdIsNotFound() {
        var response = api.get().uri("/api/files/{id}", "00000000-0000-0000-0000-000000000000")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteRemovesObjectAndMetadata() {
        var file = uploadThroughApi("old.png", PNG, "PRIVATE").getBody();
        String id = (String) file.get("id");

        assertThat(api.delete().uri("/api/files/{id}", id).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(204);

        assertThat(api.get().uri("/api/files/{id}", id).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(404);
        assertThatThrownBy(() -> s3.headObject(r -> r.bucket("files").key("private/" + id)))
                .isInstanceOf(NoSuchKeyException.class);
    }

    private ResponseEntity<Map<String, Object>> uploadThroughApi(String filename, byte[] bytes, String visibility) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        body.add("visibility", visibility);
        return api.post().uri("/api/files").contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                .retrieve().toEntity(JSON);
    }

    private Map<String, Object> createUpload(String filename, long size, String visibility) {
        var response = api.post().uri("/api/files/uploads").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filename", filename, "size", size, "visibility", visibility))
                .retrieve().toEntity(JSON);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody();
    }

    // Like a browser: only the presigned URL and its headers, no AWS credentials.
    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> httpPut(Map<String, Object> upload, byte[] bytes) throws Exception {
        var request = HttpRequest.newBuilder(URI.create((String) upload.get("url")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        // Content-Length is set by the HTTP client from the body; Java does not allow setting it by hand.
        ((Map<String, String>) upload.get("headers")).forEach((name, value) -> {
            if (!name.equalsIgnoreCase("content-length")) {
                request.header(name, value);
            }
        });
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> httpGet(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void assertPendingObjectIsGone(String id) {
        assertThatThrownBy(() -> s3.headObject(r -> r.bucket("files").key("pending/" + id)))
                .isInstanceOf(NoSuchKeyException.class);
    }
}
