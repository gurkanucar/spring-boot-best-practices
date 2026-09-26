package com.gucardev.fileoperationss3.file;

import com.gucardev.fileoperationss3.file.FileService.Download;
import com.gucardev.fileoperationss3.file.FileService.DownloadUrl;
import com.gucardev.fileoperationss3.file.FileService.UploadUrl;
import com.gucardev.fileoperationss3.file.StoredFile.Status;
import com.gucardev.fileoperationss3.file.StoredFile.Visibility;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final int MAX_PAGE_SIZE = 100;

    /** {@code visibility} defaults to PRIVATE: public is an explicit choice. */
    public record CreateUploadRequest(String filename, long size, Visibility visibility) {
    }

    public record FileResponse(UUID id, String originalFilename, String contentType, long size,
                               Visibility visibility, Status status, String publicUrl, Instant createdAt) {
    }

    private final FileService service;

    /** Upload through the API: validated before it is stored in S3. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(defaultValue = "PRIVATE") Visibility visibility) {
        StoredFile stored = service.upload(file, visibility);
        return ResponseEntity.created(URI.create("/api/files/" + stored.getId())).body(toResponse(stored));
    }

    /** Presigned upload, step 1: get a URL. Step 2: PUT the bytes to it. Step 3: {@link #complete}. */
    @PostMapping("/uploads")
    public ResponseEntity<UploadUrl> createUpload(@RequestBody CreateUploadRequest request) {
        Visibility visibility = request.visibility() == null ? Visibility.PRIVATE : request.visibility();
        UploadUrl upload = service.createUpload(request.filename(), request.size(), visibility);
        return ResponseEntity.created(URI.create("/api/files/" + upload.id())).body(upload);
    }

    @PostMapping("/{id}/complete")
    public FileResponse complete(@PathVariable UUID id) {
        return toResponse(service.completeUpload(id));
    }

    @GetMapping
    public PagedModel<FileResponse> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedModel<>(service.list(pageable).map(this::toResponse));
    }

    @GetMapping("/{id}")
    public FileResponse get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    /** Streams the file through the API: always an attachment, with the detected Content-Type. */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Download download = service.download(id);
        StoredFile file = download.file();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.content()));
    }

    /** A URL to download straight from S3: permanent for public files, short-lived for private ones. */
    @GetMapping("/{id}/download-url")
    public DownloadUrl downloadUrl(@PathVariable UUID id) {
        return service.downloadUrl(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private FileResponse toResponse(StoredFile file) {
        return new FileResponse(file.getId(), file.getOriginalFilename(), file.getContentType(), file.getSize(),
                file.getVisibility(), file.getStatus(), service.publicUrl(file).orElse(null), file.getCreatedAt());
    }
}
