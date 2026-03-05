package com.fedex.automation.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Primary
@Profile({"stage2", "stage3", "dev"})
@Component
public class TestResourceLoader implements TestResourceProvider {
    private final ResourceLoader resourceLoader;

    @Autowired
    public TestResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public File loadToTempFile(String resourcePath) {
        String suffix = inferSuffix(resourcePath);
        return loadToTempFile(resourcePath, suffix);
    }

    @Override
    public File loadToTempFile(String resourcePath, String fileSuffix) {
        try (InputStream inputStream = getResourceStream(resourcePath)) {
            Path tempFile = Files.createTempFile("test-resource-", fileSuffix);
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile.toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource to temp file: " + resourcePath, e);
        }
    }

    private InputStream getResourceStream(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Test resource not found: " + resourcePath);
        }
        return resource.getInputStream();
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
}
