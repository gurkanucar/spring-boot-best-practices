package com.gucardev.fileoperationss3.file.validation;

import com.gucardev.fileoperationss3.file.FileRejectedException;
import com.gucardev.fileoperationss3.file.FileStorageProperties;
import com.gucardev.fileoperationss3.file.validation.FileTypeDetector.DetectedType;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The rules from the {@code file-storage} section of application.yaml. */
@Component
@RequiredArgsConstructor
public class DefaultFileValidator implements FileValidator {

    // Control characters and characters that are illegal or special in common file systems.
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[\\p{Cntrl}<>:\"/\\\\|?*]");

    private final FileTypeDetector detector;
    private final FileStorageProperties properties;

    @Override
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

    @Override
    public void checkFilename(String filename) {
        String extension = checkExtensions(filename);
        if (properties.allowedTypes().values().stream().noneMatch(allowed -> allowed.contains(extension))) {
            throw reject("File extension is not allowed");
        }
    }

    @Override
    public AcceptedFile validate(InputStream content, String filename) {
        DetectedType detected = detector.detect(content);
        String type = detected.type();

        // The type or any parent type: application/java-archive is blocked as application/zip.
        if (Stream.concat(Stream.of(type), detected.superTypes().stream())
                .anyMatch(properties.blockedTypes()::contains)) {
            throw reject("File type " + type + " is not allowed");
        }
        Set<String> allowedExtensions = properties.allowedTypes().get(type);
        if (allowedExtensions == null) {
            throw reject("File type " + type + " is not allowed");
        }

        String extension = checkExtensions(filename);
        if (!allowedExtensions.contains(extension)) {
            throw reject("File extension does not match its content (" + type + ")");
        }
        return new AcceptedFile(type, extension);
    }

    // "report.pdf.exe" -> [pdf, exe]. Every part is checked, not only the last one. Returns the last.
    private String checkExtensions(String filename) {
        List<String> extensions = extensionsOf(filename);
        if (extensions.stream().anyMatch(properties.blockedExtensions()::contains)) {
            throw reject("File extension is not allowed");
        }
        if (extensions.isEmpty()) {
            throw reject("File must have an extension");
        }
        return extensions.getLast();
    }

    private static List<String> extensionsOf(String filename) {
        String[] parts = filename.toLowerCase(Locale.ROOT).split("\\.");
        return Arrays.stream(parts).skip(1).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    private static FileRejectedException reject(String detail) {
        return new FileRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, detail);
    }
}
