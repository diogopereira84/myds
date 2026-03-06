package com.fedex.automation.utils;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public final class PrintfulUploadFileHelper {
    private static final Logger LOG = LoggerFactory.getLogger(PrintfulUploadFileHelper.class);

    private PrintfulUploadFileHelper() {
        // utility class
    }

    public static UploadArtifact prepareStableUploadArtifact(File sourceFile, String stableFileName) throws IOException {
        if (sourceFile == null) {
            throw new IllegalArgumentException("sourceFile must not be null.");
        }
        if (stableFileName == null || stableFileName.isBlank()) {
            throw new IllegalArgumentException("stableFileName must not be blank.");
        }

        Path tempDir = Files.createTempDirectory("printful-upload-");
        LOG.debug("Created temp upload directory: {}", tempDir);

        Path stablePath = tempDir.resolve(stableFileName);
        Files.copy(sourceFile.toPath(), stablePath, StandardCopyOption.REPLACE_EXISTING);

        File uploadFile = stablePath.toFile();
        LOG.debug("Prepared stable upload file: {}", uploadFile.getAbsolutePath());

        return new UploadArtifact(uploadFile, tempDir);
    }

    public static void cleanupUploadArtifact(UploadArtifact artifact) {
        if (artifact == null) {
            return;
        }
        try {
            Files.deleteIfExists(artifact.getFile().toPath());
            Path directory = artifact.getDirectory();
            if (directory != null && Files.isDirectory(directory)) {
                try (Stream<Path> entries = Files.list(directory)) {
                    if (entries.findFirst().isEmpty()) {
                        Files.deleteIfExists(directory);
                    }
                }
            }
            LOG.debug("Cleaned up temp upload artifacts.");
        } catch (IOException e) {
            LOG.warn("Failed to clean up temporary upload file.", e);
        }
    }

    @Getter
    public static final class UploadArtifact {
        private final File file;
        private final Path directory;

        public UploadArtifact(File file, Path directory) {
            this.file = file;
            this.directory = directory;
        }

    }
}

