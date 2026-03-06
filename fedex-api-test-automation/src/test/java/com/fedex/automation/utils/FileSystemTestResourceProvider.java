package com.fedex.automation.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Component
@Profile("local")
public class FileSystemTestResourceProvider implements TestResourceProvider {

    @Value("${test.resources.baseDir:}")
    private String baseDir;

    @Override
    public File loadToTempFile(String resourcePath) {
        String suffix = inferSuffix(resourcePath);
        return loadToTempFile(resourcePath, suffix);
    }

    @Override
    public File loadToTempFile(String resourcePath, String fileSuffix) {
        Path resourceFile = resolvePath(resourcePath);
        if (!Files.exists(resourceFile)) {
            throw new IllegalStateException("Test resource not found: " + resourceFile);
        }

        try (InputStream inputStream = Files.newInputStream(resourceFile)) {
            Path tempFile = Files.createTempFile("test-resource-", fileSuffix);
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile.toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource to temp file: " + resourceFile, e);
        }
    }

    private Path resolvePath(String resourcePath) {
        String normalizedPath = resourcePath;
        if (normalizedPath != null && normalizedPath.startsWith("classpath:")) {
            normalizedPath = normalizedPath.substring("classpath:".length());
        }
        if (baseDir != null && !baseDir.isBlank()) {
            return Paths.get(baseDir, normalizedPath);
        }
        return Paths.get(Objects.requireNonNull(normalizedPath));
    }

    private String inferSuffix(String resourcePath) {
        String filename = resourcePath;
        int slashIndex = resourcePath.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < resourcePath.length()) {
            filename = resourcePath.substring(slashIndex + 1);
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex + 1 < filename.length()) {
            return filename.substring(dotIndex);
        }
        return ".tmp";
    }

    @PostConstruct
    public void logProviderSelection() {
        org.slf4j.LoggerFactory.getLogger(FileSystemTestResourceProvider.class)
                .info("TestResourceProvider active: filesystem (baseDir={})", baseDir);
    }
}
