package com.gucardev.fileoperationsio.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Local disk storage. Files are named by their UUID only. */
@Component
public class FileStorage {

    /** A fully written upload that has not been accepted yet. */
    public record TempFile(Path path, long size, String sha256) {
    }

    private final Path root;
    private final long maxBytes;

    public FileStorage(FileStorageProperties properties) throws IOException {
        this.root = properties.directory().toAbsolutePath().normalize();
        this.maxBytes = properties.maxFileSize().toBytes();
        Files.createDirectories(root);
    }

    /**
     * Copies the upload to a temp file in the storage directory and computes its SHA-256. Stops as
     * soon as the size limit is passed, so the limit holds even without the multipart limit.
     */
    public TempFile writeTemp(InputStream content) {
        Path temp = null;
        try (content) {
            temp = Files.createTempFile(root, "upload-", ".tmp");
            MessageDigest sha256 = sha256();
            long size = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    size += read;
                    if (size > maxBytes) {
                        throw new FileRejectedException(HttpStatus.CONTENT_TOO_LARGE,
                                "File is larger than " + maxBytes + " bytes");
                    }
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            return new TempFile(temp, size, HexFormat.of().formatHex(sha256.digest()));
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    public void moveToStorage(Path temp, UUID id) {
        try {
            Files.move(temp, pathOf(id), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource open(UUID id) {
        return new FileSystemResource(pathOf(id));
    }

    public void delete(UUID id) {
        deleteQuietly(pathOf(id));
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: a leftover file is harmless, it is never served without a metadata row.
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM supports SHA-256", e);
        }
    }

    // A UUID cannot contain "../", but the check keeps this method safe if the naming ever changes.
    Path pathOf(UUID id) {
        Path path = root.resolve(id.toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Resolved path is outside the storage directory");
        }
        return path;
    }
}
