package com.gucardev.fileoperationss3.file.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Component;

/** Apache Tika (tika-core): magic-byte detection, no document parsers. */
@Component
public class TikaFileTypeDetector implements FileTypeDetector {

    private static final Tika TIKA = new Tika();
    private static final MediaTypeRegistry REGISTRY = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry();

    // Content only: without a filename hint Tika cannot be steered by the extension.
    @Override
    public DetectedType detect(InputStream content) {
        MediaType type;
        try {
            type = MediaType.parse(TIKA.detect(content)).getBaseType(); // drop parameters such as charset
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> superTypes = new ArrayList<>();
        for (MediaType parent = REGISTRY.getSupertype(type); parent != null; parent = REGISTRY.getSupertype(parent)) {
            superTypes.add(parent.toString());
        }
        return new DetectedType(type.toString(), List.copyOf(superTypes));
    }
}
