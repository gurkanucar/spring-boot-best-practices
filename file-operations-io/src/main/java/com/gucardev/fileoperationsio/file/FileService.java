package com.gucardev.fileoperationsio.file;

import com.gucardev.fileoperationsio.file.FileStorage.TempFile;
import com.gucardev.fileoperationsio.file.FileTypeValidator.AcceptedType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
public class FileService {

    /** A file ready to stream back to the client. */
    public record Download(StoredFile file, Resource content) {
    }

    private final StoredFileRepository repository;
    private final FileStorage storage;
    private final FileTypeValidator validator;

    /**
     * Stream to a temp file, detect the type from its bytes, then keep it under its UUID. A rejected
     * upload never reaches its final location, and nothing is kept on any failure.
     */
    public StoredFile upload(MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new FileRejectedException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String filename = validator.sanitizeFilename(upload.getOriginalFilename());
        TempFile temp = storage.writeTemp(inputStreamOf(upload));
        try {
            AcceptedType type = validator.validate(temp.path(), filename);
            StoredFile file = StoredFile.create(filename, type.extension(), type.contentType(), temp.size(),
                    temp.sha256());
            storage.moveToStorage(temp.path(), file.getId());
            try {
                return repository.save(file);
            } catch (RuntimeException e) {
                storage.delete(file.getId());
                throw e;
            }
        } finally {
            storage.deleteQuietly(temp.path()); // no-op once the file was moved
        }
    }

    public Page<StoredFile> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public StoredFile get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    public Download download(UUID id) {
        StoredFile file = get(id);
        Resource content = storage.open(id);
        if (!content.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File content not found");
        }
        return new Download(file, content);
    }

    public void delete(UUID id) {
        repository.delete(get(id));
        storage.delete(id);
    }

    private static InputStream inputStreamOf(MultipartFile upload) {
        try {
            return upload.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
