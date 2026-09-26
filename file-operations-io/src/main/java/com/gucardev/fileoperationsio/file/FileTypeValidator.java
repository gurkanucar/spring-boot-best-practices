package com.gucardev.fileoperationsio.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Decides whether an upload is accepted. The type comes from the file content (magic bytes), never
 * from the client's Content-Type header or the file extension.
 */
@Component
@RequiredArgsConstructor
public class FileTypeValidator {

    /** What an accepted file is stored as. */
    public record AcceptedType(String contentType, String extension) {
    }

    // Control characters and characters that are illegal or special in common file systems.
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[\\p{Cntrl}<>:\"/\\\\|?*]");

    private static final Tika TIKA = new Tika();
    private static final MediaTypeRegistry REGISTRY = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry();

    private final FileStorageProperties properties;

    /**
     * Makes the client's filename safe to store and to send back in Content-Disposition:
     * "../../etc/passwd.txt" becomes "passwd.txt". It is still never used as a disk path.
     */
    public String sanitizeFilename(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = UNSAFE_CHARACTERS.matcher(name).replaceAll("").strip();
        name = name.replaceFirst("^\\.+", ""); // no hidden files, no "." or ".."
        int max = properties.maxFilenameLength();
        if (name.length() > max) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot < max ? name.substring(dot) : "";
            name = name.substring(0, max - extension.length()) + extension;
        }
        return name.isEmpty() ? "file" : name;
    }

    /** Throws {@link FileRejectedException} (415) unless the file passes every rule. */
    public AcceptedType validate(Path file, String filename) {
        MediaType detected = detect(file);
        String type = detected.getBaseType().toString(); // without parameters such as charset

        if (isBlocked(detected)) {
            throw reject("File type " + type + " is not allowed");
        }
        Set<String> allowedExtensions = properties.allowedTypes().get(type);
        if (allowedExtensions == null) {
            throw reject("File type " + type + " is not allowed");
        }

        // "report.pdf.exe" -> [pdf, exe]. Every part is checked, not only the last one.
        List<String> extensions = extensionsOf(filename);
        if (extensions.stream().anyMatch(properties.blockedExtensions()::contains)) {
            throw reject("File extension is not allowed");
        }
        if (extensions.isEmpty()) {
            throw reject("File must have an extension");
        }
        String extension = extensions.getLast();
        if (!allowedExtensions.contains(extension)) {
            throw reject("File extension does not match its content (" + type + ")");
        }
        return new AcceptedType(type, extension);
    }

    // Content only: without a filename hint Tika cannot be steered by the extension.
    private static MediaType detect(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return MediaType.parse(TIKA.detect(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // A type is blocked when it or one of its super types is, e.g. application/java-archive -> application/zip.
    private boolean isBlocked(MediaType type) {
        for (MediaType current = type.getBaseType(); current != null; current = REGISTRY.getSupertype(current)) {
            if (properties.blockedTypes().contains(current.toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extensionsOf(String filename) {
        String[] parts = filename.toLowerCase(Locale.ROOT).split("\\.");
        return Arrays.stream(parts).skip(1).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    private static FileRejectedException reject(String detail) {
        return new FileRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, detail);
    }
}
