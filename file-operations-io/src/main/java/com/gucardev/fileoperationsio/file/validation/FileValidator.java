package com.gucardev.fileoperationsio.file.validation;

import com.gucardev.fileoperationsio.file.FileRejectedException;
import java.io.InputStream;

/** Decides whether an upload is accepted, and makes its filename safe to keep. */
public interface FileValidator {

    /** What an accepted file is stored as. */
    record AcceptedFile(String contentType, String extension) {
    }

    /**
     * Makes the client's filename safe to store and to send back in Content-Disposition:
     * {@code ../../etc/passwd.txt} becomes {@code passwd.txt}. It is still never used as a disk path.
     */
    String sanitizeFilename(String originalFilename);

    /**
     * Checks the type detected from {@code content} and the extensions in {@code filename} against
     * the configured allow and block lists. The caller closes the stream.
     *
     * @throws FileRejectedException (415) when a rule fails
     */
    AcceptedFile validate(InputStream content, String filename);
}
