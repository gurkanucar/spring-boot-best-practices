package com.gucardev.fileoperationsio.file;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/** Upload, list, download and delete files. Unknown ids end in 404. */
public interface FileService {

    /** A file ready to stream back to the client. */
    record Download(StoredFile file, Resource content) {
    }

    /**
     * Validates and stores an upload.
     *
     * @throws FileRejectedException 400 (empty), 413 (too large) or 415 (type not accepted)
     */
    StoredFile upload(MultipartFile upload);

    Page<StoredFile> list(Pageable pageable);

    StoredFile get(UUID id);

    Download download(UUID id);

    void delete(UUID id);
}
