package com.fedex.automation.utils;

import java.io.File;

public interface TestResourceProvider {
    File loadToTempFile(String resourcePath, String fileSuffix);
    File loadToTempFile(String resourcePath);

    default String inferSuffix(String resourcePath) {
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
