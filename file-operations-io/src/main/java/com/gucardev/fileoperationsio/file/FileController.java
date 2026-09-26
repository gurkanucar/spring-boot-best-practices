package com.gucardev.fileoperationsio.file;

import com.gucardev.fileoperationsio.file.FileService.Download;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final int MAX_PAGE_SIZE = 100;

    public record FileResponse(UUID id, String originalFilename, String contentType, long size, String sha256,
                               Instant uploadedAt) {

        static FileResponse from(StoredFile file) {
            return new FileResponse(file.getId(), file.getOriginalFilename(), file.getContentType(), file.getSize(),
                    file.getSha256(), file.getUploadedAt());
        }
    }

    private final FileService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@RequestParam("file") MultipartFile file) {
        StoredFile stored = service.upload(file);
        return ResponseEntity.created(URI.create("/api/files/" + stored.getId())).body(FileResponse.from(stored));
    }

    @GetMapping
    public PagedModel<FileResponse> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return new PagedModel<>(service.list(pageable).map(FileResponse::from));
    }

    @GetMapping("/{id}")
    public FileResponse get(@PathVariable UUID id) {
        return FileResponse.from(service.get(id));
    }

    /**
     * Always an attachment, never inline: the browser saves the file instead of rendering it. The
     * Content-Type is the detected one; the builder encodes the name (filename*=UTF-8''...) so
     * quotes, CR/LF or non-ASCII characters cannot break the header.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Download download = service.download(id);
        StoredFile file = download.file();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
