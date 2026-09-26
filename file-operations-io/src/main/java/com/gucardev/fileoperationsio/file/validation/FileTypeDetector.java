package com.gucardev.fileoperationsio.file.validation;

import java.io.InputStream;
import java.util.List;

/**
 * Detects the media type of a file from its content (magic bytes), never from its name or the
 * client's Content-Type. {@link TikaFileTypeDetector} is the only class that knows about Tika.
 */
public interface FileTypeDetector {

    /**
     * @param type       detected media type without parameters, e.g. {@code image/png}
     * @param superTypes its parent types, most specific first, e.g. {@code application/zip} for a JAR
     */
    record DetectedType(String type, List<String> superTypes) {
    }

    /** Reads only as many bytes as detection needs; the caller closes the stream. */
    DetectedType detect(InputStream content);
}
