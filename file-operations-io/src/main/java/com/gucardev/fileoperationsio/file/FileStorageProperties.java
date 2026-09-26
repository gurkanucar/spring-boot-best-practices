package com.gucardev.fileoperationsio.file;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** The {@code file-storage} section of application.yaml. Types and extensions are lower-cased. */
@ConfigurationProperties("file-storage")
public record FileStorageProperties(
        @DefaultValue("./uploads") Path directory,
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("255") int maxFilenameLength,
        Map<String, Set<String>> allowedTypes,
        Set<String> blockedTypes,
        Set<String> blockedExtensions) {

    public FileStorageProperties {
        allowedTypes = allowedTypes == null ? Map.of() : allowedTypes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> lower(e.getKey()), e -> lower(e.getValue())));
        blockedTypes = lower(blockedTypes);
        blockedExtensions = lower(blockedExtensions);
    }

    private static Set<String> lower(Set<String> values) {
        return values == null ? Set.of()
                : values.stream().map(FileStorageProperties::lower).collect(Collectors.toUnmodifiableSet());
    }

    private static String lower(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
