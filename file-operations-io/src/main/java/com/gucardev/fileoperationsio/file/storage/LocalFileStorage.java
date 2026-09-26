package com.gucardev.fileoperationsio.file.storage;

import com.gucardev.fileoperationsio.file.FileStorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Stores files as {@code <directory>/<uuid>}, without extension, outside any web root. */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(FileStorageProperties properties) throws IOException {
        this.root = properties.directory().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void store(UUID id, InputStream content) {
        Path path = pathOf(id);
        try (content) {
            Files.copy(content, path);
        } catch (IOException e) {
            delete(id); // no half-written file
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Resource open(UUID id) {
        return new FileSystemResource(pathOf(id));
    }

    @Override
    public void delete(UUID id) {
        try {
            Files.deleteIfExists(pathOf(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // A UUID cannot contain "../", but the check keeps this method safe if the naming ever changes.
    private Path pathOf(UUID id) {
        Path path = root.resolve(id.toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Resolved path is outside the storage directory");
        }
        return path;
    }
}
