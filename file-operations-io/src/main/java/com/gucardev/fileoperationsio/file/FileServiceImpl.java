package com.gucardev.fileoperationsio.file;

import com.gucardev.fileoperationsio.file.storage.FileStorage;
import com.gucardev.fileoperationsio.file.validation.FileValidator;
import com.gucardev.fileoperationsio.file.validation.FileValidator.AcceptedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final StoredFileRepository repository;
    private final FileValidator validator;
    private final FileStorage storage;

    /**
     * Validate first, then store. The servlet container has already buffered the upload (to disk for
     * large files) and enforced {@code spring.servlet.multipart.max-file-size}, so the upload can be
     * read twice: once for type detection, once to copy it into storage. A rejected upload never
     * reaches storage, and nothing is kept on any failure.
     */
    @Override
    public StoredFile upload(MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new FileRejectedException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String filename = validator.sanitizeFilename(upload.getOriginalFilename());
        AcceptedFile accepted;
        try (InputStream content = upload.getInputStream()) {
            accepted = validator.validate(content, filename);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        UUID id = UUID.randomUUID();
        MessageDigest sha256 = sha256();
        try {
            storage.store(id, new DigestInputStream(upload.getInputStream(), sha256)); // hashed while stored
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StoredFile file = StoredFile.create(id, filename, accepted.extension(), accepted.contentType(),
                upload.getSize(), HexFormat.of().formatHex(sha256.digest()));
        try {
            return repository.save(file);
        } catch (RuntimeException e) {
            storage.delete(id);
            throw e;
        }
    }

    @Override
    public Page<StoredFile> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public StoredFile get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    @Override
    public Download download(UUID id) {
        StoredFile file = get(id);
        Resource content = storage.open(id);
        if (!content.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File content not found");
        }
        return new Download(file, content);
    }

    @Override
    public void delete(UUID id) {
        repository.delete(get(id));
        storage.delete(id);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM supports SHA-256", e);
        }
    }
}
