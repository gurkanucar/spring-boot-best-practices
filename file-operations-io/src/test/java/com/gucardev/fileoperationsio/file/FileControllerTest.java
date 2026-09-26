package com.gucardev.fileoperationsio.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:files-test")
@AutoConfigureMockMvc
class FileControllerTest {

    // Real magic bytes: Tika decides by these, not by the name or the client's Content-Type.
    private static final byte[] PNG = bytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
    private static final byte[] PDF = "%PDF-1.4\n%demo\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EXE = bytes(new byte[]{'M', 'Z'}, 128);
    private static final byte[] HTML = "<!DOCTYPE html><html><script>alert(1)</script></html>"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] TEXT = "hello".getBytes(StandardCharsets.UTF_8);

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("file-storage.directory", storageDir::toString);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void uploadedFileIsDownloadedWithItsOriginalNameAndSecurityHeaders() throws Exception {
        String id = idOf(upload("photo.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/files/")))
                .andExpect(jsonPath("$.originalFilename").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(PNG.length)));

        mvc.perform(get("/api/files/{id}/content", id))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition", containsString("photo.png")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", "no-store"));

        // Stored under the UUID only, not the original name.
        assertThat(storageDir.resolve(id)).exists();
    }

    @Test
    void nonAsciiFilenameIsKeptAndEncodedInContentDisposition() throws Exception {
        String id = idOf(upload("Rapor Öğrenci.txt", TEXT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("Rapor Öğrenci.txt")));

        mvc.perform(get("/api/files/{id}/content", id))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''Rapor%20%C3%96%C4%9Frenci.txt")));
    }

    @Test
    void pdfIsAccepted() throws Exception {
        upload("report.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
    }

    @Test
    void executableRenamedToPdfIsRejected() throws Exception {
        upload("invoice.pdf", EXE).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void htmlIsRejected() throws Exception {
        upload("page.txt", HTML).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void extensionMustMatchTheContent() throws Exception {
        upload("photo.pdf", PNG)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("File extension does not match its content (image/png)"));
    }

    @Test
    void blockedExtensionAnywhereInTheNameIsRejected() throws Exception {
        upload("shell.php.png", PNG).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void fileWithoutExtensionIsRejected() throws Exception {
        upload("README", TEXT).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void pathInTheFilenameIsRemoved() throws Exception {
        String id = idOf(upload("../../secret.txt", TEXT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("secret.txt")));

        assertThat(storageDir.resolve(id)).exists();
        assertThat(storageDir.getParent().resolve("secret.txt")).doesNotExist();
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        upload("empty.txt", new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        mvc.perform(get("/api/files/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesMetadataAndFile() throws Exception {
        String id = idOf(upload("notes.txt", TEXT).andExpect(status().isCreated()));

        mvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/files/{id}", id)).andExpect(status().isNotFound());
        assertThat(storageDir.resolve(id)).doesNotExist();
    }

    @Test
    void rejectedUploadLeavesNoFileBehind() throws Exception {
        long before;
        try (var files = Files.list(storageDir)) {
            before = files.count();
        }
        upload("invoice.pdf", EXE).andExpect(status().isUnsupportedMediaType());
        try (var files = Files.list(storageDir)) {
            assertThat(files.count()).isEqualTo(before);
        }
    }

    // The client claims a harmless Content-Type on purpose: the server must not trust it.
    private ResultActions upload(String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", filename, "image/png", bytes)));
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    // The magic-byte header followed by zeros up to the given length.
    private static byte[] bytes(byte[] header, int length) {
        return Arrays.copyOf(header, length);
    }
}
